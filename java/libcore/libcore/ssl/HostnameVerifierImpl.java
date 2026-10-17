package libcore.ssl;

import java.net.InetAddress;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

/**
 * HttpsURLConnection's default HostnameVerifier, with the rules of Android's OkHostnameVerifier: IP addresses match
 * iPAddress subject alternative names, host names match dNSName ones (a "*." wildcard covers exactly one leftmost
 * label). The subject's common name is not consulted, as on Android 9 and later.
 */
public final class HostnameVerifierImpl implements HostnameVerifier {
    public static final HostnameVerifierImpl INSTANCE = new HostnameVerifierImpl();

    private static final int ALT_DNS_NAME = 2;
    private static final int ALT_IPA_NAME = 7;

    private HostnameVerifierImpl() {
    }

    public boolean verify(String host, SSLSession session) {
        try {
            Certificate[] certs = session.getPeerCertificates();
            return certs.length > 0 && certs[0] instanceof X509Certificate && verify(host, (X509Certificate) certs[0]);
        } catch (SSLException e) {
            return false;
        }
    }

    public boolean verify(String host, X509Certificate certificate) {
        if (host == null) return false;
        if (isIpAddress(host)) {
            InetAddress want;
            try {
                want = InetAddress.getByName(host);
            } catch (Exception e) {
                return false;
            }
            for (String ip : altNames(certificate, ALT_IPA_NAME)) {
                try {
                    if (InetAddress.getByName(ip).equals(want)) return true;
                } catch (Exception ignored) {
                    // not an address
                }
            }
            return false;
        }
        String h = host.toLowerCase(Locale.US);
        for (String pattern : altNames(certificate, ALT_DNS_NAME)) {
            if (verifyHostname(h, pattern)) return true;
        }
        return false;
    }

    static boolean isIpAddress(String host) {
        if (host.indexOf(':') >= 0) return true;
        if (host.isEmpty()) return false;
        int dots = 0;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c == '.') dots++;
            else if (c < '0' || c > '9') return false;
        }
        return dots == 3;
    }

    private static List<String> altNames(X509Certificate certificate, int type) {
        List<String> out = new ArrayList<String>();
        try {
            Collection<List<?>> names = certificate.getSubjectAlternativeNames();
            if (names == null) return out;
            for (List<?> entry : names) {
                if (entry == null || entry.size() < 2) continue;
                Object t = entry.get(0);
                if (t instanceof Integer && (Integer) t == type && entry.get(1) instanceof String) {
                    out.add((String) entry.get(1));
                }
            }
        } catch (CertificateParsingException e) {
            // no usable names
        }
        return out;
    }

    /** OkHostnameVerifier.verifyHostname: host is lower case. */
    static boolean verifyHostname(String host, String pattern) {
        if (host == null || host.isEmpty() || host.startsWith(".") || host.endsWith("..")) return false;
        if (pattern == null || pattern.isEmpty() || pattern.startsWith(".") || pattern.endsWith("..")) return false;
        if (!host.endsWith(".")) host += ".";
        if (!pattern.endsWith(".")) pattern += ".";
        pattern = pattern.toLowerCase(Locale.US);
        if (!pattern.contains("*")) return host.equals(pattern);
        if (!pattern.startsWith("*.") || pattern.indexOf('*', 1) != -1) return false;
        if (host.length() < pattern.length()) return false;
        if ("*.".equals(pattern)) return false;
        String suffix = pattern.substring(1);
        if (!host.endsWith(suffix)) return false;
        int prefixLength = host.length() - suffix.length();
        return prefixLength <= 0 || host.lastIndexOf('.', prefixLength - 1) == -1;
    }
}
