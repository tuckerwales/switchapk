import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.jar.*;

/** jar: URLs, as class loaders return for APK entries: parsing, JarURLConnection and reading entries. */
public class JarUrlTest {
    static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[256];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return out.toString("UTF-8");
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "jarurltest dir");
        dir.mkdirs();
        File jar = new File(dir, "my lib.jar");
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().putValue("Created-By", "JarUrlTest");
        try (JarOutputStream jo = new JarOutputStream(new FileOutputStream(jar), mf)) {
            jo.putNextEntry(new JarEntry("kotlin/kotlin.kotlin_builtins"));
            jo.write("builtins data".getBytes(StandardCharsets.UTF_8));
            jo.closeEntry();
            jo.putNextEntry(new JarEntry("dir/with space.txt"));
            jo.write("spaced é".getBytes(StandardCharsets.UTF_8));
            jo.closeEntry();
        }

        String base = "jar:" + jar.toURI().toString() + "!/";
        URL u = new URL(base + "kotlin/kotlin.kotlin_builtins");
        System.out.println("protocol " + u.getProtocol() + " host '" + u.getHost() + "' query " + u.getQuery());
        System.out.println("path ends " + u.getPath().substring(u.getPath().indexOf("!/")));
        System.out.println("file has jar " + u.getFile().contains("my%20lib.jar"));
        URLConnection c = u.openConnection();
        System.out.println("connection is jar " + (c instanceof JarURLConnection));
        JarURLConnection jc = (JarURLConnection) c;
        System.out.println("entry " + jc.getEntryName());
        System.out.println("jar file url ends " + jc.getJarFileURL().toString().endsWith("my%20lib.jar"));
        System.out.println("jar entry " + jc.getJarEntry().getName() + " size " + jc.getJarEntry().getSize());
        System.out.println("main attr " + jc.getMainAttributes().getValue("Created-By"));
        System.out.println("content " + read(jc.getInputStream()));
        System.out.println("content length " + jc.getContentLengthLong());

        URL spaced = new URL(base + "dir/with%20space.txt");
        JarURLConnection sc = (JarURLConnection) spaced.openConnection();
        System.out.println("decoded entry '" + sc.getEntryName() + "' -> " + read(spaced.openStream()));

        try {
            new URL(base + "missing.txt").openStream();
            System.out.println("no exception");
        } catch (FileNotFoundException e) {
            System.out.println("missing entry: FileNotFoundException");
        }
        try {
            new URL("jar:" + jar.toURI() + "kotlin/x").openConnection();
            System.out.println("no exception");
        } catch (MalformedURLException e) {
            System.out.println("no separator: MalformedURLException");
        }

        URLConnection nc = u.openConnection();
        nc.setUseCaches(false);
        System.out.println("uncached " + read(nc.getInputStream()));

        File plain = new File(dir, "plain file.txt");
        try (FileOutputStream fo = new FileOutputStream(plain)) {
            fo.write("plain".getBytes(StandardCharsets.UTF_8));
        }
        System.out.println("file url " + read(plain.toURI().toURL().openStream()));
        System.out.println(new URI("file", null, "/a b/100%/c#d \u00e9\u00a0", "frag ment"));
        System.out.println(new URI("http", "user name", "host", 80, "/p q", "x=1 2&y=[]", null));
        System.out.println(new URI("s", "auth ority", "/p", "q r", "f"));
        String d = dir.toURI().toString();
        System.out.println("dir uri ends " + d.substring(d.length() - 16) + " starts " + d.substring(0, 6));
    }
}
