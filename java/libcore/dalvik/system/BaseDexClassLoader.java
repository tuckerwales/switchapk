package dalvik.system;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Class loading goes to the VM's one class path (the app's and the framework's dex files): dex files named by
 * dexPath are not loaded (VM dex loading is not implemented). Native library lookup follows DexPathList: each
 * element of the library search path is a directory or "path/to/file.apk!/lib/abi", and findLibrary returns the
 * first that holds System.mapLibraryName(name).
 */
public class BaseDexClassLoader extends ClassLoader {
    private final String mDexPath;
    private final ArrayList<String> mNativeDirs = new ArrayList<String>();
    // Zip path element -> library file names under its directory, read once.
    private final java.util.HashMap<String, HashSet<String>> mZipLibs =
            new java.util.HashMap<String, HashSet<String>>();

    public BaseDexClassLoader(String dexPath, File optimizedDirectory, String librarySearchPath, ClassLoader parent) {
        super(parent);
        mDexPath = dexPath;
        if (librarySearchPath != null) addNativePaths(librarySearchPath.split(File.pathSeparator));
    }

    private synchronized void addNativePaths(String[] paths) {
        for (String p : paths) {
            if (p != null && !p.isEmpty() && !mNativeDirs.contains(p)) mNativeDirs.add(p);
        }
    }

    /** framework-internal (hidden in AOSP): adds native library directories or zip paths to the search. */
    public void addNativePath(Collection<String> libPaths) {
        if (libPaths.isEmpty()) return;
        addNativePaths(libPaths.toArray(new String[0]));
    }

    public String findLibrary(String name) {
        String file = System.mapLibraryName(name);
        List<String> dirs;
        synchronized (this) {
            dirs = new ArrayList<String>(mNativeDirs);
        }
        for (String dir : dirs) {
            int bang = dir.indexOf("!/");
            if (bang < 0) {
                File f = new File(dir, file);
                if (f.isFile()) return f.getPath();
            } else if (zipLibs(dir.substring(0, bang), dir.substring(bang + 2)).contains(file)) {
                return dir + "/" + file;
            }
        }
        return null;
    }

    private synchronized HashSet<String> zipLibs(String zipPath, String dir) {
        String key = zipPath + "!/" + dir;
        HashSet<String> names = mZipLibs.get(key);
        if (names != null) return names;
        names = new HashSet<String>();
        String prefix = dir.endsWith("/") ? dir : dir + "/";
        ZipFile zip = null;
        try {
            zip = new ZipFile(zipPath);
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
                String n = e.nextElement().getName();
                if (n.startsWith(prefix) && n.indexOf('/', prefix.length()) < 0) {
                    names.add(n.substring(prefix.length()));
                }
            }
        } catch (java.io.IOException e) {
            // unreadable: no libraries there
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (java.io.IOException e) {
                    // ignored
                }
            }
        }
        mZipLibs.put(key, names);
        return names;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getName()).append("[DexPathList[[");
        if (mDexPath != null) {
            String[] parts = mDexPath.split(File.pathSeparator);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append("zip file \"").append(parts[i]).append('"');
            }
        }
        sb.append("],nativeLibraryDirectories=[");
        synchronized (this) {
            for (int i = 0; i < mNativeDirs.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(mNativeDirs.get(i));
            }
        }
        return sb.append("]]]").toString();
    }
}
