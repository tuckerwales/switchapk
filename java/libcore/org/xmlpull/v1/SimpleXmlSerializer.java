package org.xmlpull.v1;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;

/** Small XmlSerializer implementation (used by SharedPreferences and apps). */
public class SimpleXmlSerializer implements XmlSerializer {
    private Writer out;
    private final ArrayList<String> stack = new ArrayList<>();
    private boolean pendingStart;
    private boolean indent;
    private boolean lastWasText;
    private final HashMap<String, String> prefixes = new HashMap<>();
    private final ArrayList<String[]> pendingNs = new ArrayList<>();

    public void setFeature(String name, boolean state) {
        if ("http://xmlpull.org/v1/doc/features.html#indent-output".equals(name)) indent = state;
    }

    public boolean getFeature(String name) {
        return "http://xmlpull.org/v1/doc/features.html#indent-output".equals(name) && indent;
    }

    public void setProperty(String name, Object value) {}
    public Object getProperty(String name) { return null; }

    public void setOutput(OutputStream os, String encoding) throws IOException {
        out = new OutputStreamWriter(os, encoding == null ? "UTF-8" : encoding);
    }

    public void setOutput(Writer writer) { out = writer; }

    public void startDocument(String encoding, Boolean standalone) throws IOException {
        out.write("<?xml version='1.0' encoding='" + (encoding == null ? "utf-8" : encoding) + "'");
        if (standalone != null) out.write(" standalone='" + (standalone ? "yes" : "no") + "'");
        out.write(" ?>");
        if (indent) out.write('\n');
    }

    public void endDocument() throws IOException {
        while (!stack.isEmpty()) endTag(null, stack.get(stack.size() - 1));
        flush();
    }

    public void setPrefix(String prefix, String namespace) {
        prefixes.put(namespace, prefix);
        pendingNs.add(new String[] {prefix, namespace});
    }

    public String getPrefix(String namespace, boolean generatePrefix) { return prefixes.get(namespace); }
    public int getDepth() { return stack.size(); }
    public String getNamespace() { return null; }
    public String getName() { return stack.isEmpty() ? null : stack.get(stack.size() - 1); }

    private void closeStart() throws IOException {
        if (pendingStart) {
            out.write('>');
            pendingStart = false;
        }
    }

    private String qname(String namespace, String name) {
        if (namespace == null || namespace.isEmpty()) return name;
        String p = prefixes.get(namespace);
        return p == null || p.isEmpty() ? name : p + ":" + name;
    }

    public XmlSerializer startTag(String namespace, String name) throws IOException {
        closeStart();
        if (indent) {
            if (!stack.isEmpty() || !lastWasText) out.write('\n');
            for (int i = 0; i < stack.size(); i++) out.write("  ");
        }
        String q = qname(namespace, name);
        out.write('<');
        out.write(q);
        for (String[] ns : pendingNs) {
            out.write(ns[0] == null || ns[0].isEmpty() ? " xmlns" : " xmlns:" + ns[0]);
            out.write("=\"");
            escape(ns[1], true);
            out.write('"');
        }
        pendingNs.clear();
        stack.add(q);
        pendingStart = true;
        lastWasText = false;
        return this;
    }

    public XmlSerializer attribute(String namespace, String name, String value) throws IOException {
        out.write(' ');
        out.write(qname(namespace, name));
        out.write("=\"");
        escape(value, true);
        out.write('"');
        return this;
    }

    public XmlSerializer endTag(String namespace, String name) throws IOException {
        String q = stack.remove(stack.size() - 1);
        if (pendingStart) {
            out.write(" />");
            pendingStart = false;
        } else {
            if (indent && !lastWasText) {
                out.write('\n');
                for (int i = 0; i < stack.size(); i++) out.write("  ");
            }
            out.write("</");
            out.write(q);
            out.write('>');
        }
        lastWasText = false;
        return this;
    }

    public XmlSerializer text(String text) throws IOException {
        closeStart();
        escape(text, false);
        lastWasText = true;
        return this;
    }

    public XmlSerializer text(char[] buf, int start, int len) throws IOException { return text(new String(buf, start, len)); }

    public void cdsect(String text) throws IOException {
        closeStart();
        out.write("<![CDATA[" + text + "]]>");
    }

    public void entityRef(String text) throws IOException {
        closeStart();
        out.write("&" + text + ";");
    }

    public void processingInstruction(String text) throws IOException {
        closeStart();
        out.write("<?" + text + "?>");
    }

    public void comment(String text) throws IOException {
        closeStart();
        out.write("<!--" + text + "-->");
    }

    public void docdecl(String text) throws IOException { out.write("<!DOCTYPE" + text + ">"); }

    public void ignorableWhitespace(String text) throws IOException {
        closeStart();
        out.write(text);
    }

    public void flush() throws IOException {
        closeStart();
        out.flush();
    }

    private void escape(String s, boolean attr) throws IOException {
        if (s == null) return;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<': out.write("&lt;"); break;
                case '>': out.write("&gt;"); break;
                case '&': out.write("&amp;"); break;
                case '"': out.write(attr ? "&quot;" : "\""); break;
                case '\n': out.write(attr ? "&#10;" : "\n"); break;
                case '\r': out.write("&#13;"); break;
                case '\t': out.write(attr ? "&#9;" : "\t"); break;
                default:
                    if (c < 0x20) out.write("&#" + (int) c + ";");
                    else out.write(c);
            }
        }
    }
}
