package java.util.jar;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A jar file: a ZipFile with its manifest. Signatures are not verified (the verify flag is accepted and
 * ignored), and multi-release versioning is not applied (no Runtime.Version in libcore).
 */
public class JarFile extends ZipFile {
    public static final String MANIFEST_NAME = "META-INF/MANIFEST.MF";

    private Manifest manifest;
    private boolean manifestRead;
    private String manifestName;

    public JarFile(String name) throws IOException {
        this(new File(name), true, ZipFile.OPEN_READ);
    }

    public JarFile(String name, boolean verify) throws IOException {
        this(new File(name), verify, ZipFile.OPEN_READ);
    }

    public JarFile(File file) throws IOException {
        this(file, true, ZipFile.OPEN_READ);
    }

    public JarFile(File file, boolean verify) throws IOException {
        this(file, verify, ZipFile.OPEN_READ);
    }

    public JarFile(File file, boolean verify, int mode) throws IOException {
        super(file, mode);
        Enumeration<? extends ZipEntry> en = super.entries();
        while (en.hasMoreElements()) {
            String n = en.nextElement().getName();
            if (n.equalsIgnoreCase(MANIFEST_NAME)) {
                manifestName = n;
                break;
            }
        }
    }

    public final boolean isMultiRelease() {
        try {
            Manifest m = getManifest();
            return m != null && "true".equalsIgnoreCase(m.getMainAttributes().getValue(Attributes.Name.MULTI_RELEASE));
        } catch (IOException e) {
            return false;
        }
    }

    public Manifest getManifest() throws IOException {
        synchronized (this) {
            if (!manifestRead) {
                if (manifestName != null) {
                    ZipEntry e = super.getEntry(manifestName);
                    InputStream in = super.getInputStream(e);
                    try {
                        manifest = new Manifest(in);
                    } finally {
                        in.close();
                    }
                }
                manifestRead = true;
            }
            return manifest;
        }
    }

    public JarEntry getJarEntry(String name) {
        return (JarEntry) getEntry(name);
    }

    public ZipEntry getEntry(String name) {
        ZipEntry ze = super.getEntry(name);
        return ze != null ? wrap(ze) : null;
    }

    private JarEntry wrap(ZipEntry ze) {
        JarEntry je = new JarEntry(ze);
        try {
            Manifest m = getManifest();
            if (m != null) {
                je.attr = m.getAttributes(ze.getName());
            }
        } catch (IOException e) {
            // an unreadable manifest gives entries without attributes
        }
        return je;
    }

    public Enumeration<JarEntry> entries() {
        final Enumeration<? extends ZipEntry> en = super.entries();
        return new Enumeration<JarEntry>() {
            public boolean hasMoreElements() {
                return en.hasMoreElements();
            }

            public JarEntry nextElement() {
                return wrap(en.nextElement());
            }
        };
    }

    public Stream<JarEntry> stream() {
        ArrayList<JarEntry> list = new ArrayList<>();
        Enumeration<JarEntry> en = entries();
        while (en.hasMoreElements()) {
            list.add(en.nextElement());
        }
        return list.stream();
    }

    public Stream<JarEntry> versionedStream() {
        return stream();
    }

    public synchronized InputStream getInputStream(ZipEntry ze) throws IOException {
        return super.getInputStream(ze);
    }
}
