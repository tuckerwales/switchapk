package java.nio.file;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

public final class Paths {
    private Paths() {
    }

    public static Path get(String first, String... more) {
        StringBuilder sb = new StringBuilder(first);
        for (String m : more) {
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '/') {
                sb.append('/');
            }
            sb.append(m);
        }
        return new SimplePath(sb.toString());
    }

    public static Path get(java.net.URI uri) {
        return new SimplePath(uri.getPath());
    }

    static final class SimplePath implements Path {
        final String p;

        SimplePath(String p) {
            this.p = new File(p).getPath();
        }

        public Path getFileName() {
            return p.isEmpty() ? null : new SimplePath(new File(p).getName());
        }

        public Path getParent() {
            String parent = new File(p).getParent();
            return parent == null ? null : new SimplePath(parent);
        }

        public Path getRoot() {
            return isAbsolute() ? new SimplePath("/") : null;
        }

        public boolean isAbsolute() {
            return p.startsWith("/");
        }

        public Path resolve(String other) {
            if (other.startsWith("/")) {
                return new SimplePath(other);
            }
            return new SimplePath(p.isEmpty() ? other : p + "/" + other);
        }

        public Path resolve(Path other) {
            return resolve(other.toString());
        }

        public Path toAbsolutePath() {
            return new SimplePath(new File(p).getAbsolutePath());
        }

        public Path normalize() {
            try {
                return isAbsolute() ? new SimplePath(new File(p).getCanonicalPath()) : this;
            } catch (java.io.IOException e) {
                return this;
            }
        }

        public File toFile() {
            return new File(p);
        }

        private String[] parts() {
            ArrayList<String> l = new ArrayList<String>();
            for (String s : p.split("/")) {
                if (!s.isEmpty()) {
                    l.add(s);
                }
            }
            return l.toArray(new String[l.size()]);
        }

        public int getNameCount() {
            return parts().length;
        }

        public Path getName(int index) {
            return new SimplePath(parts()[index]);
        }

        public boolean startsWith(String other) {
            return p.startsWith(other);
        }

        public boolean endsWith(String other) {
            return p.endsWith(other);
        }

        public Path relativize(Path other) {
            String o = other.toString();
            if (o.startsWith(p + "/")) {
                return new SimplePath(o.substring(p.length() + 1));
            }
            return other;
        }

        public Iterator<Path> iterator() {
            ArrayList<Path> l = new ArrayList<Path>();
            for (String s : parts()) {
                l.add(new SimplePath(s));
            }
            return l.iterator();
        }

        public int compareTo(Path other) {
            return p.compareTo(other.toString());
        }

        public boolean equals(Object o) {
            return o instanceof Path && p.equals(o.toString());
        }

        public int hashCode() {
            return p.hashCode();
        }

        public String toString() {
            return p;
        }
    }
}
