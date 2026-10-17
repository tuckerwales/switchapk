package libcore.ssl;

import java.security.Provider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The TLS services of the built-in provider ("AndroidOpenSSL", as Conscrypt's). */
public final class SslServices {
    private SslServices() {
    }

    public static List<Provider.Service> services(Provider p) {
        Map<String, String> attrs = new HashMap<String, String>();
        attrs.put("ImplementedIn", "Software");
        List<Provider.Service> out = new ArrayList<Provider.Service>();
        out.add(new Provider.Service(p, "SSLContext", "TLS", SslContextImpl.class.getName(),
                Arrays.asList("SSL", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"), attrs));
        out.add(new Provider.Service(p, "SSLContext", "Default", SslContextImpl.Default.class.getName(), null, attrs));
        out.add(new Provider.Service(p, "TrustManagerFactory", "PKIX", TrustManagerFactoryImpl.class.getName(),
                Arrays.asList("X509", "X.509", "SunX509"), attrs));
        out.add(new Provider.Service(p, "KeyManagerFactory", "PKIX", KeyManagerFactoryImpl.class.getName(),
                Arrays.asList("X509", "X.509", "SunX509", "NewSunX509"), attrs));
        return out;
    }
}
