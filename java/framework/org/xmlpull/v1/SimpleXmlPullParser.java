package org.xmlpull.v1;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * A compact, namespace-aware XML pull parser for textual XML (the role
 * KXmlParser plays on Android). Handles elements, attributes, text, CDATA,
 * comments, processing instructions, DOCTYPE (skipped) and the predefined
 * and numeric entities.
 */
public class SimpleXmlPullParser implements XmlPullParser {
    private Reader reader;
    private char[] buf = new char[8192];
    private int pos, limit;
    private int line = 1, column = 0;

    private boolean processNs;
    private boolean relaxed;
    private int eventType = START_DOCUMENT;
    private String name, prefix, namespace;
    private String text;
    private boolean isEmptyTag, pendingEnd;
    private String encoding;

    private final ArrayList<String[]> elementStack = new ArrayList<>(); // {qname, prefix, name, ns}
    private final ArrayList<Integer> nsCounts = new ArrayList<>();
    private final ArrayList<String> nsPrefixes = new ArrayList<>();
    private final ArrayList<String> nsUris = new ArrayList<>();

    private int attrCount;
    private String[] attrNames = new String[16];
    private String[] attrPrefixes = new String[16];
    private String[] attrNs = new String[16];
    private String[] attrValues = new String[16];

    private HashMap<String, String> entities;

    public void setFeature(String name, boolean state) throws XmlPullParserException {
        if (FEATURE_PROCESS_NAMESPACES.equals(name)) processNs = state;
        else if ("http://xmlpull.org/v1/doc/features.html#relaxed".equals(name)) relaxed = state;
    }

    public boolean getFeature(String name) {
        if (FEATURE_PROCESS_NAMESPACES.equals(name)) return processNs;
        if ("http://xmlpull.org/v1/doc/features.html#relaxed".equals(name)) return relaxed;
        return false;
    }

    public void setProperty(String name, Object value) throws XmlPullParserException {}
    public Object getProperty(String name) { return null; }

    public void setInput(Reader in) throws XmlPullParserException {
        reader = in;
        pos = limit = 0;
        line = 1;
        column = 0;
        eventType = START_DOCUMENT;
        elementStack.clear();
        nsCounts.clear();
        nsPrefixes.clear();
        nsUris.clear();
        attrCount = 0;
        name = prefix = namespace = text = null;
        pendingEnd = false;
    }

    public void setInput(InputStream is, String enc) throws XmlPullParserException {
        if (is == null) throw new IllegalArgumentException("input stream must not be null");
        try {
            if (enc == null) {
                // sniff a BOM / xml declaration
                java.io.BufferedInputStream bis = new java.io.BufferedInputStream(is, 1024);
                bis.mark(1024);
                byte[] head = new byte[256];
                int n = bis.read(head);
                bis.reset();
                enc = "UTF-8";
                if (n >= 2 && (head[0] & 0xff) == 0xfe && (head[1] & 0xff) == 0xff) enc = "UTF-16BE";
                else if (n >= 2 && (head[0] & 0xff) == 0xff && (head[1] & 0xff) == 0xfe) enc = "UTF-16LE";
                else if (n > 0) {
                    String h = new String(head, 0, n, "ISO-8859-1");
                    int e = h.indexOf("encoding=");
                    if (h.startsWith("<?xml") && e > 0 && e + 10 < h.length()) {
                        char q = h.charAt(e + 9);
                        int end = h.indexOf(q, e + 10);
                        if (end > 0) enc = h.substring(e + 10, end);
                    }
                }
                is = bis;
            }
            encoding = enc;
            setInput(new InputStreamReader(is, enc));
        } catch (IOException e) {
            throw new XmlPullParserException(e.toString(), this, e);
        }
    }

    public String getInputEncoding() { return encoding; }

    public void defineEntityReplacementText(String entityName, String replacementText) {
        if (entities == null) entities = new HashMap<>();
        entities.put(entityName, replacementText);
    }

    public int getNamespaceCount(int depth) {
        if (depth == 0) return 0;
        if (depth > nsCounts.size()) depth = nsCounts.size();
        return depth == 0 ? 0 : nsCounts.get(depth - 1);
    }

    public String getNamespacePrefix(int pos) { return nsPrefixes.get(pos); }
    public String getNamespaceUri(int pos) { return nsUris.get(pos); }

    public String getNamespace(String prefix) {
        if ("xml".equals(prefix)) return "http://www.w3.org/XML/1998/namespace";
        if ("xmlns".equals(prefix)) return "http://www.w3.org/2000/xmlns/";
        for (int i = nsPrefixes.size() - 1; i >= 0; i--) {
            String p = nsPrefixes.get(i);
            if (prefix == null ? p == null : prefix.equals(p)) return nsUris.get(i);
        }
        return null;
    }

    public int getDepth() {
        int d = elementStack.size();
        if (eventType == END_TAG) d++;
        return d;
    }

    public String getPositionDescription() {
        return TYPES[eventType] + (name != null ? " <" + name + ">" : "") + " @" + line + ":" + column;
    }

    public int getLineNumber() { return line; }
    public int getColumnNumber() { return column; }

    public boolean isWhitespace() throws XmlPullParserException {
        if (eventType != TEXT && eventType != IGNORABLE_WHITESPACE && eventType != CDSECT)
            throw new XmlPullParserException("Wrong event type", this, null);
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) > ' ') return false;
        return true;
    }

    public String getText() {
        if (eventType < TEXT && eventType != START_DOCUMENT && eventType != END_DOCUMENT) return null;
        return text;
    }

    public char[] getTextCharacters(int[] holder) {
        String t = getText();
        if (t == null) {
            holder[0] = -1;
            holder[1] = -1;
            return null;
        }
        holder[0] = 0;
        holder[1] = t.length();
        return t.toCharArray();
    }

    public String getNamespace() { return eventType == START_TAG || eventType == END_TAG ? namespace : null; }
    public String getName() { return eventType == START_TAG || eventType == END_TAG || eventType == ENTITY_REF ? name : null; }
    public String getPrefix() { return eventType == START_TAG || eventType == END_TAG ? prefix : null; }

    public boolean isEmptyElementTag() throws XmlPullParserException {
        if (eventType != START_TAG) throw new XmlPullParserException("Wrong event type", this, null);
        return isEmptyTag;
    }

    public int getAttributeCount() { return eventType == START_TAG ? attrCount : -1; }
    public String getAttributeNamespace(int i) { check(i); return attrNs[i] == null ? "" : attrNs[i]; }
    public String getAttributeName(int i) { check(i); return attrNames[i]; }
    public String getAttributePrefix(int i) { check(i); return attrPrefixes[i]; }
    public String getAttributeType(int i) { return "CDATA"; }
    public boolean isAttributeDefault(int i) { return false; }
    public String getAttributeValue(int i) { check(i); return attrValues[i]; }

    private void check(int i) {
        if (eventType != START_TAG || i < 0 || i >= attrCount) throw new IndexOutOfBoundsException("attribute " + i);
    }

    public String getAttributeValue(String ns, String n) {
        if (eventType != START_TAG) return null;
        for (int i = 0; i < attrCount; i++) {
            if (!attrNames[i].equals(n)) continue;
            if (ns == null) return attrValues[i];
            String a = attrNs[i] == null ? "" : attrNs[i];
            if (ns.equals(a)) return attrValues[i];
        }
        return null;
    }

    public int getEventType() { return eventType; }

    public int next() throws XmlPullParserException, IOException {
        StringBuilder acc = null;
        int accType = -1;
        while (true) {
            int t = nextToken();
            if (t == TEXT || t == CDSECT || t == ENTITY_REF || t == IGNORABLE_WHITESPACE) {
                if (acc == null) acc = new StringBuilder();
                acc.append(text);
                accType = TEXT;
                if (peekIsText()) continue;
                eventType = TEXT;
                text = acc.toString();
                return TEXT;
            }
            if (t == COMMENT || t == PROCESSING_INSTRUCTION || t == DOCDECL) {
                if (acc != null && peekIsText()) continue;
                if (acc != null) {
                    eventType = TEXT;
                    text = acc.toString();
                    return TEXT;
                }
                continue;
            }
            if (acc != null && accType == TEXT) {
                // cannot happen: text is returned before non-text tokens are consumed
            }
            return t;
        }
    }

    private boolean peekIsText() throws IOException {
        if (pendingEnd) return false;
        if (!fill(1)) return false;
        char c = buf[pos];
        if (c != '<') return true;
        if (!fill(4)) return false;
        return (buf[pos + 1] == '!' && (buf[pos + 2] == '-' || buf[pos + 2] == '[')) || buf[pos + 1] == '?';
    }

    public int nextToken() throws XmlPullParserException, IOException {
        if (eventType == END_TAG) {
            // pop namespaces of the finished element
            int d = elementStack.size();
            int keep = d == 0 ? 0 : nsCounts.get(d - 1);
            while (nsPrefixes.size() > keep) {
                nsPrefixes.remove(nsPrefixes.size() - 1);
                nsUris.remove(nsUris.size() - 1);
            }
            while (nsCounts.size() > d) nsCounts.remove(nsCounts.size() - 1);
        }
        if (pendingEnd) {
            pendingEnd = false;
            String[] top = elementStack.remove(elementStack.size() - 1);
            prefix = top[1];
            name = top[2];
            namespace = top[3];
            eventType = END_TAG;
            return eventType;
        }
        attrCount = 0;
        text = null;
        if (!fill(1)) {
            if (!elementStack.isEmpty() && !relaxed) throw new XmlPullParserException("Unexpected EOF", this, null);
            eventType = END_DOCUMENT;
            return eventType;
        }
        char c = buf[pos];
        if (c != '<') {
            eventType = readText();
            return eventType;
        }
        fill(2);
        char c1 = pos + 1 < limit ? buf[pos + 1] : 0;
        if (c1 == '/') {
            advance(2);
            String q = readName();
            skipWs();
            expect('>');
            if (elementStack.isEmpty()) throw new XmlPullParserException("Unexpected end tag </" + q + ">", this, null);
            String[] top = elementStack.remove(elementStack.size() - 1);
            if (!top[0].equals(q) && !relaxed) throw new XmlPullParserException("Expected </" + top[0] + "> but found </" + q + ">", this, null);
            prefix = top[1];
            name = top[2];
            namespace = top[3];
            eventType = END_TAG;
            return eventType;
        }
        if (c1 == '?') {
            advance(2);
            text = readUntil("?>");
            if (text.startsWith("xml ") || text.equals("xml")) return nextToken();
            eventType = PROCESSING_INSTRUCTION;
            return eventType;
        }
        if (c1 == '!') {
            fill(9);
            if (startsWith("<!--")) {
                advance(4);
                text = readUntil("-->");
                eventType = COMMENT;
                return eventType;
            }
            if (startsWith("<![CDATA[")) {
                advance(9);
                text = readUntil("]]>");
                eventType = CDSECT;
                return eventType;
            }
            advance(2);
            // DOCTYPE: skip to matching '>' honoring an internal subset in [...]
            StringBuilder sb = new StringBuilder();
            int depth = 0;
            while (fill(1)) {
                char ch = read();
                if (ch == '[') depth++;
                else if (ch == ']') depth--;
                else if (ch == '>' && depth <= 0) break;
                sb.append(ch);
            }
            text = sb.toString();
            eventType = DOCDECL;
            return eventType;
        }
        advance(1);
        parseStartTag();
        return eventType;
    }

    private void parseStartTag() throws XmlPullParserException, IOException {
        String q = readName();
        attrCount = 0;
        int nsStart = nsPrefixes.size();
        while (true) {
            skipWs();
            if (!fill(1)) throw new XmlPullParserException("Unexpected EOF in tag", this, null);
            char c = buf[pos];
            if (c == '/') {
                advance(1);
                expect('>');
                isEmptyTag = true;
                break;
            }
            if (c == '>') {
                advance(1);
                isEmptyTag = false;
                break;
            }
            String an = readName();
            skipWs();
            String av;
            if (fill(1) && buf[pos] == '=') {
                advance(1);
                skipWs();
                char quote = read();
                if (quote != '"' && quote != '\'') {
                    if (!relaxed) throw new XmlPullParserException("Expected quote", this, null);
                    StringBuilder sb = new StringBuilder().append(quote);
                    while (fill(1) && buf[pos] > ' ' && buf[pos] != '>' && buf[pos] != '/') sb.append(read());
                    av = sb.toString();
                } else {
                    av = readAttrValue(quote);
                }
            } else {
                if (!relaxed) throw new XmlPullParserException("Attribute without value: " + an, this, null);
                av = an;
            }
            if (processNs && (an.equals("xmlns") || an.startsWith("xmlns:"))) {
                nsPrefixes.add(an.length() == 5 ? null : an.substring(6));
                nsUris.add(av);
                continue;
            }
            if (attrCount == attrNames.length) {
                int n = attrCount * 2;
                attrNames = java.util.Arrays.copyOf(attrNames, n);
                attrPrefixes = java.util.Arrays.copyOf(attrPrefixes, n);
                attrNs = java.util.Arrays.copyOf(attrNs, n);
                attrValues = java.util.Arrays.copyOf(attrValues, n);
            }
            attrNames[attrCount] = an;
            attrPrefixes[attrCount] = null;
            attrNs[attrCount] = null;
            attrValues[attrCount] = av;
            attrCount++;
        }
        nsCounts.add(nsPrefixes.size());
        if (processNs) {
            for (int i = 0; i < attrCount; i++) {
                int colon = attrNames[i].indexOf(':');
                if (colon > 0) {
                    attrPrefixes[i] = attrNames[i].substring(0, colon);
                    attrNames[i] = attrNames[i].substring(colon + 1);
                    attrNs[i] = getNamespace(attrPrefixes[i]);
                    if (attrNs[i] == null && !relaxed)
                        throw new XmlPullParserException("Undefined prefix: " + attrPrefixes[i], this, null);
                } else {
                    attrNs[i] = "";
                }
            }
            int colon = q.indexOf(':');
            if (colon > 0) {
                prefix = q.substring(0, colon);
                name = q.substring(colon + 1);
            } else {
                prefix = null;
                name = q;
            }
            namespace = getNamespace(prefix);
            if (namespace == null) namespace = "";
        } else {
            for (int i = 0; i < attrCount; i++) attrNs[i] = "";
            prefix = null;
            name = q;
            namespace = "";
        }
        elementStack.add(new String[] {q, prefix, name, namespace});
        eventType = START_TAG;
        if (isEmptyTag) pendingEnd = true;
    }

    private int readText() throws XmlPullParserException, IOException {
        StringBuilder sb = new StringBuilder();
        boolean ws = true;
        while (fill(1) && buf[pos] != '<') {
            char c = read();
            if (c == '&') {
                String r = readEntity();
                sb.append(r);
                ws = false;
            } else {
                if (c > ' ') ws = false;
                sb.append(c);
            }
        }
        text = sb.toString();
        return ws && elementStack.isEmpty() ? IGNORABLE_WHITESPACE : TEXT;
    }

    private String readAttrValue(char quote) throws XmlPullParserException, IOException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (!fill(1)) throw new XmlPullParserException("Unexpected EOF in attribute", this, null);
            char c = read();
            if (c == quote) break;
            if (c == '&') sb.append(readEntity());
            else if (c == '\n' || c == '\t' || c == '\r') sb.append(' ');
            else sb.append(c);
        }
        return sb.toString();
    }

    private String readEntity() throws XmlPullParserException, IOException {
        StringBuilder sb = new StringBuilder();
        while (fill(1) && buf[pos] != ';' && sb.length() < 32) {
            char c = buf[pos];
            if (c == '<' || c == '&' || c <= ' ') break;
            sb.append(read());
        }
        if (fill(1) && buf[pos] == ';') advance(1);
        else if (!relaxed) throw new XmlPullParserException("Unterminated entity &" + sb, this, null);
        String e = sb.toString();
        if (e.startsWith("#x") || e.startsWith("#X")) return new String(Character.toChars(Integer.parseInt(e.substring(2), 16)));
        if (e.startsWith("#")) return new String(Character.toChars(Integer.parseInt(e.substring(1))));
        switch (e) {
            case "lt": return "<";
            case "gt": return ">";
            case "amp": return "&";
            case "quot": return "\"";
            case "apos": return "'";
        }
        if (entities != null && entities.containsKey(e)) return entities.get(e);
        if (relaxed) return "&" + e + ";";
        throw new XmlPullParserException("Unknown entity &" + e + ";", this, null);
    }

    public void require(int type, String ns, String n) throws XmlPullParserException, IOException {
        if (type != eventType || (ns != null && !ns.equals(getNamespace())) || (n != null && !n.equals(getName())))
            throw new XmlPullParserException("expected " + TYPES[type] + " " + n + " but was " + getPositionDescription(), this, null);
    }

    public String nextText() throws XmlPullParserException, IOException {
        if (eventType != START_TAG) throw new XmlPullParserException("precondition: START_TAG", this, null);
        int t = next();
        String result;
        if (t == TEXT) {
            result = text;
            t = next();
        } else {
            result = "";
        }
        if (t != END_TAG) throw new XmlPullParserException("END_TAG expected", this, null);
        return result;
    }

    public int nextTag() throws XmlPullParserException, IOException {
        int t = next();
        if (t == TEXT && isWhitespace()) t = next();
        if (t != START_TAG && t != END_TAG) throw new XmlPullParserException("expected start or end tag", this, null);
        return t;
    }

    // ---- low level input ----------------------------------------------------------------

    private boolean fill(int n) throws IOException {
        if (limit - pos >= n) return true;
        if (reader == null) return false;
        if (pos > 0) {
            System.arraycopy(buf, pos, buf, 0, limit - pos);
            limit -= pos;
            pos = 0;
        }
        if (n > buf.length) buf = java.util.Arrays.copyOf(buf, n * 2);
        while (limit - pos < n) {
            int r = reader.read(buf, limit, buf.length - limit);
            if (r <= 0) return limit - pos >= n;
            limit += r;
        }
        return true;
    }

    private char read() throws IOException {
        char c = buf[pos++];
        if (c == '\n') {
            line++;
            column = 0;
        } else {
            column++;
        }
        return c;
    }

    private void advance(int n) throws IOException {
        for (int i = 0; i < n; i++) read();
    }

    private boolean startsWith(String s) throws IOException {
        if (!fill(s.length())) return false;
        for (int i = 0; i < s.length(); i++) if (buf[pos + i] != s.charAt(i)) return false;
        return true;
    }

    private void expect(char c) throws XmlPullParserException, IOException {
        if (!fill(1) || buf[pos] != c) {
            if (relaxed) return;
            throw new XmlPullParserException("Expected '" + c + "'", this, null);
        }
        read();
    }

    private void skipWs() throws IOException {
        while (fill(1) && buf[pos] <= ' ') read();
    }

    private String readName() throws XmlPullParserException, IOException {
        StringBuilder sb = new StringBuilder();
        while (fill(1)) {
            char c = buf[pos];
            if (c <= ' ' || c == '>' || c == '/' || c == '=' || c == '<') break;
            sb.append(read());
        }
        if (sb.length() == 0) throw new XmlPullParserException("Expected a name", this, null);
        return sb.toString();
    }

    private String readUntil(String end) throws XmlPullParserException, IOException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (startsWith(end)) {
                advance(end.length());
                return sb.toString();
            }
            if (!fill(1)) throw new XmlPullParserException("Unexpected EOF, expected " + end, this, null);
            sb.append(read());
        }
    }
}
