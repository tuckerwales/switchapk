package java.util.jar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Manifest implements Cloneable {
    private final Attributes attr = new Attributes();
    private final Map<String, Attributes> entries = new HashMap<>();

    public Manifest() {
    }

    public Manifest(InputStream is) throws IOException {
        read(is);
    }

    public Manifest(Manifest man) {
        attr.putAll(man.getMainAttributes());
        for (Map.Entry<String, Attributes> e : man.getEntries().entrySet()) {
            entries.put(e.getKey(), new Attributes(e.getValue()));
        }
    }

    public Attributes getMainAttributes() {
        return attr;
    }

    public Map<String, Attributes> getEntries() {
        return entries;
    }

    public Attributes getAttributes(String name) {
        return getEntries().get(name);
    }

    public void clear() {
        attr.clear();
        entries.clear();
    }

    public void write(OutputStream out) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        attr.writeMain(bo);
        for (Map.Entry<String, Attributes> e : entries.entrySet()) {
            println72(bo, "Name: " + e.getKey());
            e.getValue().write(bo);
        }
        bo.writeTo(out);
        out.flush();
    }

    /* Writes one line folded at 72 bytes: continuation lines start with a space (framework-internal). */
    static void println72(OutputStream out, String line) throws IOException {
        if (!line.isEmpty()) {
            byte[] b = line.getBytes(StandardCharsets.UTF_8);
            out.write(b[0]);
            int pos = 1;
            while (b.length - pos > 71) {
                out.write(b, pos, 71);
                pos += 71;
                println(out);
                out.write(' ');
            }
            out.write(b, pos, b.length - pos);
        }
        println(out);
    }

    static void println(OutputStream out) throws IOException {
        out.write('\r');
        out.write('\n');
    }

    public void read(InputStream is) throws IOException {
        LineReader in = new LineReader(is);
        attr.read(in);
        Attributes section;
        for (;;) {
            section = new Attributes();
            if (!section.read(in)) {
                break;
            }
            if (section.isEmpty()) {
                continue;
            }
            Object nameKey = new Attributes.Name("Name");
            String name = (String) section.remove(nameKey);
            if (name == null) {
                throw new IOException("invalid manifest format");
            }
            Attributes existing = entries.get(name);
            if (existing == null) {
                entries.put(name, section);
            } else {
                existing.map.putAll(section.map);
            }
        }
    }

    public boolean equals(Object o) {
        if (o instanceof Manifest) {
            Manifest m = (Manifest) o;
            return attr.equals(m.getMainAttributes()) && entries.equals(m.getEntries());
        }
        return false;
    }

    public int hashCode() {
        return attr.hashCode() + entries.hashCode();
    }

    public Object clone() {
        return new Manifest(this);
    }

    /* Splits the input into lines ending in CR, LF or CRLF; the last line may lack an ending. */
    static final class LineReader {
        private final InputStream in;
        private int pushback = -2;

        LineReader(InputStream in) {
            this.in = in;
        }

        private int next() throws IOException {
            if (pushback != -2) {
                int c = pushback;
                pushback = -2;
                return c;
            }
            return in.read();
        }

        byte[] readLine() throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int c = next();
            if (c == -1) {
                return null;
            }
            while (c != -1 && c != '\n' && c != '\r') {
                line.write(c);
                if (line.size() > 65535) {
                    throw new IOException("line too long");
                }
                c = next();
            }
            if (c == '\r') {
                int d = next();
                if (d != '\n') {
                    pushback = d;
                }
            }
            return line.toByteArray();
        }
    }
}
