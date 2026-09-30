package java.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;

public class Properties extends Hashtable<Object, Object> {
    protected Properties defaults;

    public Properties() {
        this(null);
    }

    public Properties(Properties defaults) {
        this.defaults = defaults;
    }

    public synchronized Object setProperty(String key, String value) {
        return put(key, value);
    }

    public String getProperty(String key) {
        Object oval = get(key);
        String sval = (oval instanceof String) ? (String) oval : null;
        return ((sval == null) && (defaults != null)) ? defaults.getProperty(key) : sval;
    }

    public String getProperty(String key, String defaultValue) {
        String val = getProperty(key);
        return (val == null) ? defaultValue : val;
    }

    public Enumeration<?> propertyNames() {
        return Collections.enumeration(stringPropertyNames());
    }

    public Set<String> stringPropertyNames() {
        TreeSet<String> set = new TreeSet<String>();
        if (defaults != null) {
            set.addAll(defaults.stringPropertyNames());
        }
        for (Object k : keySet()) {
            if (k instanceof String && get(k) instanceof String) {
                set.add((String) k);
            }
        }
        return set;
    }

    public synchronized void load(InputStream inStream) throws IOException {
        load(new InputStreamReader(inStream, "ISO-8859-1"));
    }

    public synchronized void load(Reader reader) throws IOException {
        BufferedReader br = new BufferedReader(reader);
        String line;
        StringBuilder pending = null;
        while ((line = br.readLine()) != null) {
            String l = line;
            if (pending != null) {
                int s = 0;
                while (s < l.length() && Character.isWhitespace(l.charAt(s))) {
                    s++;
                }
                l = pending.append(l.substring(s)).toString();
                pending = null;
            }
            String t = l.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("!")) {
                continue;
            }
            int bs = 0;
            for (int i = l.length() - 1; i >= 0 && l.charAt(i) == '\\'; i--) {
                bs++;
            }
            if (bs % 2 == 1) {
                pending = new StringBuilder(l.substring(0, l.length() - 1));
                continue;
            }
            int i = 0;
            while (i < l.length() && Character.isWhitespace(l.charAt(i))) {
                i++;
            }
            StringBuilder key = new StringBuilder();
            while (i < l.length()) {
                char c = l.charAt(i);
                if (c == '\\' && i + 1 < l.length()) {
                    key.append(unescape(l.charAt(++i)));
                    i++;
                    continue;
                }
                if (c == '=' || c == ':' || Character.isWhitespace(c)) {
                    break;
                }
                key.append(c);
                i++;
            }
            while (i < l.length() && Character.isWhitespace(l.charAt(i))) {
                i++;
            }
            if (i < l.length() && (l.charAt(i) == '=' || l.charAt(i) == ':')) {
                i++;
            }
            while (i < l.length() && Character.isWhitespace(l.charAt(i))) {
                i++;
            }
            StringBuilder value = new StringBuilder();
            while (i < l.length()) {
                char c = l.charAt(i);
                if (c == '\\' && i + 1 < l.length()) {
                    char n = l.charAt(++i);
                    if (n == 'u' && i + 4 < l.length()) {
                        value.append((char) Integer.parseInt(l.substring(i + 1, i + 5), 16));
                        i += 5;
                        continue;
                    }
                    value.append(unescape(n));
                    i++;
                    continue;
                }
                value.append(c);
                i++;
            }
            put(key.toString(), value.toString());
        }
    }

    private static char unescape(char c) {
        switch (c) {
            case 't': return '\t';
            case 'n': return '\n';
            case 'r': return '\r';
            case 'f': return '\f';
            default: return c;
        }
    }

    public void store(OutputStream out, String comments) throws IOException {
        store(new OutputStreamWriter(out, "ISO-8859-1"), comments);
    }

    public void store(Writer writer, String comments) throws IOException {
        if (comments != null) {
            writer.write("#" + comments + "\n");
        }
        writer.write("#" + new Date().toString() + "\n");
        synchronized (this) {
            for (Map.Entry<Object, Object> e : entrySet()) {
                writer.write(escape(String.valueOf(e.getKey()), true) + "=" + escape(String.valueOf(e.getValue()), false) + "\n");
            }
        }
        writer.flush();
    }

    private static String escape(String s, boolean key) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\t': sb.append("\\t"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '=': case ':': case '#': case '!': sb.append('\\').append(c); break;
                case ' ':
                    if (key || i == 0) {
                        sb.append('\\');
                    }
                    sb.append(c);
                    break;
                default:
                    if (c < 0x20 || c > 0x7e) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    public void list(PrintStream out) {
        out.println("-- listing properties --");
        for (String k : stringPropertyNames()) {
            out.println(k + "=" + getProperty(k));
        }
    }

    public void list(PrintWriter out) {
        out.println("-- listing properties --");
        for (String k : stringPropertyNames()) {
            out.println(k + "=" + getProperty(k));
        }
    }
}
