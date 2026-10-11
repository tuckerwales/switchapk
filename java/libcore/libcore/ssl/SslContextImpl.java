package libcore.ssl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

/** SSLContext "TLS" (and its version aliases). "Default" is SslContextImpl.Default, initialized at creation. */
public class SslContextImpl extends javax.net.ssl.SSLContextSpi {
    private X509TrustManager trustManager;
    private X509KeyManager keyManager;
    private final SessionContextImpl clientSessions = new SessionContextImpl();
    private final SessionContextImpl serverSessions = new SessionContextImpl();

    public SslContextImpl() {
    }

    protected void engineInit(KeyManager[] km, TrustManager[] tm, SecureRandom sr) throws KeyManagementException {
        X509TrustManager t = null;
        if (tm != null) {
            for (TrustManager m : tm) {
                if (m instanceof X509TrustManager) {
                    t = (X509TrustManager) m;
                    break;
                }
            }
        }
        if (t == null) {
            try {
                TrustManagerFactory f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                f.init((KeyStore) null);
                t = (X509TrustManager) f.getTrustManagers()[0];
            } catch (NoSuchAlgorithmException e) {
                throw new KeyManagementException(e);
            } catch (KeyStoreException e) {
                throw new KeyManagementException(e);
            }
        }
        X509KeyManager k = null;
        if (km != null) {
            for (KeyManager m : km) {
                if (m instanceof X509KeyManager) {
                    k = (X509KeyManager) m;
                    break;
                }
            }
        }
        trustManager = t;
        keyManager = k;
    }

    X509TrustManager trustManager() {
        return trustManager;
    }

    SessionContextImpl clientSessions() {
        return clientSessions;
    }

    private void checkInitialized() {
        if (trustManager == null) throw new IllegalStateException("SSLContext is not initialized.");
    }

    protected SSLSocketFactory engineGetSocketFactory() {
        checkInitialized();
        return new SslSocketFactoryImpl(this);
    }

    protected SSLServerSocketFactory engineGetServerSocketFactory() {
        checkInitialized();
        return new ServerSocketFactoryImpl();
    }

    protected SSLEngine engineCreateSSLEngine() {
        throw new UnsupportedOperationException("SSLEngine is not supported yet");
    }

    protected SSLEngine engineCreateSSLEngine(String host, int port) {
        throw new UnsupportedOperationException("SSLEngine is not supported yet");
    }

    protected SSLSessionContext engineGetServerSessionContext() {
        return serverSessions;
    }

    protected SSLSessionContext engineGetClientSessionContext() {
        return clientSessions;
    }

    /** SSLContext.getDefault(): ready at once with the system trust store; init() is refused, as on Android. */
    public static final class Default extends SslContextImpl {
        public Default() throws KeyManagementException {
            super.engineInit(null, null, null);
        }

        protected void engineInit(KeyManager[] km, TrustManager[] tm, SecureRandom sr) throws KeyManagementException {
            throw new KeyManagementException("Do not init() the default SSLContext ");
        }
    }

    /** TLS server sockets are not supported yet: creating one fails. */
    static final class ServerSocketFactoryImpl extends SSLServerSocketFactory {
        public String[] getDefaultCipherSuites() {
            return CipherSuites.SUPPORTED.clone();
        }

        public String[] getSupportedCipherSuites() {
            return CipherSuites.SUPPORTED.clone();
        }

        private static SocketException unsupported() {
            return new SocketException("TLS server sockets are not supported yet");
        }

        public ServerSocket createServerSocket() throws IOException {
            throw unsupported();
        }

        public ServerSocket createServerSocket(int port) throws IOException {
            throw unsupported();
        }

        public ServerSocket createServerSocket(int port, int backlog) throws IOException {
            throw unsupported();
        }

        public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
            throw unsupported();
        }
    }
}
