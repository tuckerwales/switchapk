package java.net;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * jar: URLs, as Android's class loaders return for APK entries (getResource). Only file: jars are supported;
 * opened JarFiles are shared per path, as the JDK's cache does when useCaches is on.
 */
final class JarURLConnectionImpl extends JarURLConnection {
    private static final HashMap<String, JarFile> CACHE = new HashMap<String, JarFile>();

    private JarFile jarFile;
    private JarEntry jarEntry;
    private boolean ownsJar;

    JarURLConnectionImpl(URL url) throws MalformedURLException {
        super(url);
    }

    public void connect() throws IOException {
        if (connected) {
            return;
        }
        URL fileUrl = getJarFileURL();
        if (!"file".equals(fileUrl.getProtocol())) {
            throw new IOException("unsupported jar file URL: " + fileUrl);
        }
        String path;
        try {
            path = new File(fileUrl.toURI()).getPath();
        } catch (URISyntaxException | IllegalArgumentException e) {
            path = decode(fileUrl.getPath());
        }
        if (getUseCaches()) {
            synchronized (CACHE) {
                jarFile = CACHE.get(path);
                if (jarFile == null) {
                    jarFile = new JarFile(path);
                    CACHE.put(path, jarFile);
                }
            }
        } else {
            jarFile = new JarFile(path);
            ownsJar = true;
        }
        String entry = getEntryName();
        if (entry != null) {
            jarEntry = jarFile.getJarEntry(entry);
            if (jarEntry == null) {
                if (ownsJar) {
                    jarFile.close();
                }
                throw new FileNotFoundException("JAR entry " + entry + " not found in " + jarFile.getName());
            }
        }
        connected = true;
    }

    public JarFile getJarFile() throws IOException {
        connect();
        return jarFile;
    }

    public JarEntry getJarEntry() throws IOException {
        connect();
        return jarEntry;
    }

    public InputStream getInputStream() throws IOException {
        connect();
        if (jarEntry == null) {
            throw new IOException("no entry name specified");
        }
        final InputStream in = jarFile.getInputStream(jarEntry);
        if (!ownsJar) {
            return in;
        }
        /* an uncached jar is closed with the entry's stream */
        return new java.io.FilterInputStream(in) {
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    jarFile.close();
                }
            }
        };
    }

    public int getContentLength() {
        long n = getContentLengthLong();
        return n > Integer.MAX_VALUE ? -1 : (int) n;
    }

    public long getContentLengthLong() {
        try {
            connect();
        } catch (IOException e) {
            return -1;
        }
        return jarEntry == null ? -1 : jarEntry.getSize();
    }

    public String getContentType() {
        String entry = getEntryName();
        if (entry == null) {
            return "x-java/jar";
        }
        String type = guessContentTypeFromName(entry);
        return type != null ? type : "content/unknown";
    }

    public long getLastModified() {
        try {
            connect();
        } catch (IOException e) {
            return 0;
        }
        return jarEntry == null ? 0 : jarEntry.getTime();
    }
}
