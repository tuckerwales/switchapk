package libcore.ssl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The TLS 1.2 suites sessions offer, by their standard (Java) names; mbedtls spells them with dashes. */
final class CipherSuites {
    static final String[] SUPPORTED = {
        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
        "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
        "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
        "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
        "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
        "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
        "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
        "TLS_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_RSA_WITH_AES_256_GCM_SHA384",
        "TLS_RSA_WITH_AES_128_CBC_SHA",
        "TLS_RSA_WITH_AES_256_CBC_SHA",
    };

    static final String[] PROTOCOLS = {"TLSv1.2"};

    private CipherSuites() {
    }

    static String[] checkedSuites(String[] suites) {
        if (suites == null) throw new IllegalArgumentException("suites == null");
        List<String> known = Arrays.asList(SUPPORTED);
        List<String> out = new ArrayList<String>();
        for (String s : suites) {
            if (s == null) throw new IllegalArgumentException("suites contains null");
            // signalling suites and TLS 1.3 suites are accepted and ignored, as Conscrypt ignores what it cannot use
            if (known.contains(s)) out.add(s);
            else if (!s.startsWith("TLS_") && !s.startsWith("SSL_")) {
                throw new IllegalArgumentException("cipherSuite " + s + " is not supported.");
            }
        }
        return out.toArray(new String[0]);
    }

    static String[] checkedProtocols(String[] protocols) {
        if (protocols == null) throw new IllegalArgumentException("protocols == null");
        List<String> out = new ArrayList<String>();
        for (String p : protocols) {
            if (p == null) throw new IllegalArgumentException("protocols contains null");
            if (p.equals("TLSv1.2")) out.add(p);
            else if (!p.equals("TLSv1.3") && !p.equals("TLSv1.1") && !p.equals("TLSv1") && !p.equals("SSLv3")) {
                throw new IllegalArgumentException("protocol " + p + " is not supported");
            }
        }
        return out.toArray(new String[0]);
    }

    static String[] toMbedtls(String[] suites) {
        String[] out = new String[suites.length];
        for (int i = 0; i < suites.length; i++) out[i] = suites[i].replace('_', '-');
        return out;
    }

    static String fromMbedtls(String name) {
        return name == null ? "SSL_NULL_WITH_NULL_NULL" : name.replace('-', '_');
    }

    /** The key exchange part, as TrustManagers expect authType: ECDHE_RSA, ECDHE_ECDSA, RSA. */
    static String authType(String suite) {
        int w = suite.indexOf("_WITH_");
        return w > 4 ? suite.substring(4, w) : "UNKNOWN";
    }
}
