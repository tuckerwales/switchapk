package org.apache.harmony.xml.transform;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Writes a DOM tree as XML text, as the identity transformer does. Output follows the reference
 * serializer: attributes in map order with namespace declarations first, missing namespace
 * declarations added, empty elements as {@code <e/>}, and with indent="yes" each element-only child on
 * its own line (elements with text content are left as they are). Whitespace-only text between
 * elements is dropped when indenting.
 */
final class DomSerializer {
    private static final String XMLNS_NS = "http://www.w3.org/2000/xmlns/";
    private static final String NL = "\n";

    private final Writer out;
    private final String encoding;
    private final CharsetEncoder encoder;
    private final boolean unicode;
    boolean indent;
    int indentAmount;
    boolean omitDeclaration;
    String standalone;
    String version = "1.0";
    String doctypeSystem;
    String doctypePublic;
    String method = "xml";

    /** In-scope namespace bindings: prefix ("" for the default namespace), uri pairs. */
    private final List<String[]> bindings = new ArrayList<String[]>();
    private int generatedPrefixes;

    DomSerializer(Writer out, String encoding) {
        this.out = out;
        this.encoding = encoding;
        String upper = encoding.toUpperCase(java.util.Locale.ROOT);
        unicode = upper.startsWith("UTF-") || upper.startsWith("UTF8") || upper.startsWith("UTF16");
        CharsetEncoder enc = null;
        if (!unicode) {
            try {
                enc = Charset.forName(encoding).newEncoder();
            } catch (RuntimeException e) {
                enc = null;
            }
        }
        encoder = enc;
    }

    private boolean canEncode(char c) {
        if (c < 0x80 || unicode) {
            return true;
        }
        if (encoder == null) {
            return false;
        }
        if ("ISO-8859-1".equalsIgnoreCase(encoding)) {
            return c <= 0xFF;
        }
        return encoder.canEncode(c);
    }

    void serialize(Node node) throws IOException {
        if ("text".equals(method)) {
            String text = node.getNodeType() == Node.DOCUMENT_NODE ? textOf(node) : node.getTextContent();
            if (text != null) {
                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    out.write(canEncode(c) ? c : '?');
                }
            }
            out.flush();
            return;
        }
        if (!omitDeclaration) {
            out.write("<?xml version=\"" + version + "\" encoding=\"" + encoding + "\"");
            if (standalone != null) {
                out.write(" standalone=\"" + standalone + "\"");
            }
            out.write("?>");
            // Like Xalan, a line break follows the declaration only when standalone or a doctype is given.
            if (indent && (standalone != null || doctypeSystem != null)) {
                out.write(NL);
            }
        }
        if (doctypeSystem != null) {
            Element root = node.getNodeType() == Node.DOCUMENT_NODE
                    ? ((org.w3c.dom.Document) node).getDocumentElement()
                    : (node.getNodeType() == Node.ELEMENT_NODE ? (Element) node : null);
            if (root != null) {
                out.write("<!DOCTYPE " + root.getNodeName());
                if (doctypePublic != null) {
                    out.write(" PUBLIC \"" + doctypePublic + "\" \"" + doctypeSystem + "\"");
                } else {
                    out.write(" SYSTEM \"" + doctypeSystem + "\"");
                }
                out.write(">");
                out.write(NL);
            }
        }
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            for (Node c = node.getFirstChild(); c != null; c = c.getNextSibling()) {
                writeNode(c, 0, false);
            }
        } else {
            writeNode(node, 0, false);
        }
        if (indent) {
            out.write(NL);
        }
        out.flush();
    }

    private static String textOf(Node doc) {
        StringBuilder sb = new StringBuilder();
        for (Node c = doc.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNodeType() == Node.ELEMENT_NODE) {
                sb.append(c.getTextContent());
            }
        }
        return sb.toString();
    }

    private void newline(int depth) throws IOException {
        out.write(NL);
        for (int i = depth * indentAmount; i > 0; i--) {
            out.write(' ');
        }
    }

    private static boolean isWhitespaceText(Node n) {
        if (n.getNodeType() != Node.TEXT_NODE) {
            return false;
        }
        String s = n.getNodeValue();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return false;
            }
        }
        return true;
    }

    private void writeNode(Node node, int depth, boolean indented) throws IOException {
        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE:
                if (indented) {
                    newline(depth);
                }
                writeElement((Element) node, depth);
                break;
            case Node.TEXT_NODE:
                writeText(node.getNodeValue(), false);
                break;
            case Node.CDATA_SECTION_NODE:
                writeCData(node.getNodeValue());
                break;
            case Node.COMMENT_NODE:
                if (indented) {
                    newline(depth);
                }
                out.write("<!--");
                out.write(node.getNodeValue());
                out.write("-->");
                break;
            case Node.PROCESSING_INSTRUCTION_NODE: {
                if (indented) {
                    newline(depth);
                }
                out.write("<?");
                out.write(node.getNodeName());
                String data = node.getNodeValue();
                if (data != null && !data.isEmpty()) {
                    out.write(' ');
                    out.write(data);
                }
                out.write("?>");
                break;
            }
            case Node.ENTITY_REFERENCE_NODE:
                out.write('&');
                out.write(node.getNodeName());
                out.write(';');
                break;
            case Node.DOCUMENT_FRAGMENT_NODE:
                for (Node c = node.getFirstChild(); c != null; c = c.getNextSibling()) {
                    writeNode(c, depth, indented && !isWhitespaceText(c));
                }
                break;
            case Node.ATTRIBUTE_NODE:
                writeText(node.getNodeValue(), false);
                break;
            default:
                // Document types, entities and notations are not part of the output tree.
                break;
        }
    }

    private String lookup(String prefix) {
        for (int i = bindings.size() - 1; i >= 0; i--) {
            if (bindings.get(i)[0].equals(prefix)) {
                return bindings.get(i)[1];
            }
        }
        return prefix.equals("xml") ? "http://www.w3.org/XML/1998/namespace" : (prefix.isEmpty() ? "" : null);
    }

    private void writeElement(Element e, int depth) throws IOException {
        int mark = bindings.size();
        String name = e.getNodeName();
        out.write('<');
        out.write(name);
        NamedNodeMap attrs = e.getAttributes();
        int n = attrs.getLength();
        // Namespace declarations present as attributes come first.
        for (int i = 0; i < n; i++) {
            Attr a = (Attr) attrs.item(i);
            String an = a.getNodeName();
            if (an.equals("xmlns") || an.startsWith("xmlns:")) {
                writeAttribute(an, a.getValue());
                bindings.add(new String[] {an.length() == 5 ? "" : an.substring(6), a.getValue()});
            }
        }
        String[] elementDecl = null;
        boolean namespaceAware = e.getLocalName() != null;
        if (namespaceAware) {
            String prefix = e.getPrefix() == null ? "" : e.getPrefix();
            String ns = e.getNamespaceURI() == null ? "" : e.getNamespaceURI();
            String bound = lookup(prefix);
            if (!ns.equals(bound) && !(bound == null && ns.isEmpty())) {
                // Written after the attributes, as the reference serializer does.
                elementDecl = new String[] {prefix, ns};
                bindings.add(elementDecl);
            }
        }
        for (int i = 0; i < n; i++) {
            Attr a = (Attr) attrs.item(i);
            String an = a.getNodeName();
            if (an.equals("xmlns") || an.startsWith("xmlns:")) {
                continue;
            }
            String ans = a.getNamespaceURI();
            if (a.getLocalName() != null && ans != null && !XMLNS_NS.equals(ans)) {
                String p = a.getPrefix();
                if (p == null || p.isEmpty()) {
                    p = findPrefix(ans);
                    if (p == null) {
                        p = "ns" + (generatedPrefixes++);
                        declare(p, ans);
                    }
                    an = p + ":" + a.getLocalName();
                } else if (!ans.equals(lookup(p))) {
                    declare(p, ans);
                }
            }
            writeAttribute(an, a.getValue());
        }
        if (elementDecl != null) {
            writeAttribute(elementDecl[0].isEmpty() ? "xmlns" : "xmlns:" + elementDecl[0], elementDecl[1]);
        }
        Node first = e.getFirstChild();
        if (first == null) {
            out.write("/>");
            bindings.subList(mark, bindings.size()).clear();
            return;
        }
        out.write('>');
        boolean childIndent = false;
        if (indent) {
            boolean text = false;
            boolean other = false;
            for (Node c = first; c != null; c = c.getNextSibling()) {
                short t = c.getNodeType();
                if (t == Node.TEXT_NODE || t == Node.CDATA_SECTION_NODE || t == Node.ENTITY_REFERENCE_NODE) {
                    if (!isWhitespaceText(c)) {
                        text = true;
                    }
                } else {
                    other = true;
                }
            }
            childIndent = other && !text;
        }
        for (Node c = first; c != null; c = c.getNextSibling()) {
            if (childIndent && isWhitespaceText(c)) {
                continue;
            }
            writeNode(c, depth + 1, childIndent);
        }
        if (childIndent) {
            newline(depth);
        }
        out.write("</");
        out.write(name);
        out.write('>');
        bindings.subList(mark, bindings.size()).clear();
    }

    private String findPrefix(String ns) {
        for (int i = bindings.size() - 1; i >= 0; i--) {
            String[] b = bindings.get(i);
            if (b[1].equals(ns) && !b[0].isEmpty() && ns.equals(lookup(b[0]))) {
                return b[0];
            }
        }
        return null;
    }

    private void declare(String prefix, String ns) throws IOException {
        writeAttribute(prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix, ns);
        bindings.add(new String[] {prefix, ns});
    }

    private void writeAttribute(String name, String value) throws IOException {
        out.write(' ');
        out.write(name);
        out.write("=\"");
        writeText(value, true);
        out.write('"');
    }

    private void writeText(String s, boolean attribute) throws IOException {
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':
                    out.write("&amp;");
                    break;
                case '<':
                    out.write("&lt;");
                    break;
                case '>':
                    out.write("&gt;");
                    break;
                case '"':
                    if (attribute) {
                        out.write("&quot;");
                    } else {
                        out.write(c);
                    }
                    break;
                case '\n':
                    if (attribute) {
                        out.write("&#10;");
                    } else {
                        out.write(c);
                    }
                    break;
                case '\t':
                    if (attribute) {
                        out.write("&#9;");
                    } else {
                        out.write(c);
                    }
                    break;
                case '\r':
                    out.write("&#13;");
                    break;
                default:
                    if (Character.isHighSurrogate(c) && i + 1 < n && Character.isLowSurrogate(s.charAt(i + 1))) {
                        if (unicode) {
                            out.write(c);
                            out.write(s.charAt(i + 1));
                        } else {
                            out.write("&#" + Character.toCodePoint(c, s.charAt(i + 1)) + ";");
                        }
                        i++;
                    } else if (canEncode(c)) {
                        out.write(c);
                    } else {
                        out.write("&#" + (int) c + ";");
                    }
                    break;
            }
        }
    }

    private void writeCData(String s) throws IOException {
        out.write("<![CDATA[");
        int start = 0;
        int split;
        while ((split = s.indexOf("]]>", start)) >= 0) {
            out.write(s, start, split + 2 - start);
            out.write("]]><![CDATA[");
            start = split + 2;
        }
        out.write(s, start, s.length() - start);
        out.write("]]>");
    }
}
