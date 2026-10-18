package java.net;

import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/** A connection to a jar (or APK) entry: jar:&lt;file URL&gt;!/&lt;entry&gt;. */
public abstract class JarURLConnection extends URLConnection {
    private URL jarFileURL;
    private String entryName;

    protected URLConnection jarFileURLConnection;

    protected JarURLConnection(URL url) throws MalformedURLException {
        super(url);
        String spec = url.getFile();
        int separator = spec.indexOf("!/");
        if (separator == -1) {
            throw new MalformedURLException("no !/ found in url spec:" + spec);
        }
        jarFileURL = new URL(spec.substring(0, separator++));
        entryName = null;
        if (++separator != spec.length()) {
            entryName = decode(spec.substring(separator));
        }
    }

    /** %XX escapes as UTF-8 bytes, as Java's ParseUtil.decode ('+' stays a plus). */
    static String decode(String s) {
        if (s.indexOf('%') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        int i = 0;
        while (i < s.length()) {
            /* a run of escapes becomes one UTF-8 sequence */
            while (i + 2 < s.length() && s.charAt(i) == '%'
                    && Character.digit(s.charAt(i + 1), 16) >= 0 && Character.digit(s.charAt(i + 2), 16) >= 0) {
                bytes.write(Character.digit(s.charAt(i + 1), 16) << 4 | Character.digit(s.charAt(i + 2), 16));
                i += 3;
            }
            if (bytes.size() > 0) {
                sb.append(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
                bytes.reset();
            }
            if (i < s.length()) {
                sb.append(s.charAt(i++));
            }
        }
        return sb.toString();
    }

    public URL getJarFileURL() {
        return jarFileURL;
    }

    public String getEntryName() {
        return entryName;
    }

    public abstract JarFile getJarFile() throws IOException;

    public Manifest getManifest() throws IOException {
        return getJarFile().getManifest();
    }

    public JarEntry getJarEntry() throws IOException {
        return entryName != null ? getJarFile().getJarEntry(entryName) : null;
    }

    public Attributes getAttributes() throws IOException {
        JarEntry e = getJarEntry();
        return e != null ? e.getAttributes() : null;
    }

    public Attributes getMainAttributes() throws IOException {
        Manifest man = getManifest();
        return man != null ? man.getMainAttributes() : null;
    }

    /** Signed jars are not verified here, so there are no certificates to report. */
    public java.security.cert.Certificate[] getCertificates() throws IOException {
        return null;
    }
}
