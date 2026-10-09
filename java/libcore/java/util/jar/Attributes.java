package java.util.jar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Attributes implements Map<Object, Object>, Cloneable {
    protected Map<Object, Object> map;

    public Attributes() {
        this(11);
    }

    public Attributes(int size) {
        map = new LinkedHashMap<>(size);
    }

    public Attributes(Attributes attr) {
        map = new LinkedHashMap<>(attr);
    }

    public Object get(Object name) {
        return map.get(name);
    }

    public String getValue(String name) {
        return (String) get(new Name(name));
    }

    public String getValue(Name name) {
        return (String) get(name);
    }

    public Object put(Object name, Object value) {
        return map.put((Name) name, (String) value);
    }

    public String putValue(String name, String value) {
        return (String) put(new Name(name), value);
    }

    public Object remove(Object name) {
        return map.remove(name);
    }

    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    public boolean containsKey(Object name) {
        return map.containsKey(name);
    }

    public void putAll(Map<?, ?> attr) {
        if (!Attributes.class.isInstance(attr)) {
            throw new ClassCastException();
        }
        for (Map.Entry<?, ?> me : (attr).entrySet()) {
            put(me.getKey(), me.getValue());
        }
    }

    public void clear() {
        map.clear();
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public Set<Object> keySet() {
        return map.keySet();
    }

    public Collection<Object> values() {
        return map.values();
    }

    public Set<Map.Entry<Object, Object>> entrySet() {
        return map.entrySet();
    }

    public boolean equals(Object o) {
        return this == o || map.equals(o);
    }

    public int hashCode() {
        return map.hashCode();
    }

    public Object clone() {
        return new Attributes(this);
    }

    /* Writes the section's attributes, each "Name: value" folded at 72 bytes (framework-internal). */
    void write(OutputStream out) throws IOException {
        for (Map.Entry<Object, Object> e : entrySet()) {
            Manifest.println72(out, e.getKey().toString() + ": " + e.getValue());
        }
        Manifest.println(out);
    }

    /* The main section: the version attribute first; nothing else is written without one, as in the JDK. */
    void writeMain(OutputStream out) throws IOException {
        String vername = Name.MANIFEST_VERSION.toString();
        String version = getValue(vername);
        if (version == null) {
            vername = Name.SIGNATURE_VERSION.toString();
            version = getValue(vername);
        }
        if (version != null) {
            Manifest.println72(out, vername + ": " + version);
            for (Map.Entry<Object, Object> e : entrySet()) {
                String name = e.getKey().toString();
                if (!name.equalsIgnoreCase(vername)) {
                    Manifest.println72(out, name + ": " + e.getValue());
                }
            }
        }
        Manifest.println(out);
    }

    /*
     * Reads "Name: value" lines up to a blank line or the end. Continuation lines start with a space and are
     * joined as bytes before UTF-8 decoding. Returns false at the end of the input with nothing read.
     */
    boolean read(Manifest.LineReader in) throws IOException {
        String pendingName = null;
        ByteArrayOutputStream value = null;
        boolean any = false;
        byte[] line;
        while ((line = in.readLine()) != null) {
            any = true;
            if (line.length == 0) {
                break;
            }
            if (line[0] == ' ') {
                if (pendingName == null) {
                    throw new IOException("misplaced continuation line");
                }
                value.write(line, 1, line.length - 1);
                continue;
            }
            if (pendingName != null) {
                putValue(pendingName, new String(value.toByteArray(), StandardCharsets.UTF_8));
            }
            int i = 0;
            while (i < line.length && line[i] != ':') {
                i++;
            }
            if (i >= line.length - 1 || line[i + 1] != ' ') {
                throw new IOException("invalid header field");
            }
            pendingName = new String(line, 0, i, StandardCharsets.UTF_8);
            value = new ByteArrayOutputStream();
            value.write(line, i + 2, line.length - i - 2);
        }
        if (pendingName != null) {
            putValue(pendingName, new String(value.toByteArray(), StandardCharsets.UTF_8));
        }
        return any;
    }

    public static class Name {
        private final String name;
        private final int hashCode;

        public static final Name MANIFEST_VERSION = new Name("Manifest-Version");
        public static final Name SIGNATURE_VERSION = new Name("Signature-Version");
        public static final Name CONTENT_TYPE = new Name("Content-Type");
        public static final Name CLASS_PATH = new Name("Class-Path");
        public static final Name MAIN_CLASS = new Name("Main-Class");
        public static final Name SEALED = new Name("Sealed");
        public static final Name EXTENSION_LIST = new Name("Extension-List");
        public static final Name EXTENSION_NAME = new Name("Extension-Name");
        @Deprecated
        public static final Name EXTENSION_INSTALLATION = new Name("Extension-Installation");
        public static final Name IMPLEMENTATION_TITLE = new Name("Implementation-Title");
        public static final Name IMPLEMENTATION_VERSION = new Name("Implementation-Version");
        public static final Name IMPLEMENTATION_VENDOR = new Name("Implementation-Vendor");
        @Deprecated
        public static final Name IMPLEMENTATION_VENDOR_ID = new Name("Implementation-Vendor-Id");
        @Deprecated
        public static final Name IMPLEMENTATION_URL = new Name("Implementation-URL");
        public static final Name SPECIFICATION_TITLE = new Name("Specification-Title");
        public static final Name SPECIFICATION_VERSION = new Name("Specification-Version");
        public static final Name SPECIFICATION_VENDOR = new Name("Specification-Vendor");
        public static final Name MULTI_RELEASE = new Name("Multi-Release");

        public Name(String name) {
            if (name == null) {
                throw new NullPointerException("name");
            }
            if (!isValid(name)) {
                throw new IllegalArgumentException(name);
            }
            this.name = name;
            this.hashCode = name.toLowerCase(Locale.ROOT).hashCode();
        }

        private static boolean isValid(String name) {
            int len = name.length();
            if (len > 70 || len == 0) {
                return false;
            }
            for (int i = 0; i < len; i++) {
                char c = name.charAt(i);
                if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_'
                        || c == '-')) {
                    return false;
                }
            }
            return true;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            return o instanceof Name && name.equalsIgnoreCase(((Name) o).name);
        }

        public int hashCode() {
            return hashCode;
        }

        public String toString() {
            return name;
        }
    }
}
