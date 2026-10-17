package android.net.http;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.net.ssl.X509TrustManager;
import libcore.ssl.TrustManagerImpl;

/**
 * Chain checks that return the verified path, over the platform trust manager only (as on Android, where other
 * X509TrustManagers are refused). OkHttp builds one for every TLS client to clean chains for certificate pinning.
 */
public class X509TrustManagerExtensions {
    private final TrustManagerImpl delegate;

    public X509TrustManagerExtensions(X509TrustManager tm) throws IllegalArgumentException {
        if (!(tm instanceof TrustManagerImpl)) {
            throw new IllegalArgumentException("tm is an instance of " + tm.getClass().getName()
                    + " which is not a supported type of X509TrustManager");
        }
        delegate = (TrustManagerImpl) tm;
    }

    public List<X509Certificate> checkServerTrusted(X509Certificate[] chain, String authType, String host)
            throws CertificateException {
        return delegate.verify(chain, authType, true);
    }

    public boolean isUserAddedCertificate(X509Certificate cert) {
        return false;
    }

    public boolean isSameTrustConfiguration(String hostname1, String hostname2) {
        return true;
    }
}
