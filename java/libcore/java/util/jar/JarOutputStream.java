package java.util.jar;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class JarOutputStream extends ZipOutputStream {
    private static final int JAR_MAGIC = 0xCAFE;

    private boolean firstEntry = true;

    public JarOutputStream(OutputStream out, Manifest man) throws IOException {
        super(out);
        if (man == null) {
            throw new NullPointerException("man");
        }
        ZipEntry e = new ZipEntry(JarFile.MANIFEST_NAME);
        putNextEntry(e);
        BufferedOutputStream bo = new BufferedOutputStream(this);
        man.write(bo);
        bo.flush();
        closeEntry();
    }

    public JarOutputStream(OutputStream out) throws IOException {
        super(out);
    }

    /* The first entry carries the 0xCAFE extra field that marks a jar, as in the JDK. */
    public void putNextEntry(ZipEntry ze) throws IOException {
        if (firstEntry) {
            byte[] edata = ze.getExtra();
            if (edata == null || !hasMagic(edata)) {
                if (edata == null) {
                    edata = new byte[4];
                } else {
                    byte[] tmp = new byte[edata.length + 4];
                    System.arraycopy(edata, 0, tmp, 4, edata.length);
                    edata = tmp;
                }
                edata[0] = (byte) (JAR_MAGIC & 0xff);
                edata[1] = (byte) (JAR_MAGIC >> 8);
                edata[2] = 0;
                edata[3] = 0;
                ze.setExtra(edata);
            }
            firstEntry = false;
        }
        super.putNextEntry(ze);
    }

    private static boolean hasMagic(byte[] edata) {
        int i = 0;
        while (i + 4 <= edata.length) {
            int id = (edata[i] & 0xff) | ((edata[i + 1] & 0xff) << 8);
            if (id == JAR_MAGIC) {
                return true;
            }
            i += 4 + ((edata[i + 2] & 0xff) | ((edata[i + 3] & 0xff) << 8));
        }
        return false;
    }
}
