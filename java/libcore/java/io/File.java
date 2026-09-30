package java.io;

import java.util.ArrayList;
import libcore.io.Os;

public class File implements Serializable, Comparable<File> {
    public static final char separatorChar = '/';
    public static final String separator = "/";
    public static final char pathSeparatorChar = ':';
    public static final String pathSeparator = ":";

    private final String path;

    public File(String pathname) {
        if (pathname == null) {
            throw new NullPointerException();
        }
        path = fixSlashes(pathname);
    }

    public File(String parent, String child) {
        if (child == null) {
            throw new NullPointerException();
        }
        path = parent == null ? fixSlashes(child) : join(parent, child);
    }

    public File(File parent, String child) {
        this(parent == null ? null : parent.getPath(), child);
    }

    public File(java.net.URI uri) {
        this(uri.getPath());
    }

    private static String join(String prefix, String suffix) {
        int prefixLength = prefix.length();
        boolean haveSlash = (prefixLength > 0 && prefix.charAt(prefixLength - 1) == separatorChar);
        if (!haveSlash) {
            haveSlash = (suffix.length() > 0 && suffix.charAt(0) == separatorChar);
        }
        return fixSlashes(haveSlash ? (prefix + suffix) : (prefix + separatorChar + suffix));
    }

    private static String fixSlashes(String origPath) {
        boolean lastWasSlash = false;
        char[] newPath = origPath.toCharArray();
        int length = newPath.length;
        int newLength = 0;
        for (int i = 0; i < length; ++i) {
            char ch = newPath[i];
            if (ch == '/') {
                if (!lastWasSlash) {
                    newPath[newLength++] = separatorChar;
                    lastWasSlash = true;
                }
            } else {
                newPath[newLength++] = ch;
                lastWasSlash = false;
            }
        }
        if (lastWasSlash && newLength > 1) {
            newLength--;
        }
        return (newLength != length) ? new String(newPath, 0, newLength) : origPath;
    }

    public String getName() {
        int separatorIndex = path.lastIndexOf(separator);
        return (separatorIndex < 0) ? path : path.substring(separatorIndex + 1);
    }

    public String getParent() {
        int length = path.length(), firstInPath = 0;
        int index = path.lastIndexOf(separatorChar);
        if (index == -1 || path.charAt(length - 1) == separatorChar) {
            return null;
        }
        if (path.indexOf(separatorChar) == index && path.charAt(firstInPath) == separatorChar) {
            return path.substring(0, index + 1);
        }
        return path.substring(0, index);
    }

    public File getParentFile() {
        String tempParent = getParent();
        return tempParent == null ? null : new File(tempParent);
    }

    public String getPath() {
        return path;
    }

    public boolean isAbsolute() {
        return path.length() > 0 && path.charAt(0) == separatorChar;
    }

    public String getAbsolutePath() {
        if (isAbsolute()) {
            return path;
        }
        String userDir = System.getProperty("user.dir");
        return path.isEmpty() ? userDir : join(userDir, path);
    }

    public File getAbsoluteFile() {
        return new File(getAbsolutePath());
    }

    public String getCanonicalPath() throws IOException {
        String abs = getAbsolutePath();
        ArrayList<String> parts = new ArrayList<String>();
        for (String p : abs.split("/")) {
            if (p.isEmpty() || p.equals(".")) {
                continue;
            }
            if (p.equals("..")) {
                if (!parts.isEmpty()) {
                    parts.remove(parts.size() - 1);
                }
                continue;
            }
            parts.add(p);
        }
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append('/').append(p);
        }
        return sb.length() == 0 ? "/" : sb.toString();
    }

    public File getCanonicalFile() throws IOException {
        return new File(getCanonicalPath());
    }

    public java.net.URL toURL() throws java.net.MalformedURLException {
        return new java.net.URL("file://" + getAbsolutePath());
    }

    public java.net.URI toURI() {
        return java.net.URI.create("file://" + getAbsolutePath());
    }

    private long[] stat() {
        return Os.stat(path);
    }

    public boolean canRead() {
        long[] s = stat();
        return s[0] != 0 && s[5] != 0;
    }

    public boolean canWrite() {
        long[] s = stat();
        return s[0] != 0 && s[6] != 0;
    }

    public boolean canExecute() {
        return isDirectory();
    }

    public boolean exists() {
        return stat()[0] != 0;
    }

    public boolean isDirectory() {
        return stat()[1] != 0;
    }

    public boolean isFile() {
        return stat()[2] != 0;
    }

    public boolean isHidden() {
        return getName().startsWith(".");
    }

    public long lastModified() {
        return stat()[4];
    }

    public long length() {
        return stat()[3];
    }

    public boolean createNewFile() throws IOException {
        if (exists()) {
            return false;
        }
        int fd = Os.open(path, Os.O_WRONLY | Os.O_CREAT, 0666);
        Os.close(fd);
        return true;
    }

    public boolean delete() {
        return Os.remove(path);
    }

    public void deleteOnExit() {
    }

    public String[] list() {
        return Os.list(path);
    }

    public String[] list(FilenameFilter filter) {
        String[] names = list();
        if (names == null || filter == null) {
            return names;
        }
        ArrayList<String> v = new ArrayList<String>();
        for (String name : names) {
            if (filter.accept(this, name)) {
                v.add(name);
            }
        }
        return v.toArray(new String[v.size()]);
    }

    public File[] listFiles() {
        return listFiles((FileFilter) null);
    }

    public File[] listFiles(FilenameFilter filter) {
        String[] ss = list(filter);
        if (ss == null) {
            return null;
        }
        File[] fs = new File[ss.length];
        for (int i = 0; i < ss.length; i++) {
            fs[i] = new File(this, ss[i]);
        }
        return fs;
    }

    public File[] listFiles(FileFilter filter) {
        String[] ss = list();
        if (ss == null) {
            return null;
        }
        ArrayList<File> files = new ArrayList<File>();
        for (String s : ss) {
            File f = new File(this, s);
            if (filter == null || filter.accept(f)) {
                files.add(f);
            }
        }
        return files.toArray(new File[files.size()]);
    }

    public boolean mkdir() {
        return Os.mkdir(path);
    }

    public boolean mkdirs() {
        if (exists()) {
            return false;
        }
        if (mkdir()) {
            return true;
        }
        File parent = getParentFile();
        return parent != null && (parent.mkdirs() || parent.exists()) && mkdir();
    }

    public boolean renameTo(File dest) {
        return Os.rename(path, dest.path);
    }

    public boolean setLastModified(long time) {
        return Os.setLastModified(path, time);
    }

    public boolean setReadOnly() {
        return true;
    }

    public boolean setWritable(boolean writable, boolean ownerOnly) {
        return true;
    }

    public boolean setWritable(boolean writable) {
        return true;
    }

    public boolean setReadable(boolean readable, boolean ownerOnly) {
        return true;
    }

    public boolean setReadable(boolean readable) {
        return true;
    }

    public boolean setExecutable(boolean executable, boolean ownerOnly) {
        return true;
    }

    public boolean setExecutable(boolean executable) {
        return true;
    }

    public static File[] listRoots() {
        return new File[] {new File("/")};
    }

    public long getTotalSpace() {
        return 32L * 1024 * 1024 * 1024;
    }

    public long getFreeSpace() {
        return Os.freeSpace(path);
    }

    public long getUsableSpace() {
        return Os.freeSpace(path);
    }

    public static File createTempFile(String prefix, String suffix, File directory) throws IOException {
        if (prefix.length() < 3) {
            throw new IllegalArgumentException("prefix must be at least 3 characters");
        }
        if (suffix == null) {
            suffix = ".tmp";
        }
        File tmpDirFile = directory;
        if (tmpDirFile == null) {
            tmpDirFile = new File(System.getProperty("java.io.tmpdir", "."));
            tmpDirFile.mkdirs();
        }
        File result;
        java.util.Random r = new java.util.Random();
        do {
            result = new File(tmpDirFile, prefix + Math.abs(r.nextInt()) + suffix);
        } while (!result.createNewFile());
        return result;
    }

    public static File createTempFile(String prefix, String suffix) throws IOException {
        return createTempFile(prefix, suffix, null);
    }

    public int compareTo(File pathname) {
        return path.compareTo(pathname.path);
    }

    public boolean equals(Object obj) {
        return obj instanceof File && path.equals(((File) obj).getPath());
    }

    public int hashCode() {
        return path.hashCode() ^ 1234321;
    }

    public String toString() {
        return path;
    }

    public java.nio.file.Path toPath() {
        return java.nio.file.Paths.get(path);
    }
}
