import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.interfaces.*;
import java.util.*;
import javax.security.auth.x500.X500Principal;

/**
 * X.509 certificates, X500Principal and in-memory key stores: run on OpenJDK and on switchapk by
 * tests/run_dex_test.sh, output must match. Real CA roots (RSA and EC) plus locally generated
 * certificates with awkward names and most extensions. Provider names and the default key store
 * type differ between the two and are not printed.
 */
public class X509Test {
    static void p(Object o) {
        System.out.println(o);
    }

    static String hex(byte[] b) {
        if (b == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }

    static String sha256(byte[] b) throws Exception {
        return b == null ? "null" : hex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    static String bits(boolean[] b) {
        if (b == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (boolean x : b) sb.append(x ? '1' : '0');
        return sb.toString();
    }

    interface Op {
        Object run() throws Exception;
    }

    static void tryIt(String label, Op op) {
        try {
            Object r = op.run();
            p(label + ": " + (r instanceof byte[] ? hex((byte[]) r) : String.valueOf(r)));
        } catch (Exception e) {
            p(label + ": " + e.getClass().getName());
        }
    }

    static final String ISRG_X1 =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIFazCCA1OgAwIBAgIRAIIQz7DSQONZRGPgu2OCiwAwDQYJKoZIhvcNAQELBQAw\n" +
            "TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh\n" +
            "cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMTUwNjA0MTEwNDM4\n" +
            "WhcNMzUwNjA0MTEwNDM4WjBPMQswCQYDVQQGEwJVUzEpMCcGA1UEChMgSW50ZXJu\n" +
            "ZXQgU2VjdXJpdHkgUmVzZWFyY2ggR3JvdXAxFTATBgNVBAMTDElTUkcgUm9vdCBY\n" +
            "MTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAK3oJHP0FDfzm54rVygc\n" +
            "h77ct984kIxuPOZXoHj3dcKi/vVqbvYATyjb3miGbESTtrFj/RQSa78f0uoxmyF+\n" +
            "0TM8ukj13Xnfs7j/EvEhmkvBioZxaUpmZmyPfjxwv60pIgbz5MDmgK7iS4+3mX6U\n" +
            "A5/TR5d8mUgjU+g4rk8Kb4Mu0UlXjIB0ttov0DiNewNwIRt18jA8+o+u3dpjq+sW\n" +
            "T8KOEUt+zwvo/7V3LvSye0rgTBIlDHCNAymg4VMk7BPZ7hm/ELNKjD+Jo2FR3qyH\n" +
            "B5T0Y3HsLuJvW5iB4YlcNHlsdu87kGJ55tukmi8mxdAQ4Q7e2RCOFvu396j3x+UC\n" +
            "B5iPNgiV5+I3lg02dZ77DnKxHZu8A/lJBdiB3QW0KtZB6awBdpUKD9jf1b0SHzUv\n" +
            "KBds0pjBqAlkd25HN7rOrFleaJ1/ctaJxQZBKT5ZPt0m9STJEadao0xAH0ahmbWn\n" +
            "OlFuhjuefXKnEgV4We0+UXgVCwOPjdAvBbI+e0ocS3MFEvzG6uBQE3xDk3SzynTn\n" +
            "jh8BCNAw1FtxNrQHusEwMFxIt4I7mKZ9YIqioymCzLq9gwQbooMDQaHWBfEbwrbw\n" +
            "qHyGO0aoSCqI3Haadr8faqU9GY/rOPNk3sgrDQoo//fb4hVC1CLQJ13hef4Y53CI\n" +
            "rU7m2Ys6xt0nUW7/vGT1M0NPAgMBAAGjQjBAMA4GA1UdDwEB/wQEAwIBBjAPBgNV\n" +
            "HRMBAf8EBTADAQH/MB0GA1UdDgQWBBR5tFnme7bl5AFzgAiIyBpY9umbbjANBgkq\n" +
            "hkiG9w0BAQsFAAOCAgEAVR9YqbyyqFDQDLHYGmkgJykIrGF1XIpu+ILlaS/V9lZL\n" +
            "ubhzEFnTIZd+50xx+7LSYK05qAvqFyFWhfFQDlnrzuBZ6brJFe+GnY+EgPbk6ZGQ\n" +
            "3BebYhtF8GaV0nxvwuo77x/Py9auJ/GpsMiu/X1+mvoiBOv/2X/qkSsisRcOj/KK\n" +
            "NFtY2PwByVS5uCbMiogziUwthDyC3+6WVwW6LLv3xLfHTjuCvjHIInNzktHCgKQ5\n" +
            "ORAzI4JMPJ+GslWYHb4phowim57iaztXOoJwTdwJx4nLCgdNbOhdjsnvzqvHu7Ur\n" +
            "TkXWStAmzOVyyghqpZXjFaH3pO3JLF+l+/+sKAIuvtd7u+Nxe5AW0wdeRlN8NwdC\n" +
            "jNPElpzVmbUq4JUagEiuTDkHzsxHpFKVK7q4+63SM1N95R1NbdWhscdCb+ZAJzVc\n" +
            "oyi3B43njTOQ5yOf+1CceWxG1bQVs5ZufpsMljq4Ui0/1lvh+wjChP4kqKOJ2qxq\n" +
            "4RgqsahDYVvTH9w7jXbyLeiNdd8XM2w9U/t7y0Ff/9yi0GE44Za4rF2LN9d11TPA\n" +
            "mRGunUHBcnWEvgJBQl9nJEiU0Zsnvgc/ubhPgXRR4Xq37Z0j4r7g1SgEEzwxA57d\n" +
            "emyPxgcYxn/eR44/KJ4EBs+lVDR3veyJm+kXQ99b21/+jh5Xos1AnX5iItreGCc=\n" +
            "-----END CERTIFICATE-----\n";

    static final String MS_ECC =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICWTCCAd+gAwIBAgIQZvI9r4fei7FK6gxXMQHC7DAKBggqhkjOPQQDAzBlMQsw\n" +
            "CQYDVQQGEwJVUzEeMBwGA1UEChMVTWljcm9zb2Z0IENvcnBvcmF0aW9uMTYwNAYD\n" +
            "VQQDEy1NaWNyb3NvZnQgRUNDIFJvb3QgQ2VydGlmaWNhdGUgQXV0aG9yaXR5IDIw\n" +
            "MTcwHhcNMTkxMjE4MjMwNjQ1WhcNNDIwNzE4MjMxNjA0WjBlMQswCQYDVQQGEwJV\n" +
            "UzEeMBwGA1UEChMVTWljcm9zb2Z0IENvcnBvcmF0aW9uMTYwNAYDVQQDEy1NaWNy\n" +
            "b3NvZnQgRUNDIFJvb3QgQ2VydGlmaWNhdGUgQXV0aG9yaXR5IDIwMTcwdjAQBgcq\n" +
            "hkjOPQIBBgUrgQQAIgNiAATUvD0CQnVBEyPNgASGAlEvaqiBYgtlzPbKnR5vSmZR\n" +
            "ogPZnZH6thaxjG7efM3beaYvzrvOcS/lpaso7GMEZpn4+vKTEAXhgShC48Zo9OYb\n" +
            "hGBKia/teQ87zvH2RPUBeMCjVDBSMA4GA1UdDwEB/wQEAwIBhjAPBgNVHRMBAf8E\n" +
            "BTADAQH/MB0GA1UdDgQWBBTIy5lycFIM+Oa+sgRXKSrPQhDtNTAQBgkrBgEEAYI3\n" +
            "FQEEAwIBADAKBggqhkjOPQQDAwNoADBlAjBY8k3qDPlfXu5gKcs68tvWMoQZP3zV\n" +
            "L8KxzJOuULsJMsbG7X7JNpQS5GiFBqIb0C8CMQCZ6Ra0DvpWSNSkMBaReNtUjGUB\n" +
            "iudQZsIxtzm6uBoiB078a1QWIP8rtedMDE2mT3M=\n" +
            "-----END CERTIFICATE-----\n";

    static final String TEST_CA =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIDkjCCAnqgAwIBAgICEAEwDQYJKoZIhvcNAQELBQAwWDELMAkGA1UEBhMCR0Ix\n" +
            "GzAZBgNVBAoMElN3aXRjaCAiQVBLIiwgVGVzdDEVMBMGA1UECwwMQ2VydHMgKyBL\n" +
            "ZXlzMRUwEwYDVQQDDAxUZXN0IFJvb3QgQ0EwHhcNMjYxMDExMTQzMzQ5WhcNNDYx\n" +
            "MDA2MTQzMzQ5WjBYMQswCQYDVQQGEwJHQjEbMBkGA1UECgwSU3dpdGNoICJBUEsi\n" +
            "LCBUZXN0MRUwEwYDVQQLDAxDZXJ0cyArIEtleXMxFTATBgNVBAMMDFRlc3QgUm9v\n" +
            "dCBDQTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAO4vN+9ysCC0PCdp\n" +
            "blW1/P3m8BFev66oYEqvZeLQnk7qStHf+1Gj+yU0gED/bwRsn4yiYi9mIuAPBjTL\n" +
            "q8qT/U//RIbswmVDuQP8GRGpON1a53xYWEbgY/ljh1wwzf+FpyZGkfQTK+ph9rxJ\n" +
            "JzxlxF14l6cQvGLm2yqD74xREV0hniuB4vONOSZieZHWejnEB2mddLIFHuHXHgfU\n" +
            "5TYPp8FyXJGSAUk0hXFOKZbmbxJRw5OQfe36qZtuQF2Tj94bsJW3xdzC8dQKijnh\n" +
            "rQGCgXG42d25urVMeNP2rFRnRCqIRbJ7ooOUZ70LBWdd5dQKiVw2KOVt/rCEelkz\n" +
            "5cz5VtcCAwEAAaNmMGQwHQYDVR0OBBYEFIcC/fGnQzA1zBQRWry7sCkYAmdtMB8G\n" +
            "A1UdIwQYMBaAFIcC/fGnQzA1zBQRWry7sCkYAmdtMBIGA1UdEwEB/wQIMAYBAf8C\n" +
            "AQEwDgYDVR0PAQH/BAQDAgEGMA0GCSqGSIb3DQEBCwUAA4IBAQBESzM/Y8m4qd6K\n" +
            "xvH/skiColj/35V5bDddjsZC01iGMHxZq+iWziV7aBaTzvkSFBGpxQU/v7dWxMFx\n" +
            "dgzB9VhY2BqjBFIn/LViOWe4lI10OAq9cPHT7shBXO+GUXRcf2KEdZoXY+NY4LBk\n" +
            "mHnYEReExR3Sbxt3UgVu030RCUWjMo/sDjarR2YmJaLu5HQ5hlQRUGhgQOLCo3O5\n" +
            "PitmqwLsqmqUZIRhfWgXN00fL3EwY8Rx6El25DVBDkUFIZEpJ7NDFErtzFkcNLnu\n" +
            "G3fye99znUY4mA+mLY6nEcEIfLYm+0Bvr21aVApunp+zR1Lt07YA078jHiw3/oCS\n" +
            "4F9K3aF7\n" +
            "-----END CERTIFICATE-----\n";

    static final String EC_CA =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICBzCCAa6gAwIBAgIBTTAKBggqhkjOPQQDAzBiMRMwEQYKCZImiZPyLGQBGRYD\n" +
            "Y29tMRcwFQYKCZImiZPyLGQBGRYHZXhhbXBsZTETMBEGA1UEAwwKRUMgVGVzdCBD\n" +
            "QTEdMBsGCSqGSIb3DQEJARYOY2FAZXhhbXBsZS5jb20wIBcNMjYxMDExMTQzMzQy\n" +
            "WhgPMjA1MTA2MDIxNDMzNDJaMGIxEzARBgoJkiaJk/IsZAEZFgNjb20xFzAVBgoJ\n" +
            "kiaJk/IsZAEZFgdleGFtcGxlMRMwEQYDVQQDDApFQyBUZXN0IENBMR0wGwYJKoZI\n" +
            "hvcNAQkBFg5jYUBleGFtcGxlLmNvbTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IA\n" +
            "BH4A4g0lud2tuhAZObRaROqVdbNFUu7eKPZEvgQPs2JAVOGotbBmiaP/NBqmRVos\n" +
            "565gmlg+wV+YCDAN9ztB1m6jUzBRMB0GA1UdDgQWBBTMaqkTkWFT1r6DB4NuRtqn\n" +
            "Qw6KWzAfBgNVHSMEGDAWgBTMaqkTkWFT1r6DB4NuRtqnQw6KWzAPBgNVHRMBAf8E\n" +
            "BTADAQH/MAoGCCqGSM49BAMDA0cAMEQCIG+mIxfga3G5vbsJw/d8YGJTco9QOIJk\n" +
            "gJnDE/yQZkXsAiBKsn6g/MyRU1liwJFr3ZtZqjeACliZBjVwWBBorzjgQA==\n" +
            "-----END CERTIFICATE-----\n";

    static final String LEAF =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIEujCCA6KgAwIBAgIJAPEjRWeJq83vMA0GCSqGSIb3DQEBCwUAMFgxCzAJBgNV\n" +
            "BAYTAkdCMRswGQYDVQQKDBJTd2l0Y2ggIkFQSyIsIFRlc3QxFTATBgNVBAsMDENl\n" +
            "cnRzICsgS2V5czEVMBMGA1UEAwwMVGVzdCBSb290IENBMCAXDTI2MTAxMTE0MzM0\n" +
            "OVoYDzIwNTQwMjI2MTQzMzQ5WjCBwjELMAkGA1UEBhMCVVMxEzARBgNVBAgMCkNh\n" +
            "bGlmb3JuaWExFjAUBgNVBAcMDVNhbiBGcmFuY2lzY28xEjAQBgNVBAkMCTEgTWFp\n" +
            "biBTdDEWMBQGA1UECgwNRXhhbXBsZSwgSW5jLjEYMAoGA1UECwwDT3BzMAoGA1UE\n" +
            "CwwDV2ViMRgwFgYDVQQDDA93d3cuZXhhbXBsZS5jb20xFjAUBgoJkiaJk/IsZAEB\n" +
            "DAZ1c2VyNDIxDjAMBgNVBAUTBTEyMzQ1MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcD\n" +
            "QgAEBgSV9TkrfxiiyGccmoUPHSCVA0SS4UIMNlZ1xVTqUvFhPW2JwZnW7OObToyR\n" +
            "QXf73saOaikklM3Sb6ir72B3kqOCAeMwggHfMAkGA1UdEwQCMAAwDgYDVR0PAQH/\n" +
            "BAQDAgOIMCcGA1UdJQQgMB4GCCsGAQUFBwMBBggrBgEFBQcDAgYIKwYBBQUHAwQw\n" +
            "HQYDVR0OBBYEFK6hGAKDAt0/6AZPl1lRmyfbSCoAMB8GA1UdIwQYMBaAFIcC/fGn\n" +
            "QzA1zBQRWry7sCkYAmdtMIGsBgNVHREEgaQwgaGCD3d3dy5leGFtcGxlLmNvbYIN\n" +
            "Ki5leGFtcGxlLm9yZ4cEwAACAYcQIAENuAAAAAAAAAAAAAAAAYERYWRtaW5AZXhh\n" +
            "bXBsZS5jb22GGGh0dHBzOi8vZXhhbXBsZS5jb20vcGF0aIgEKgMEBaQ0MDIxCzAJ\n" +
            "BgNVBAYTAkRFMRAwDgYDVQQKDAdEaXIgT3JnMREwDwYDVQQDDAhEaXIgTmFtZTAZ\n" +
            "BgNVHRIEEjAQgg5jYS5leGFtcGxlLmNvbTATBgNVHSAEDDAKMAgGBmeBDAECAjBd\n" +
            "BggrBgEFBQcBAQRRME8wIwYIKwYBBQUHMAGGF2h0dHA6Ly9vY3NwLmV4YW1wbGUu\n" +
            "Y29tMCgGCCsGAQUFBzAChhxodHRwOi8vY2EuZXhhbXBsZS5jb20vY2EuY3J0MBsG\n" +
            "BSoDBAUGBBIMEGN1c3RvbSBleHRlbnNpb24wDQYJKoZIhvcNAQELBQADggEBAF0g\n" +
            "ys1Wpcyh9nZaOvSv19rGlezg4PfFRvqWBiyoxxOQv4hVSvmkT20xi5qTTxZlpNRk\n" +
            "gnlKMp1PutIlq5ZSohLeosmKB3E12OWNxzeWGjjn9XWzD3zydhEbVGTZAcqKZrnH\n" +
            "GpF9jtvGkIOhfme0TeoyaM/LDEHopck/IFaJzlILvvYDkmTkse9nXB8U85xSg0+D\n" +
            "R9Jx9G1MERXveW8x/HqS1u57qliqZtJjISU3R25jKzbbKjF1XGutULhexGsom1Yk\n" +
            "rc7RmfGxomYssVcr1z70P1TaR9YeoW9L550rp1FkxqSIR30XMgC4b0oMS9KnHAi8\n" +
            "RLhW9Kcc/+jhRxfEXEk=\n" +
            "-----END CERTIFICATE-----\n";

    static final String LEAF2 =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICZDCCAgkCAQMwCgYIKoZIzj0EAwMwYjETMBEGCgmSJomT8ixkARkWA2NvbTEX\n" +
            "MBUGCgmSJomT8ixkARkWB2V4YW1wbGUxEzARBgNVBAMMCkVDIFRlc3QgQ0ExHTAb\n" +
            "BgkqhkiG9w0BCQEWDmNhQGV4YW1wbGUuY29tMB4XDTI2MTAxMTE0MzM0MloXDTI3\n" +
            "MTExNTE0MzM0MlowTjEXMBUGA1UEAwwOIHNwYWNlZCAgbmFtZSAxITAfBgNVBAoM\n" +
            "GCNoYXNoO3NlbWk8bHQ+Z3Q9ZXFcYmFjazEQMA4GA1UEBwwHWsO8cmljaDCCASIw\n" +
            "DQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAJ53BQwS+GSzygHP8E7awz7KD7MS\n" +
            "XcHUAzGltvXajEzXWTHdSmGNsxRZFiSA10gn11xSiC5oeByUD35gVR6i97KXdiWE\n" +
            "agstljSRe52wfcOlVwnrRLjcCTRRusc4zVOO7U9BiXZTSESZ2U6AX2qXWyTrEQjJ\n" +
            "5GmGXtkut1riPdgxl57e6PiG5DOnpx3bBSIJYuXwWKi5dESTduG26k3OFs8gTZDW\n" +
            "DFAlcXHWMvmN5S2joPDTMsAojJuIecakG1tgkICrKrdp4DRgsJsn9eTeHa7Mz7Qh\n" +
            "p+duhuAMHFODbiDKR/OSbW4SSXxQ/E7f40sKn51EEaFxdqFvpElcZpeDi2ECAwEA\n" +
            "ATAKBggqhkjOPQQDAwNJADBGAiEA2KShl+5I0RGQziXfsfJRKmhpjIbo9z7r5+mu\n" +
            "mWAUXecCIQCNtX42DW79+R6xlmwoHOA2Uj+VQB2qsGM1Rx6QRc7KDQ==\n" +
            "-----END CERTIFICATE-----\n";

    static CertificateFactory cf;

    static X509Certificate parse(String pem) throws Exception {
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }

    public static void main(String[] args) throws Exception {
        cf = CertificateFactory.getInstance("X.509");
        p("type " + cf.getType() + " " + CertificateFactory.getInstance("X509").getType());
        X509Certificate isrg = parse(ISRG_X1), ms = parse(MS_ECC), ca = parse(TEST_CA), ecca = parse(EC_CA);
        X509Certificate leaf = parse(LEAF), leaf2 = parse(LEAF2);
        X509Certificate[] all = {isrg, ms, ca, ecca, leaf, leaf2};
        for (X509Certificate c : all) dump(c);
        factory();
        verify(isrg, ms, ca, ecca, leaf, leaf2);
        names();
        keyStore(ca, ecca, leaf);
        certPath(ca, leaf);
        p("done");
    }

    static void dump(X509Certificate c) throws Exception {
        p("== " + c.getSubjectX500Principal());
        p("type " + c.getType() + " version " + c.getVersion() + " serial " + c.getSerialNumber() + " hex " + c.getSerialNumber().toString(16));
        p("issuer " + c.getIssuerX500Principal());
        p("issuer 2253 " + c.getIssuerX500Principal().getName());
        p("subject 2253 " + c.getSubjectX500Principal().getName());
        p("subject 1779 " + c.getSubjectX500Principal().getName(X500Principal.RFC1779));
        p("subjectDN " + c.getSubjectDN() + " / issuerDN " + c.getIssuerDN());
        p("subject der " + hex(c.getSubjectX500Principal().getEncoded()));
        p("notBefore " + c.getNotBefore() + " " + c.getNotBefore().getTime());
        p("notAfter " + c.getNotAfter() + " " + c.getNotAfter().getTime());
        p("sigalg " + c.getSigAlgName() + " " + c.getSigAlgOID() + " params " + hex(c.getSigAlgParams()));
        p("encoded " + sha256(c.getEncoded()) + " len " + c.getEncoded().length);
        p("tbs " + sha256(c.getTBSCertificate()) + " sig " + sha256(c.getSignature()) + " siglen " + c.getSignature().length);
        p("hashCode " + c.hashCode() + " equals self copy " + c.equals(parse(pem(c.getEncoded()))));
        p("uids " + bits(c.getIssuerUniqueID()) + " " + bits(c.getSubjectUniqueID()));
        p("keyUsage " + bits(c.getKeyUsage()));
        p("extKeyUsage " + c.getExtendedKeyUsage());
        p("basicConstraints " + c.getBasicConstraints());
        p("subjectAltNames " + c.getSubjectAlternativeNames());
        p("issuerAltNames " + c.getIssuerAlternativeNames());
        p("critical " + c.getCriticalExtensionOIDs());
        p("noncritical " + c.getNonCriticalExtensionOIDs());
        p("unsupportedCritical " + c.hasUnsupportedCriticalExtension());
        TreeSet<String> oids = new TreeSet<String>();
        if (c.getCriticalExtensionOIDs() != null) oids.addAll(c.getCriticalExtensionOIDs());
        if (c.getNonCriticalExtensionOIDs() != null) oids.addAll(c.getNonCriticalExtensionOIDs());
        for (String oid : oids) p("  ext " + oid + " " + hex(c.getExtensionValue(oid)));
        p("ext missing " + hex(c.getExtensionValue("1.2.3.4")));
        PublicKey k = c.getPublicKey();
        byte[] spki = spki(c.getEncoded());
        p("key " + k.getAlgorithm() + " " + k.getFormat() + " rsa " + (k instanceof RSAPublicKey) + " ec " + (k instanceof ECPublicKey));
        if (k instanceof RSAPublicKey) p("rsa bits " + ((RSAPublicKey) k).getModulus().bitLength() + " e " + ((RSAPublicKey) k).getPublicExponent());
        if (k instanceof ECPublicKey) p("ec field " + ((ECPublicKey) k).getParams().getCurve().getField().getFieldSize() + " x " + ((ECPublicKey) k).getW().getAffineX().toString(16));
        p("spki " + hex(k.getEncoded()));
        p("spki matches cert " + Arrays.equals(k.getEncoded(), spki) + " pin " + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(k.getEncoded())));
        Date[] dates = {new Date(0L), new Date(1500000000000L), new Date(1800000000000L), new Date(2500000000000L), c.getNotBefore(), c.getNotAfter(), new Date(c.getNotAfter().getTime() + 1000)};
        for (Date d : dates) {
            final Date dd = d;
            final X509Certificate cc = c;
            tryIt("checkValidity " + d.getTime(), () -> { cc.checkValidity(dd); return "ok"; });
        }
        final X509Certificate cc = c;
        tryIt("checkValidity now", () -> { cc.checkValidity(); return "ok"; });
    }

    /** SubjectPublicKeyInfo bytes cut out of a certificate by hand (DER walk, short and long lengths). */
    static byte[] spki(byte[] cert) {
        int[] pos = {0};
        enter(cert, pos); // Certificate
        enter(cert, pos); // TBSCertificate
        if ((cert[pos[0]] & 0xff) == 0xa0) skip(cert, pos); // version
        skip(cert, pos); // serial
        skip(cert, pos); // signature
        skip(cert, pos); // issuer
        skip(cert, pos); // validity
        skip(cert, pos); // subject
        int start = pos[0];
        skip(cert, pos);
        return Arrays.copyOfRange(cert, start, pos[0]);
    }

    static int header(byte[] b, int[] pos) {
        pos[0]++;
        int len = b[pos[0]++] & 0xff;
        if (len >= 0x80) {
            int n = len & 0x7f;
            len = 0;
            for (int i = 0; i < n; i++) len = (len << 8) | (b[pos[0]++] & 0xff);
        }
        return len;
    }

    static void enter(byte[] b, int[] pos) {
        header(b, pos);
    }

    static void skip(byte[] b, int[] pos) {
        int len = header(b, pos);
        pos[0] += len;
    }

    static String pem(byte[] der) {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN CERTIFICATE-----\n" + b64 + "\n-----END CERTIFICATE-----\n";
    }

    static void factory() throws Exception {
        p("== factory");
        // A cacerts-style file: PEM block then openssl text; several blocks with junk around them.
        String bundle = "Bag Attributes\n  friendlyName: test\n" + TEST_CA + "Certificate:\n    Data: blah -- not-a-header\n"
                + EC_CA.replace("\n", "\r\n") + "\n\n" + LEAF + "trailing text\n";
        Collection<? extends Certificate> certs = cf.generateCertificates(new ByteArrayInputStream(bundle.getBytes(StandardCharsets.US_ASCII)));
        p("bundle " + certs.size());
        for (Certificate c : certs) p("  " + ((X509Certificate) c).getSubjectX500Principal().getName());
        // generateCertificate reads one block at a time and leaves the stream after it.
        InputStream in = new ByteArrayInputStream(bundle.getBytes(StandardCharsets.US_ASCII));
        for (int i = 0; i < 4; i++) {
            final InputStream fin = in;
            tryIt("one-by-one " + i, () -> ((X509Certificate) cf.generateCertificate(fin)).getSerialNumber());
        }
        // DER, concatenated DER.
        byte[] der1 = parse(TEST_CA).getEncoded(), der2 = parse(LEAF2).getEncoded();
        ByteArrayOutputStream two = new ByteArrayOutputStream();
        two.write(der1);
        two.write(der2);
        p("der " + ((X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der1))).getSubjectX500Principal());
        p("der x2 " + cf.generateCertificates(new ByteArrayInputStream(two.toByteArray())).size());
        InputStream din = new ByteArrayInputStream(two.toByteArray());
        cf.generateCertificate(din);
        p("der after first, available " + din.available() + " second " + ((X509Certificate) cf.generateCertificate(din)).getSerialNumber());
        p("getEncoded is input " + Arrays.equals(der2, ((X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der2))).getEncoded()));
        tryIt("empty", () -> cf.generateCertificate(new ByteArrayInputStream(new byte[0])));
        tryIt("empty all", () -> cf.generateCertificates(new ByteArrayInputStream(new byte[0])).size());
        tryIt("text only all", () -> cf.generateCertificates(new ByteArrayInputStream("hello\nworld\n".getBytes())).size());
        tryIt("text only", () -> cf.generateCertificate(new ByteArrayInputStream("hello\nworld\n".getBytes())));
        tryIt("truncated der", () -> cf.generateCertificate(new ByteArrayInputStream(Arrays.copyOf(der1, 100))));
        byte[] bad = der1.clone();
        bad[bad.length / 3] ^= 0x55;
        tryIt("corrupt signature verify", () -> {
            X509Certificate c = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(bad));
            c.verify(parse(TEST_CA).getPublicKey());
            return "verified";
        });
        tryIt("garbage", () -> cf.generateCertificate(new ByteArrayInputStream(new byte[] {0x30, 3, 1, 2, 3})));
        tryIt("bad pem footer", () -> cf.generateCertificate(new ByteArrayInputStream(TEST_CA.replace("END CERTIFICATE", "END FOO").getBytes())));
        tryIt("no such type", () -> CertificateFactory.getInstance("X.510"));
    }

    static void verify(X509Certificate isrg, X509Certificate ms, X509Certificate ca, X509Certificate ecca, X509Certificate leaf,
            X509Certificate leaf2) {
        p("== verify");
        X509Certificate[] certs = {isrg, ms, ca, ecca, leaf, leaf2};
        String[] names = {"isrg", "ms", "ca", "ecca", "leaf", "leaf2"};
        X509Certificate[] keys = {isrg, ms, ca, ecca};
        for (int i = 0; i < certs.length; i++) {
            for (int j = 0; j < keys.length; j++) {
                final X509Certificate c = certs[i], k = keys[j];
                tryIt(names[i] + " by " + names[j], () -> { c.verify(k.getPublicKey()); return "ok"; });
            }
        }
        tryIt("leaf by ca, null provider", () -> { leaf.verify(ca.getPublicKey(), (Provider) null); return "ok"; });
        tryIt("leaf by ca, missing provider", () -> { leaf.verify(ca.getPublicKey(), "NoSuchProvider"); return "ok"; });
        tryIt("signature initVerify(cert)", () -> {
            Signature s = Signature.getInstance(leaf.getSigAlgName());
            s.initVerify(ca);
            s.update(leaf.getTBSCertificate());
            return s.verify(leaf.getSignature());
        });
    }

    static void principal(String label, X500Principal x) {
        p(label + " toString=" + x);
        p("  2253=" + x.getName() + " | " + x.getName(X500Principal.RFC2253));
        p("  1779=" + x.getName(X500Principal.RFC1779));
        p("  canonical=" + x.getName(X500Principal.CANONICAL));
        p("  der=" + hex(x.getEncoded()) + " hash " + x.hashCode());
    }

    static void names() throws Exception {
        p("== names");
        String[] names = {
            "CN=a\\,b, O=\"x  y\", C=US",
            "cn=Duke+OU=Java+O=Sun",
            "CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US",
            "CN=Duke,OU=JavaSoft,O=Sun Microsystems,C=US",
            "EMAILADDRESS=a@b.c,DC=com,UID=x,STREET=1 st,T=boss,DNQ=q,SURNAME=s,IP=1.2.3.4,SERIALNUMBER=5",
            "OID.1.2.3=foo,  CN =  leading  ",
            "2.5.4.3=dotted, 2.5.4.10=#130361626364",
            "CN=#0c03616263",
            "CN=a=b#c, O=\\#lead, OU=\\ sp\\ ",
            "CN=semi;O=colon",
            "CN=\"quoted, value\" ; O=q\\\"uote\\\\back",
            "CN=lt\\<gt\\>",
            "S=state, ST=st, L=loc, GIVENNAME=g, INITIALS=i, GENERATION=III, DNQUALIFIER=d, EMAIL=e@x",
            "",
            "CN=  x  ",
            "CN=Ünïcödé",
        };
        for (String n : names) {
            try {
                X500Principal x = new X500Principal(n);
                principal("[" + n + "]", x);
                X500Principal y = new X500Principal(x.getEncoded());
                X500Principal z = new X500Principal(new ByteArrayInputStream(x.getEncoded()));
                X500Principal w = new X500Principal(x.getName());
                p("  roundtrip " + x.equals(y) + " " + x.equals(z) + " " + x.equals(w) + " " + Arrays.equals(x.getEncoded(), w.getEncoded()));
            } catch (Exception e) {
                p("[" + n + "] " + e.getClass().getName());
            }
        }
        String[] bad = {"CN", "CN=a\\", "CN=\"unterminated", "FOO=bar", "CN=a\\x", "1.2=#zz"};
        for (String n : bad) tryIt("bad [" + n + "]", () -> new X500Principal(n).getName());
        X500Principal a = new X500Principal("CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US");
        X500Principal b = new X500Principal("cn=duke,ou=javasoft,o=sun microsystems,c=us");
        X500Principal c = new X500Principal("CN=Duke,  OU=JavaSoft , O=Sun   Microsystems, C=US");
        p("equals " + a.equals(b) + " " + a.equals(c) + " hash " + (a.hashCode() == b.hashCode()));
        p("not equal " + a.equals(new X500Principal("CN=Duke2, OU=JavaSoft, O=Sun Microsystems, C=US")));
        tryIt("bad format", () -> a.getName("XML"));
        tryIt("null name", () -> new X500Principal((String) null));
        tryIt("bad der", () -> new X500Principal(new byte[] {0x30, 5, 1}));
        Map<String, String> oidMap = new HashMap<String, String>();
        oidMap.put("1.2.840.113549.1.9.1", "EMAIL");
        oidMap.put("2.5.4.3", "COMMONNAME");
        X500Principal e = new X500Principal("EMAILADDRESS=a@b.c, CN=x");
        p("oidMap 2253 " + e.getName(X500Principal.RFC2253, oidMap));
        p("oidMap 1779 " + e.getName(X500Principal.RFC1779, oidMap));
        Map<String, String> kwMap = new HashMap<String, String>();
        kwMap.put("FOO", "1.2.3.4");
        principal("keywordMap", new X500Principal("FOO=bar, CN=x", kwMap));
        // Reading a name from a stream leaves the rest.
        ByteArrayOutputStream two = new ByteArrayOutputStream();
        two.write(a.getEncoded());
        two.write(e.getEncoded());
        InputStream in = new ByteArrayInputStream(two.toByteArray());
        p("stream " + new X500Principal(in) + " / " + new X500Principal(in) + " / " + in.available());
    }

    static void keyStore(X509Certificate ca, X509Certificate ecca, X509Certificate leaf) throws Exception {
        p("== keystore");
        KeyStore ks = KeyStore.getInstance("PKCS12");
        p("type " + ks.getType());
        tryIt("uninitialized", () -> ks.size());
        ks.load(null, null);
        p("empty size " + ks.size() + " aliases " + Collections.list(ks.aliases()));
        ks.setCertificateEntry("ca", ca);
        ks.setCertificateEntry("ecca", ecca);
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair kp = kpg.generateKeyPair();
        char[] pw = "secret".toCharArray();
        ks.setKeyEntry("leaf", kp.getPrivate(), pw, new Certificate[] {leaf, ca});
        List<String> aliases = Collections.list(ks.aliases());
        Collections.sort(aliases);
        p("size " + ks.size() + " aliases " + aliases);
        for (String a : new String[] {"ca", "ecca", "leaf", "CA", "missing"}) {
            Certificate c = ks.getCertificate(a);
            p(a + " contains " + ks.containsAlias(a) + " cert " + ks.isCertificateEntry(a) + " key " + ks.isKeyEntry(a)
                    + " subject " + (c == null ? null : ((X509Certificate) c).getSubjectX500Principal().getName())
                    + " chain " + (ks.getCertificateChain(a) == null ? null : ks.getCertificateChain(a).length)
                    + " date " + (ks.getCreationDate(a) != null));
        }
        Key k = ks.getKey("leaf", pw);
        p("key " + k.getAlgorithm() + " " + k.getFormat() + " same " + Arrays.equals(k.getEncoded(), kp.getPrivate().getEncoded()));
        p("alias of leaf " + ks.getCertificateAlias(leaf) + " of ecca " + ks.getCertificateAlias(ecca));
        p("entry ca " + ks.entryInstanceOf("ca", KeyStore.TrustedCertificateEntry.class) + " leaf "
                + ks.entryInstanceOf("leaf", KeyStore.PrivateKeyEntry.class));
        KeyStore.Entry entry = ks.getEntry("ca", null);
        p("entry " + entry.getClass().getSimpleName() + " " + ((KeyStore.TrustedCertificateEntry) entry).getTrustedCertificate().equals(ca));
        KeyStore.PrivateKeyEntry pke = (KeyStore.PrivateKeyEntry) ks.getEntry("leaf", new KeyStore.PasswordProtection(pw));
        p("pke chain " + pke.getCertificateChain().length + " cert " + pke.getCertificate().equals(leaf));
        tryIt("cert over key", () -> { ks.setCertificateEntry("leaf", ca); return "ok"; });
        tryIt("key without chain", () -> { ks.setKeyEntry("x", kp.getPrivate(), pw, null); return "ok"; });
        ks.deleteEntry("ecca");
        ks.deleteEntry("nothing");
        p("after delete " + ks.size() + " " + ks.containsAlias("ecca") + " " + ks.getCertificate("ecca"));
        ks.setCertificateEntry("ca", ecca);
        p("replaced " + ((X509Certificate) ks.getCertificate("ca")).getSubjectX500Principal().getName() + " size " + ks.size());
        KeyStore ks2 = KeyStore.getInstance("PKCS12");
        ks2.load((KeyStore.LoadStoreParameter) null);
        p("load param size " + ks2.size());
        tryIt("no such keystore", () -> KeyStore.getInstance("NOPE"));
    }

    static void certPath(X509Certificate ca, X509Certificate leaf) throws Exception {
        p("== certpath");
        List<String> encs = new ArrayList<String>();
        for (Iterator<String> it = cf.getCertPathEncodings(); it.hasNext(); ) encs.add(it.next());
        p("encodings " + encs);
        CertPath path = cf.generateCertPath(Arrays.asList(leaf, ca));
        p("type " + path.getType() + " size " + path.getCertificates().size());
        for (Iterator<String> it = path.getEncodings(); it.hasNext(); ) p("path encoding " + it.next());
        byte[] pki = path.getEncoded();
        p("pkipath " + sha256(pki) + " same as explicit " + Arrays.equals(pki, path.getEncoded("PkiPath")));
        CertPath back = cf.generateCertPath(new ByteArrayInputStream(pki));
        p("pkipath back " + back.getCertificates().size() + " first " + ((X509Certificate) back.getCertificates().get(0)).getSubjectX500Principal().getName()
                + " equals " + back.equals(path));
        byte[] p7 = path.getEncoded("PKCS7");
        p("pkcs7 " + sha256(p7));
        CertPath back7 = cf.generateCertPath(new ByteArrayInputStream(p7), "PKCS7");
        p("pkcs7 back " + back7.getCertificates().size() + " equals " + back7.equals(path));
        Collection<? extends Certificate> fromP7 = cf.generateCertificates(new ByteArrayInputStream(p7));
        p("pkcs7 certs " + fromP7.size());
        String p7pem = "-----BEGIN PKCS7-----\n" + Base64.getMimeEncoder().encodeToString(p7) + "\n-----END PKCS7-----\n";
        p("pkcs7 pem certs " + cf.generateCertificates(new ByteArrayInputStream(p7pem.getBytes())).size());
        tryIt("bad encoding", () -> path.getEncoded("XML"));
        tryIt("crl", () -> cf.generateCRL(new ByteArrayInputStream(new byte[] {0x30, 0})));
    }

}
