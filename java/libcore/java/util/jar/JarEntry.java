package java.util.jar;

import java.io.IOException;
import java.util.zip.ZipEntry;

/**
 * A jar entry. Signed jars are not verified: getCertificates() and getCodeSigners() are left out (the VM answers
 * null for them, which is what an unsigned entry returns).
 */
public class JarEntry extends ZipEntry {
    Attributes attr;

    public JarEntry(String name) {
        super(name);
    }

    public JarEntry(ZipEntry ze) {
        super(ze);
    }

    public JarEntry(JarEntry je) {
        this((ZipEntry) je);
        this.attr = je.attr;
    }

    public Attributes getAttributes() throws IOException {
        return attr;
    }

    public String getRealName() {
        return super.getName();
    }
}
