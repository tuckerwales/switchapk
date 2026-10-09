package java.util.jar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Reads a jar stream; the manifest is read when it is the first entry (after META-INF/). No verification. */
public class JarInputStream extends ZipInputStream {
    private Manifest man;
    private JarEntry first;

    public JarInputStream(InputStream in) throws IOException {
        this(in, true);
    }

    public JarInputStream(InputStream in, boolean verify) throws IOException {
        super(in);
        JarEntry e = (JarEntry) super.getNextEntry();
        if (e != null && e.getName().equalsIgnoreCase("META-INF/")) {
            e = (JarEntry) super.getNextEntry();
        }
        if (e != null && JarFile.MANIFEST_NAME.equalsIgnoreCase(e.getName())) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = read(buf, 0, buf.length)) != -1) {
                bo.write(buf, 0, n);
            }
            man = new Manifest(new ByteArrayInputStream(bo.toByteArray()));
            closeEntry();
            e = (JarEntry) super.getNextEntry();
        }
        first = e;
        if (first != null && man != null) {
            first.attr = man.getAttributes(first.getName());
        }
    }

    public Manifest getManifest() {
        return man;
    }

    public ZipEntry getNextEntry() throws IOException {
        JarEntry e;
        if (first == null) {
            e = (JarEntry) super.getNextEntry();
            if (e != null && man != null) {
                e.attr = man.getAttributes(e.getName());
            }
        } else {
            e = first;
            first = null;
        }
        return e;
    }

    public JarEntry getNextJarEntry() throws IOException {
        return (JarEntry) getNextEntry();
    }

    public int read(byte[] b, int off, int len) throws IOException {
        if (first != null) {
            // the stream is positioned before the first entry's data until getNextEntry hands it out
            return -1;
        }
        return super.read(b, off, len);
    }

    protected ZipEntry createZipEntry(String name) {
        return new JarEntry(name);
    }
}
