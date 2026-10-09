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

    public Properties(int initialCapacity) {
        this();
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
        store0(new OutputStreamWriter(out, "ISO-8859-1"), comments, true);
    }

    @Deprecated
    public void save(OutputStream out, String comments) {
        try {
            store(out, comments);
        } catch (IOException e) {
            // save() swallows I/O errors, as in the JDK
        }
    }

    public void store(Writer writer, String comments) throws IOException {
        store0(writer, comments, false);
    }

    private void store0(Writer writer, String comments, boolean escUnicode) throws IOException {
        if (comments != null) {
            writer.write("#" + comments + "\n");
        }
        writer.write("#" + new Date().toString() + "\n");
        synchronized (this) {
            for (Map.Entry<Object, Object> e : entrySet()) {
                writer.write(escape(String.valueOf(e.getKey()), true, escUnicode) + "="
                        + escape(String.valueOf(e.getValue()), false, escUnicode) + "\n");
            }
        }
        writer.flush();
    }

    private static String escape(String s, boolean key, boolean escUnicode) {
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
                    if (c < 0x20 || (c > 0x7e && escUnicode)) {
                        sb.append(String.format("\\u%04X", (int) c));
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

    public void storeToXML(OutputStream os, String comment) throws IOException {
        storeToXML(os, comment, java.nio.charset.StandardCharsets.UTF_8);
    }

    public void storeToXML(OutputStream os, String comment, String encoding) throws IOException {
        Objects.requireNonNull(os);
        Objects.requireNonNull(encoding);
        java.nio.charset.Charset cs;
        try {
            cs = java.nio.charset.Charset.forName(encoding);
        } catch (RuntimeException e) {
            throw new java.io.UnsupportedEncodingException(encoding);
        }
        storeToXML(os, comment, cs);
    }

    public void storeToXML(OutputStream os, String comment, java.nio.charset.Charset charset) throws IOException {
        Objects.requireNonNull(os, "OutputStream");
        Objects.requireNonNull(charset, "Charset");
        Writer w = new java.io.BufferedWriter(new OutputStreamWriter(os, charset));
        w.write("<?xml version=\"1.0\" encoding=\"" + charset.name() + "\" standalone=\"no\"?>\n");
        w.write("<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\n");
        w.write("<properties>\n");
        if (comment != null) {
            w.write("<comment>" + xmlEscape(comment) + "</comment>\n");
        }
        synchronized (this) {
            for (Map.Entry<Object, Object> e : entrySet()) {
                if (e.getKey() instanceof String && e.getValue() instanceof String) {
                    w.write("<entry key=\"" + xmlEscape((String) e.getKey()) + "\">" + xmlEscape((String) e.getValue())
                            + "</entry>\n");
                }
            }
        }
        w.write("</properties>\n");
        w.flush();
    }

    private static String xmlEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '&': sb.append("&amp;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    /* A small reader for the properties DTD only: <entry key="...">text</entry> elements, comments skipped. */
    public synchronized void loadFromXML(InputStream in) throws IOException, InvalidPropertiesFormatException {
        Objects.requireNonNull(in);
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) > 0) {
            bo.write(b, 0, n);
        }
        in.close();
        String doc = new String(bo.toByteArray(), xmlCharset(bo.toByteArray()));
        if (doc.indexOf("<properties") < 0) {
            throw new InvalidPropertiesFormatException("not a properties document");
        }
        int i = 0;
        while ((i = doc.indexOf("<entry", i)) >= 0) {
            int tagEnd = doc.indexOf('>', i);
            if (tagEnd < 0) {
                throw new InvalidPropertiesFormatException("unterminated entry");
            }
            String tag = doc.substring(i, tagEnd);
            int k = tag.indexOf("key");
            int q = k < 0 ? -1 : tag.indexOf('=', k);
            if (q < 0) {
                throw new InvalidPropertiesFormatException("entry without key");
            }
            q++;
            while (q < tag.length() && tag.charAt(q) <= ' ') {
                q++;
            }
            char quote = tag.charAt(q);
            int keyEnd = tag.indexOf(quote, q + 1);
            String key = xmlUnescape(tag.substring(q + 1, keyEnd));
            String value;
            if (tag.endsWith("/")) {
                value = "";
                i = tagEnd + 1;
            } else {
                int close = doc.indexOf("</entry>", tagEnd);
                if (close < 0) {
                    throw new InvalidPropertiesFormatException("unterminated entry");
                }
                value = xmlUnescape(doc.substring(tagEnd + 1, close));
                i = close + 8;
            }
            put(key, value);
        }
    }

    private static java.nio.charset.Charset xmlCharset(byte[] doc) {
        String head = new String(doc, 0, Math.min(doc.length, 200), java.nio.charset.StandardCharsets.ISO_8859_1);
        int e = head.indexOf("encoding=");
        if (e >= 0 && e + 10 < head.length()) {
            char quote = head.charAt(e + 9);
            int end = head.indexOf(quote, e + 10);
            if (end > 0) {
                try {
                    return java.nio.charset.Charset.forName(head.substring(e + 10, end));
                } catch (RuntimeException ex) {
                    // fall back to UTF-8
                }
            }
        }
        return java.nio.charset.StandardCharsets.UTF_8;
    }

    private static String xmlUnescape(String s) {
        if (s.startsWith("<![CDATA[") && s.endsWith("]]>")) {
            return s.substring(9, s.length() - 3);
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int semi;
            if (c == '&' && (semi = s.indexOf(';', i)) > i) {
                String ent = s.substring(i + 1, semi);
                switch (ent) {
                    case "lt": sb.append('<'); break;
                    case "gt": sb.append('>'); break;
                    case "amp": sb.append('&'); break;
                    case "quot": sb.append('"'); break;
                    case "apos": sb.append('\''); break;
                    default:
                        if (ent.startsWith("#x") || ent.startsWith("#X")) {
                            sb.appendCodePoint(Integer.parseInt(ent.substring(2), 16));
                        } else if (ent.startsWith("#")) {
                            sb.appendCodePoint(Integer.parseInt(ent.substring(1)));
                        } else {
                            sb.append('&').append(ent).append(';');
                        }
                }
                i = semi;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
