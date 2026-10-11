package javax.net.ssl;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.SecureRandom;
import libcore.crypto.Services;

public class SSLContext {
    private static SSLContext defaultContext;

    private final SSLContextSpi contextSpi;
    private final Provider provider;
    private final String protocol;

    protected SSLContext(SSLContextSpi contextSpi, Provider provider, String protocol) {
        this.contextSpi = contextSpi;
        this.provider = provider;
        this.protocol = protocol;
    }

    public static synchronized SSLContext getDefault() throws NoSuchAlgorithmException {
        if (defaultContext == null) {
            defaultContext = getInstance("Default");
        }
        return defaultContext;
    }

    public static synchronized void setDefault(SSLContext context) {
        if (context == null) throw new NullPointerException();
        defaultContext = context;
    }

    public static SSLContext getInstance(String protocol) throws NoSuchAlgorithmException {
        if (protocol == null) throw new NullPointerException("null protocol name");
        Provider.Service s = Services.first("SSLContext", protocol);
        return new SSLContext(Services.newSpi(s, SSLContextSpi.class, null), s.getProvider(), protocol);
    }

    public static SSLContext getInstance(String protocol, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        if (provider == null || provider.isEmpty()) throw new IllegalArgumentException("missing provider");
        Provider.Service s = Services.in("SSLContext", protocol, provider);
        return new SSLContext(Services.newSpi(s, SSLContextSpi.class, null), s.getProvider(), protocol);
    }

    public static SSLContext getInstance(String protocol, Provider provider) throws NoSuchAlgorithmException {
        if (provider == null) throw new IllegalArgumentException("missing provider");
        Provider.Service s = Services.in("SSLContext", protocol, provider);
        return new SSLContext(Services.newSpi(s, SSLContextSpi.class, null), s.getProvider(), protocol);
    }

    public final String getProtocol() {
        return protocol;
    }

    public final Provider getProvider() {
        return provider;
    }

    public final void init(KeyManager[] km, TrustManager[] tm, SecureRandom random) throws KeyManagementException {
        contextSpi.engineInit(km, tm, random);
    }

    public final SSLSocketFactory getSocketFactory() {
        return contextSpi.engineGetSocketFactory();
    }

    public final SSLServerSocketFactory getServerSocketFactory() {
        return contextSpi.engineGetServerSocketFactory();
    }

    public final SSLEngine createSSLEngine() {
        return contextSpi.engineCreateSSLEngine();
    }

    public final SSLEngine createSSLEngine(String peerHost, int peerPort) {
        return contextSpi.engineCreateSSLEngine(peerHost, peerPort);
    }

    public final SSLSessionContext getServerSessionContext() {
        return contextSpi.engineGetServerSessionContext();
    }

    public final SSLSessionContext getClientSessionContext() {
        return contextSpi.engineGetClientSessionContext();
    }

    public final SSLParameters getDefaultSSLParameters() {
        return contextSpi.engineGetDefaultSSLParameters();
    }

    public final SSLParameters getSupportedSSLParameters() {
        return contextSpi.engineGetSupportedSSLParameters();
    }
}
