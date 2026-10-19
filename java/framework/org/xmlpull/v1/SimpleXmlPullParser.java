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
    /** General entities declared in the internal DTD subset; their replacement text is parsed as markup. */
    private HashMap<String, String> dtdEntities;
    private ArrayList<String> dtdEntityOrder;
    private int expandedChars;
    private boolean lastWasCR;
    private boolean atStart;
    private String declVersion, declEncoding;
    private Boolean declStandalone;

    /** Property names for the XML declaration (the first two as in KXmlParser). */
    public static final String PROPERTY_XMLDECL_VERSION = "http://xmlpull.org/v1/doc/properties.html#xmldecl-version";
    public static final String PROPERTY_XMLDECL_STANDALONE =
            "http://xmlpull.org/v1/doc/properties.html#xmldecl-standalone";
    /** Framework-internal: the encoding named in the XML declaration. */
    public static final String PROPERTY_XMLDECL_ENCODING =
            "http://xmlpull.org/v1/doc/properties.html#xmldecl-encoding";
    /** Framework-internal: String[] {name, value, ...} of the entities declared in the internal subset. */
    public static final String PROPERTY_DTD_ENTITIES = "http://xmlpull.org/v1/doc/properties.html#dtd-entities";

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

    public Object getProperty(String name) {
        if (PROPERTY_XMLDECL_VERSION.equals(name)) return declVersion;
        if (PROPERTY_XMLDECL_STANDALONE.equals(name)) return declStandalone;
        if (PROPERTY_XMLDECL_ENCODING.equals(name)) return declEncoding;
        if (PROPERTY_DTD_ENTITIES.equals(name)) {
            if (dtdEntityOrder == null) return new String[0];
            String[] out = new String[dtdEntityOrder.size() * 2];
            for (int i = 0; i < dtdEntityOrder.size(); i++) {
                out[2 * i] = dtdEntityOrder.get(i);
                out[2 * i + 1] = dtdEntities.get(dtdEntityOrder.get(i));
            }
            return out;
        }
        return null;
    }

    public void setInput(Reader in) throws XmlPullParserException {
        reader = in;
        dtdEntities = null;
        dtdEntityOrder = null;
        expandedChars = 0;
        lastWasCR = false;
        atStart = true;
        declVersion = declEncoding = null;
        declStandalone = null;
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
                byte[] head = new byte[512];
                int n = 0;
                while (n < head.length) {
                    int r = bis.read(head, n, head.length - n);
                    if (r <= 0) break;
                    n += r;
                }
                bis.reset();
                enc = sniffEncoding(head, n);
                is = bis;
            }
            encoding = enc;
            setInput(new InputStreamReader(is, enc));
        } catch (IOException e) {
            throw new XmlPullParserException(e.toString(), this, e);
        }
    }

    /** Picks the encoding from a byte order mark or the encoding declared in the XML prolog. */
    private static String sniffEncoding(byte[] head, int n) throws IOException {
        int b0 = n > 0 ? head[0] & 0xff : -1;
        int b1 = n > 1 ? head[1] & 0xff : -1;
        if (b0 == 0xfe && b1 == 0xff) return "UTF-16BE";
        if (b0 == 0xff && b1 == 0xfe) return "UTF-16LE";
        String h;
        if (b0 == 0 && b1 == '<') {
            h = new String(head, 0, n & ~1, "UTF-16BE");
            if (declaredEncoding(h) == null) return "UTF-16BE";
        } else if (b0 == '<' && b1 == 0) {
            h = new String(head, 0, n & ~1, "UTF-16LE");
            if (declaredEncoding(h) == null) return "UTF-16LE";
        } else {
            h = new String(head, 0, Math.max(n, 0), "ISO-8859-1");
        }
        String declared = declaredEncoding(h);
        if (declared == null) return "UTF-8";
        String upper = declared.toUpperCase(java.util.Locale.ROOT);
        if (upper.equals("UTF-16") || upper.equals("UTF16")) {
            // No byte order mark: the byte pattern tells which order was used.
            return b0 == 0 ? "UTF-16BE" : (b1 == 0 ? "UTF-16LE" : "UTF-8");
        }
        return declared;
    }

    /** Returns the encoding pseudo-attribute of a leading XML declaration, or null. */
    private static String declaredEncoding(String h) {
        if (h.startsWith("﻿")) h = h.substring(1);
        if (!h.startsWith("<?xml")) return null;
        int end = h.indexOf("?>");
        if (end < 0) return null;
        return pseudoAttribute(h.substring(5, end), "encoding");
    }

    /** Reads name="value" or name='value' from the body of an XML declaration. */
    private static String pseudoAttribute(String decl, String name) {
        int i = 0;
        while ((i = decl.indexOf(name, i)) >= 0) {
            int j = i + name.length();
            if (i > 0 && decl.charAt(i - 1) > ' ') {
                i = j;
                continue;
            }
            while (j < decl.length() && decl.charAt(j) <= ' ') j++;
            if (j < decl.length() && decl.charAt(j) == '=') {
                j++;
                while (j < decl.length() && decl.charAt(j) <= ' ') j++;
                if (j < decl.length() && (decl.charAt(j) == '"' || decl.charAt(j) == '\'')) {
                    int close = decl.indexOf(decl.charAt(j), j + 1);
                    if (close > 0) return decl.substring(j + 1, close);
                }
            }
            i = j;
        }
        return null;
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
        if (atStart) {
            atStart = false;
            if (fill(1) && buf[pos] == '﻿') pos++;
        }
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
            if (text.startsWith("xml") && (text.length() == 3 || text.charAt(3) <= ' ')) {
                String decl = text.substring(3);
                declVersion = pseudoAttribute(decl, "version");
                declEncoding = pseudoAttribute(decl, "encoding");
                String sa = pseudoAttribute(decl, "standalone");
                declStandalone = sa == null ? null : Boolean.valueOf("yes".equals(sa));
                return nextToken();
            }
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
            if (!startsWith("<!DOCTYPE") && !relaxed) {
                throw new XmlPullParserException("Unexpected markup <!", this, null);
            }
            advance(2);
            // DOCTYPE: skip to the closing '>' honoring quotes, comments and an internal subset in [...]
            StringBuilder sb = new StringBuilder();
            int depth = 0;
            char quote = 0;
            while (true) {
                if (!fill(1)) throw new XmlPullParserException("Unexpected EOF in DOCTYPE", this, null);
                if (quote == 0 && depth > 0 && startsWith("<!--")) {
                    advance(4);
                    sb.append("<!--").append(readUntil("-->")).append("-->");
                    continue;
                }
                char ch = read();
                if (quote != 0) {
                    if (ch == quote) quote = 0;
                } else if (ch == '"' || ch == '\'') {
                    quote = ch;
                } else if (ch == '[') {
                    depth++;
                } else if (ch == ']') {
                    depth--;
                } else if (ch == '>' && depth <= 0) {
                    break;
                }
                sb.append(ch);
            }
            text = sb.toString();
            int open = text.indexOf('[');
            int close = text.lastIndexOf(']');
            if (open >= 0 && close > open) parseInternalSubset(text.substring(open + 1, close));
            eventType = DOCDECL;
            return eventType;
        }
        advance(1);
        parseStartTag();
        return eventType;
    }

    /**
     * Records the general entities declared in an internal DTD subset. Character references in the
     * literal are expanded now; entity references in it are expanded where the entity is used.
     * External entities are never fetched and expand to nothing.
     */
    private void parseInternalSubset(String s) {
        int i = 0;
        int n = s.length();
        while (i < n) {
            if (s.startsWith("<!--", i)) {
                int end = s.indexOf("-->", i + 4);
                i = end < 0 ? n : end + 3;
                continue;
            }
            if (s.startsWith("<?", i)) {
                int end = s.indexOf("?>", i + 2);
                i = end < 0 ? n : end + 2;
                continue;
            }
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                int end = s.indexOf(c, i + 1);
                i = end < 0 ? n : end + 1;
                continue;
            }
            if (!s.startsWith("<!ENTITY", i)) {
                i++;
                continue;
            }
            i += 8;
            while (i < n && s.charAt(i) <= ' ') i++;
            boolean parameter = i < n && s.charAt(i) == '%';
            if (parameter) {
                i++;
                while (i < n && s.charAt(i) <= ' ') i++;
            }
            int nameStart = i;
            while (i < n && s.charAt(i) > ' ' && s.charAt(i) != '"' && s.charAt(i) != '\'' && s.charAt(i) != '>') i++;
            String entityName = s.substring(nameStart, i);
            while (i < n && s.charAt(i) <= ' ') i++;
            String value = "";
            if (i < n && (s.charAt(i) == '"' || s.charAt(i) == '\'')) {
                int end = s.indexOf(s.charAt(i), i + 1);
                if (end < 0) end = n;
                value = expandCharRefs(s.substring(i + 1, end));
                i = end + 1;
            }
            int close = s.indexOf('>', Math.min(i, n));
            i = close < 0 ? n : close + 1;
            if (parameter || entityName.isEmpty()) continue;
            if (dtdEntities == null) {
                dtdEntities = new HashMap<>();
                dtdEntityOrder = new ArrayList<>();
            }
            if (!dtdEntities.containsKey(entityName)) {
                dtdEntities.put(entityName, value);
                dtdEntityOrder.add(entityName);
            }
        }
    }

    private static String expandCharRefs(String v) {
        if (v.indexOf("&#") < 0) return v;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < v.length()) {
            int amp = v.indexOf("&#", i);
            int semi = amp < 0 ? -1 : v.indexOf(';', amp);
            if (amp < 0 || semi < 0) {
                sb.append(v, i, v.length());
                break;
            }
            sb.append(v, i, amp);
            String ref = v.substring(amp + 2, semi);
            try {
                int cp = ref.startsWith("x") ? Integer.parseInt(ref.substring(1), 16) : Integer.parseInt(ref);
                sb.appendCodePoint(cp);
            } catch (RuntimeException e) {
                sb.append(v, amp, semi + 1);
            }
            i = semi + 1;
        }
        return sb.toString();
    }

    /** Inserts text at the read position so that it is parsed next (entity replacement text). */
    private void inject(String s) throws XmlPullParserException {
        expandedChars += s.length();
        if (expandedChars > 1 << 22) throw new XmlPullParserException("Entity expansion limit exceeded", this, null);
        int len = s.length();
        if (limit + len > buf.length) {
            char[] nb = new char[Math.max(buf.length * 2, limit - pos + len + 1024)];
            System.arraycopy(buf, pos, nb, 0, limit - pos);
            limit -= pos;
            pos = 0;
            buf = nb;
        }
        System.arraycopy(buf, pos, buf, pos + len, limit - pos);
        s.getChars(0, len, buf, pos);
        limit += len;
    }

    /** Expands entity and character references for an attribute value (no markup allowed there). */
    private String expandForAttribute(String v, int depth) throws XmlPullParserException {
        if (v.indexOf('&') < 0) return v;
        if (depth > 32) throw new XmlPullParserException("Recursive entity reference", this, null);
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < v.length()) {
            char c = v.charAt(i);
            int semi = c == '&' ? v.indexOf(';', i) : -1;
            if (semi < 0) {
                sb.append(c);
                i++;
                continue;
            }
            String e = v.substring(i + 1, semi);
            i = semi + 1;
            if (e.startsWith("#")) {
                sb.append(expandCharRefs("&" + e + ";"));
            } else if (dtdEntities != null && dtdEntities.containsKey(e)) {
                sb.append(expandForAttribute(dtdEntities.get(e), depth + 1));
            } else {
                String p = predefined(e);
                if (p == null && entities != null) p = entities.get(e);
                if (p == null) throw new XmlPullParserException("Unknown entity &" + e + ";", this, null);
                sb.append(p);
            }
        }
        return sb.toString();
    }

    private static String predefined(String e) {
        switch (e) {
            case "lt": return "<";
            case "gt": return ">";
            case "amp": return "&";
            case "quot": return "\"";
            case "apos": return "'";
            default: return null;
        }
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
                String r = readEntity(false);
                sb.append(r);
                if (!r.isEmpty()) ws = false;
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
            if (c == '&') sb.append(readEntity(true));
            else if (c == '\n' || c == '\t' || c == '\r') sb.append(' ');
            else sb.append(c);
        }
        return sb.toString();
    }

    private String readEntity(boolean inAttribute) throws XmlPullParserException, IOException {
        StringBuilder sb = new StringBuilder();
        while (fill(1) && buf[pos] != ';' && sb.length() < 256) {
            char c = buf[pos];
            if (c == '<' || c == '&' || c <= ' ') break;
            sb.append(read());
        }
        if (fill(1) && buf[pos] == ';') advance(1);
        else if (!relaxed) throw new XmlPullParserException("Unterminated entity &" + sb, this, null);
        String e = sb.toString();
        try {
            if (e.startsWith("#x")) return new String(Character.toChars(Integer.parseInt(e.substring(2), 16)));
            if (e.startsWith("#")) return new String(Character.toChars(Integer.parseInt(e.substring(1))));
        } catch (IllegalArgumentException ex) {
            if (relaxed) return "&" + e + ";";
            throw new XmlPullParserException("Invalid character reference &" + e + ";", this, null);
        }
        String p = predefined(e);
        if (p != null) return p;
        if (entities != null && entities.containsKey(e)) return entities.get(e);
        if (dtdEntities != null && dtdEntities.containsKey(e)) {
            String value = dtdEntities.get(e);
            if (inAttribute) return expandForAttribute(value, 0);
            if (value.indexOf('<') < 0 && value.indexOf('&') < 0) return value;
            inject(value);
            return "";
        }
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
            // Line-end normalization (XML 1.0 section 2.11): CR LF and lone CR become LF.
            int w = limit;
            for (int i = limit; i < limit + r; i++) {
                char c = buf[i];
                if (c == '\n' && lastWasCR) {
                    lastWasCR = false;
                    continue;
                }
                lastWasCR = c == '\r';
                buf[w++] = lastWasCR ? '\n' : c;
            }
            limit = w;
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
