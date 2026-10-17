package android.net.http;

import android.os.Bundle;

import java.io.ByteArrayInputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/** An SSL certificate summary, as shown in a browser's page info. Ported from AOSP. */
public class SslCertificate {
    private static final String ISO_8601_DATE_FORMAT = "yyyy-MM-dd HH:mm:ssZ";

    private static final String ISSUED_TO = "issued-to";
    private static final String ISSUED_BY = "issued-by";
    private static final String VALID_NOT_BEFORE = "valid-not-before";
    private static final String VALID_NOT_AFTER = "valid-not-after";
    private static final String X509_CERTIFICATE = "x509-certificate";

    private final java.security.cert.X509Certificate mX509Certificate;
    private final DName mIssuedTo;
    private final DName mIssuedBy;
    private final Date mValidNotBefore;
    private final Date mValidNotAfter;

    public static Bundle saveState(SslCertificate certificate) {
        if (certificate == null) return null;
        Bundle bundle = new Bundle();
        bundle.putString(ISSUED_TO, certificate.getIssuedTo().getDName());
        bundle.putString(ISSUED_BY, certificate.getIssuedBy().getDName());
        bundle.putString(VALID_NOT_BEFORE, certificate.getValidNotBefore());
        bundle.putString(VALID_NOT_AFTER, certificate.getValidNotAfter());
        java.security.cert.X509Certificate x509Certificate = certificate.mX509Certificate;
        if (x509Certificate != null) {
            try {
                bundle.putByteArray(X509_CERTIFICATE, x509Certificate.getEncoded());
            } catch (CertificateEncodingException ignored) {
            }
        }
        return bundle;
    }

    public static SslCertificate restoreState(Bundle bundle) {
        if (bundle == null) return null;
        java.security.cert.X509Certificate x509Certificate = null;
        byte[] bytes = bundle.getByteArray(X509_CERTIFICATE);
        if (bytes != null) {
            try {
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                Certificate cert = certFactory.generateCertificate(new ByteArrayInputStream(bytes));
                x509Certificate = (java.security.cert.X509Certificate) cert;
            } catch (Exception e) {
                x509Certificate = null;
            }
        }
        return new SslCertificate(bundle.getString(ISSUED_TO), bundle.getString(ISSUED_BY),
                parseDate(bundle.getString(VALID_NOT_BEFORE)), parseDate(bundle.getString(VALID_NOT_AFTER)),
                x509Certificate);
    }

    @Deprecated
    public SslCertificate(String issuedTo, String issuedBy, String validNotBefore, String validNotAfter) {
        this(issuedTo, issuedBy, parseDate(validNotBefore), parseDate(validNotAfter), null);
    }

    @Deprecated
    public SslCertificate(String issuedTo, String issuedBy, Date validNotBefore, Date validNotAfter) {
        this(issuedTo, issuedBy, validNotBefore, validNotAfter, null);
    }

    public SslCertificate(java.security.cert.X509Certificate certificate) {
        this(certificate.getSubjectDN().getName(), certificate.getIssuerDN().getName(), certificate.getNotBefore(),
                certificate.getNotAfter(), certificate);
    }

    private SslCertificate(String issuedTo, String issuedBy, Date validNotBefore, Date validNotAfter,
            java.security.cert.X509Certificate x509Certificate) {
        mIssuedTo = new DName(issuedTo);
        mIssuedBy = new DName(issuedBy);
        mValidNotBefore = cloneDate(validNotBefore);
        mValidNotAfter = cloneDate(validNotAfter);
        mX509Certificate = x509Certificate;
    }

    public Date getValidNotBeforeDate() {
        return cloneDate(mValidNotBefore);
    }

    @Deprecated
    public String getValidNotBefore() {
        return formatDate(mValidNotBefore);
    }

    public Date getValidNotAfterDate() {
        return cloneDate(mValidNotAfter);
    }

    @Deprecated
    public String getValidNotAfter() {
        return formatDate(mValidNotAfter);
    }

    public DName getIssuedTo() {
        return mIssuedTo;
    }

    public DName getIssuedBy() {
        return mIssuedBy;
    }

    public java.security.cert.X509Certificate getX509Certificate() {
        return mX509Certificate;
    }

    @Override
    public String toString() {
        return "Issued to: " + mIssuedTo.getDName() + ";\n" + "Issued by: " + mIssuedBy.getDName() + ";\n";
    }

    private static Date parseDate(String string) {
        if (string == null) return null;
        try {
            return new SimpleDateFormat(ISO_8601_DATE_FORMAT).parse(string);
        } catch (ParseException e) {
            return null;
        }
    }

    private static String formatDate(Date date) {
        if (date == null) return "";
        return new SimpleDateFormat(ISO_8601_DATE_FORMAT).format(date);
    }

    private static Date cloneDate(Date date) {
        if (date == null) return null;
        return (Date) date.clone();
    }

    /** A distinguished name: the first CN, O and OU values are picked out. */
    public class DName {
        private String mDName;
        private String mCName;
        private String mOName;
        private String mUName;

        public DName(String dName) {
            if (dName == null) return;
            mDName = dName;
            // AOSP parses with BouncyCastle's X509Name; this handles RFC 2253 and RFC 1779 style names.
            for (String[] rdn : parseRdns(dName)) {
                String key = rdn[0].trim().toUpperCase(java.util.Locale.ROOT);
                if (key.startsWith("OID.")) key = key.substring(4);
                String value = rdn[1];
                if ((key.equals("CN") || key.equals("2.5.4.3")) && mCName == null) {
                    mCName = value;
                } else if ((key.equals("O") || key.equals("2.5.4.10")) && mOName == null) {
                    mOName = value;
                } else if ((key.equals("OU") || key.equals("2.5.4.11")) && mUName == null) {
                    mUName = value;
                }
            }
        }

        public String getDName() {
            return mDName != null ? mDName : "";
        }

        public String getCName() {
            return mCName != null ? mCName : "";
        }

        public String getOName() {
            return mOName != null ? mOName : "";
        }

        public String getUName() {
            return mUName != null ? mUName : "";
        }
    }

    // Splits "CN=a, O=b\, c+OU=\"d\"" into {key, value} pairs, unescaping values.
    private static ArrayList<String[]> parseRdns(String dn) {
        ArrayList<String[]> out = new ArrayList<String[]>();
        int n = dn.length();
        int i = 0;
        while (i < n) {
            StringBuilder key = new StringBuilder();
            while (i < n && dn.charAt(i) != '=' && dn.charAt(i) != ',' && dn.charAt(i) != ';' && dn.charAt(i) != '+') {
                key.append(dn.charAt(i++));
            }
            if (i >= n || dn.charAt(i) != '=') {
                i++;
                continue;
            }
            i++;
            while (i < n && dn.charAt(i) == ' ') i++;
            StringBuilder value = new StringBuilder();
            boolean quoted = i < n && dn.charAt(i) == '"';
            if (quoted) i++;
            while (i < n) {
                char ch = dn.charAt(i);
                if (ch == '\\' && i + 1 < n) {
                    char next = dn.charAt(i + 1);
                    if (isHex(next) && i + 2 < n && isHex(dn.charAt(i + 2))) {
                        value.append((char) Integer.parseInt(dn.substring(i + 1, i + 3), 16));
                        i += 3;
                    } else {
                        value.append(next);
                        i += 2;
                    }
                    continue;
                }
                if (quoted) {
                    i++;
                    if (ch == '"') {
                        quoted = false;
                        continue;
                    }
                    value.append(ch);
                    continue;
                }
                if (ch == ',' || ch == ';' || ch == '+') break;
                value.append(ch);
                i++;
            }
            i++;
            out.add(new String[] {key.toString(), value.toString().trim()});
        }
        return out;
    }

    private static boolean isHex(char ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
    }
}
