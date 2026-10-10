import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * java.nio selectors and socket channels on loopback: run on OpenJDK and on switchapk by tests/run_dex_test.sh,
 * output must match. Event order depends on timing, so the test prints what it learns, not the order of events.
 */
public class SelectorTest {
    static void p(Object o) {
        System.out.println(o);
    }

    static String name(Throwable t) {
        return t.getClass().getName();
    }

    static String str(ByteBuffer b) {
        byte[] a = new byte[b.remaining()];
        b.get(a);
        return new String(a, StandardCharsets.US_ASCII);
    }

    public static void main(String[] args) throws Exception {
        Selector sel = Selector.open();
        p("open " + sel.isOpen() + " keys " + sel.keys().size() + " now " + sel.selectNow());

        ServerSocketChannel server = ServerSocketChannel.open();
        p("server valid ops " + server.validOps() + " blocking " + server.isBlocking());
        try {
            server.register(sel, SelectionKey.OP_ACCEPT);
        } catch (IllegalBlockingModeException e) {
            p("register blocking: " + name(e));
        }
        try {
            server.accept();
        } catch (NotYetBoundException e) {
            p("accept unbound: " + name(e));
        }
        server.configureBlocking(false);
        server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        int port = ((InetSocketAddress) server.getLocalAddress()).getPort();
        p("bound " + (port > 0) + " accept none " + server.accept());
        SelectionKey serverKey = server.register(sel, SelectionKey.OP_ACCEPT, "server");
        p("registered " + server.isRegistered() + " key " + (server.keyFor(sel) == serverKey) + " attach "
                + serverKey.attachment() + " interest " + serverKey.interestOps());
        try {
            serverKey.interestOps(SelectionKey.OP_READ);
        } catch (IllegalArgumentException e) {
            p("bad ops: " + name(e));
        }

        SocketChannel client = SocketChannel.open();
        client.configureBlocking(false);
        boolean immediate = client.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
        SelectionKey clientKey = client.register(sel, immediate ? SelectionKey.OP_WRITE : SelectionKey.OP_CONNECT,
                "client");
        p("client pending or connected " + (client.isConnectionPending() || client.isConnected()));

        SocketChannel accepted = null;
        boolean connected = immediate;
        boolean sent = false;
        String received = null;
        String echoed = null;
        ByteBuffer serverBuf = ByteBuffer.allocate(64);
        ByteBuffer clientBuf = ByteBuffer.allocate(64);
        long deadline = System.currentTimeMillis() + 10000;
        while (echoed == null && System.currentTimeMillis() < deadline) {
            if (sel.select(1000) == 0) {
                continue;
            }
            Iterator<SelectionKey> it = sel.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey k = it.next();
                it.remove();
                if (k.isAcceptable()) {
                    accepted = server.accept();
                    accepted.configureBlocking(false);
                    accepted.register(sel, SelectionKey.OP_READ, "accepted");
                }
                if (k.isConnectable()) {
                    connected = client.finishConnect();
                    k.interestOps(SelectionKey.OP_WRITE);
                }
                if (k.isWritable() && k.attachment().equals("client") && !sent) {
                    client.write(ByteBuffer.wrap("ping".getBytes(StandardCharsets.US_ASCII)));
                    sent = true;
                    k.interestOps(SelectionKey.OP_READ);
                } else if (k.isReadable() && k.attachment().equals("accepted")) {
                    int n = accepted.read(serverBuf);
                    if (n > 0 && serverBuf.position() >= 4) {
                        serverBuf.flip();
                        received = str(serverBuf);
                        accepted.write(ByteBuffer.wrap(("echo " + received).getBytes(StandardCharsets.US_ASCII)));
                    }
                } else if (k.isReadable() && k.attachment().equals("client")) {
                    client.read(clientBuf);
                    if (clientBuf.position() >= 9) {
                        clientBuf.flip();
                        echoed = str(clientBuf);
                    }
                }
            }
        }
        p("connected " + connected + " " + client.isConnected() + " pending " + client.isConnectionPending());
        p("server got " + received + ", client got " + echoed);
        p("remote port " + (((InetSocketAddress) client.getRemoteAddress()).getPort() == port) + " socket "
                + (client.socket().getChannel() == client) + " server socket " + (server.socket().getChannel() == server));
        p("read nothing " + client.read(ByteBuffer.allocate(8)));

        // wakeup before select returns at once
        sel.wakeup();
        long t0 = System.currentTimeMillis();
        sel.select(5000);
        p("wakeup quick " + (System.currentTimeMillis() - t0 < 2000));

        // cancel and close
        clientKey.cancel();
        p("cancelled valid " + clientKey.isValid());
        sel.selectNow();
        p("keys after cancel " + sel.keys().contains(clientKey));
        try {
            clientKey.interestOps();
        } catch (CancelledKeyException e) {
            p("cancelled key: " + name(e));
        }
        accepted.close();
        clientBuf.clear();
        client.configureBlocking(true);
        p("eof " + client.read(clientBuf));
        client.close();
        p("client open " + client.isOpen());
        try {
            client.write(ByteBuffer.allocate(1));
        } catch (ClosedChannelException e) {
            p("write closed: " + name(e));
        }

        // blocking channels
        ServerSocketChannel bserver = ServerSocketChannel.open().bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        SocketChannel bclient = SocketChannel.open(bserver.getLocalAddress());
        SocketChannel bpeer = bserver.accept();
        bclient.write(ByteBuffer.wrap("blocking".getBytes(StandardCharsets.US_ASCII)));
        ByteBuffer bb = ByteBuffer.allocate(8);
        while (bb.hasRemaining()) {
            bpeer.read(bb);
        }
        bb.flip();
        p("blocking " + str(bb) + " connected " + bclient.isConnected() + " finish " + bclient.finishConnect());
        try {
            bclient.connect(bserver.getLocalAddress());
        } catch (AlreadyConnectedException e) {
            p("again: " + name(e));
        }
        ByteBuffer[] parts = {ByteBuffer.wrap("ab".getBytes(StandardCharsets.US_ASCII)),
                ByteBuffer.wrap("cd".getBytes(StandardCharsets.US_ASCII))};
        p("gather " + bclient.write(parts));
        ByteBuffer direct = ByteBuffer.allocateDirect(4);
        while (direct.hasRemaining()) {
            bpeer.read(direct);
        }
        direct.flip();
        p("direct " + str(direct));
        bclient.shutdownOutput();
        p("shutdown eof " + bpeer.read(ByteBuffer.allocate(4)));
        bclient.close();
        bpeer.close();
        bserver.close();

        // datagrams
        DatagramChannel a = DatagramChannel.open().bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        DatagramChannel b = DatagramChannel.open().bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        b.configureBlocking(false);
        p("nothing yet " + b.receive(ByteBuffer.allocate(16)));
        SelectionKey bk = b.register(sel, SelectionKey.OP_READ);
        a.send(ByteBuffer.wrap("udp".getBytes(StandardCharsets.US_ASCII)), b.getLocalAddress());
        int ready = 0;
        for (int i = 0; i < 20 && ready == 0; i++) {
            ready = sel.select(500);
        }
        p("udp ready " + ready + " readable " + bk.isReadable());
        ByteBuffer ub = ByteBuffer.allocate(16);
        SocketAddress from = b.receive(ub);
        ub.flip();
        p("udp " + str(ub) + " from a " + from.equals(a.getLocalAddress()));
        a.connect(b.getLocalAddress());
        p("connected " + a.isConnected() + " remote " + a.getRemoteAddress().equals(b.getLocalAddress()));
        a.write(ByteBuffer.wrap("conn".getBytes(StandardCharsets.US_ASCII)));
        try {
            b.configureBlocking(true);
        } catch (IllegalBlockingModeException e) {
            p("blocking registered: " + name(e));
        }
        bk.cancel();
        sel.selectNow();
        b.configureBlocking(true);
        ub.clear();
        b.receive(ub);
        ub.flip();
        p("udp " + str(ub));
        a.disconnect();
        p("disconnected " + a.isConnected());
        try {
            a.write(ByteBuffer.allocate(1));
        } catch (NotYetConnectedException e) {
            p("write unconnected: " + name(e));
        }
        a.close();
        b.close();

        server.close();
        sel.close();
        p("selector open " + sel.isOpen() + " key valid " + serverKey.isValid());
        try {
            sel.select(1);
        } catch (ClosedSelectorException e) {
            p("select closed: " + name(e));
        }
        Set<SocketOption<?>> opts = SocketChannel.open().supportedOptions();
        p("options tcp_nodelay " + opts.contains(StandardSocketOptions.TCP_NODELAY));
        SocketChannel oc = SocketChannel.open();
        oc.setOption(StandardSocketOptions.TCP_NODELAY, true);
        p("tcp_nodelay " + oc.getOption(StandardSocketOptions.TCP_NODELAY));
        oc.close();
    }
}
