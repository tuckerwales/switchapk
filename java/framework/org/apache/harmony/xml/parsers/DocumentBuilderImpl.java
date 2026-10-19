package org.apache.harmony.xml.parsers;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.net.URLDecoder;
import javax.xml.parsers.DocumentBuilder;
import org.apache.harmony.xml.dom.DOMImplementationImpl;
import org.apache.harmony.xml.dom.DocumentImpl;
import org.apache.harmony.xml.dom.DocumentTypeImpl;
import org.apache.harmony.xml.dom.EntityImpl;
import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xmlpull.v1.SimpleXmlPullParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Builds a DOM tree with the framework's pull parser, as Android's DocumentBuilderImpl does with
 * KXmlParser. Entities declared in the internal DTD subset are always expanded (no
 * EntityReference nodes); external entities and DTDs are never fetched.
 */
class DocumentBuilderImpl extends DocumentBuilder {
    private static final String XMLNS_NS = "http://www.w3.org/2000/xmlns/";

    private boolean coalescing;
    private EntityResolver entityResolver;
    private ErrorHandler errorHandler;
    private boolean ignoreComments;
    private boolean ignoreElementContentWhitespace;
    private boolean namespaceAware;

    public void reset() {
        coalescing = false;
        entityResolver = null;
        errorHandler = null;
        ignoreComments = false;
        ignoreElementContentWhitespace = false;
        namespaceAware = false;
    }

    public DOMImplementation getDOMImplementation() {
        return DOMImplementationImpl.getInstance();
    }

    public boolean isNamespaceAware() {
        return namespaceAware;
    }

    public boolean isValidating() {
        return false;
    }

    public Document newDocument() {
        return new DocumentImpl(DOMImplementationImpl.getInstance(), null, null, null, null);
    }

    public Document parse(InputSource source) throws SAXException, IOException {
        if (source == null) {
            throw new IllegalArgumentException("source == null");
        }
        String publicId = source.getPublicId();
        String systemId = source.getSystemId();
        SimpleXmlPullParser parser = new SimpleXmlPullParser();
        InputStream opened = null;
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, namespaceAware);
            Reader chars = source.getCharacterStream();
            InputStream bytes = source.getByteStream();
            if (chars != null) {
                parser.setInput(chars);
            } else if (bytes != null) {
                parser.setInput(bytes, source.getEncoding());
            } else if (systemId != null) {
                opened = openSystemId(systemId);
                parser.setInput(opened, source.getEncoding());
            } else {
                throw new SAXParseException("InputSource requires a stream, reader or system id", publicId, systemId,
                        -1, -1);
            }
            DocumentImpl document = new DocumentImpl(DOMImplementationImpl.getInstance(), null, null, null,
                    parser.getInputEncoding() != null ? parser.getInputEncoding() : "UTF-16");
            document.setDocumentURI(systemId);
            build(parser, document);
            return document;
        } catch (XmlPullParserException e) {
            throw fatal(new SAXParseException(e.getMessage(), publicId, systemId, e.getLineNumber(),
                    e.getColumnNumber(), e));
        } catch (DOMException e) {
            throw fatal(new SAXParseException(e.getMessage(), publicId, systemId, parser.getLineNumber(),
                    parser.getColumnNumber(), e));
        } catch (BuildError e) {
            throw fatal(new SAXParseException(e.getMessage(), publicId, systemId, parser.getLineNumber(),
                    parser.getColumnNumber()));
        } catch (RuntimeException e) {
            throw fatal(new SAXParseException(String.valueOf(e), publicId, systemId, parser.getLineNumber(),
                    parser.getColumnNumber(), e));
        } finally {
            if (opened != null) {
                opened.close();
            }
        }
    }

    private SAXParseException fatal(SAXParseException e) throws SAXException {
        if (errorHandler != null) {
            errorHandler.fatalError(e);
        }
        return e;
    }

    static InputStream openSystemId(String systemId) throws IOException {
        if (systemId.startsWith("file:")) {
            String path = systemId.substring(5);
            while (path.startsWith("//")) {
                path = path.substring(1);
            }
            return new FileInputStream(URLDecoder.decode(path.replace("+", "%2B"), "UTF-8"));
        }
        if (systemId.indexOf(':') < 2) {
            // a plain path rather than a URI
            return new FileInputStream(systemId);
        }
        return new URL(systemId).openStream();
    }

    private static final class BuildError extends RuntimeException {
        BuildError(String message) {
            super(message);
        }
    }

    private void build(SimpleXmlPullParser parser, DocumentImpl document) throws XmlPullParserException,
            IOException {
        String version = (String) parser.getProperty(SimpleXmlPullParser.PROPERTY_XMLDECL_VERSION);
        if (version != null) {
            document.setXmlVersion("1.1".equals(version) ? "1.1" : "1.0");
        }
        document.setXmlEncoding((String) parser.getProperty(SimpleXmlPullParser.PROPERTY_XMLDECL_ENCODING));
        Node node = document;
        boolean seenRoot = false;
        int token;
        while ((token = parser.nextToken()) != XmlPullParser.END_DOCUMENT) {
            if (node == document && token != XmlPullParser.START_TAG && token != XmlPullParser.COMMENT
                    && token != XmlPullParser.PROCESSING_INSTRUCTION && token != XmlPullParser.DOCDECL) {
                String text = parser.getText();
                if (text != null && !isWhitespace(text)) {
                    throw new BuildError(seenRoot ? "Content is not allowed in trailing section."
                            : "Content is not allowed in prolog.");
                }
                continue;
            }
            switch (token) {
                case XmlPullParser.START_TAG: {
                    if (node == document && seenRoot) {
                        throw new BuildError("The markup in the document following the root element must be "
                                + "well-formed.");
                    }
                    if (node == document) {
                        // The declaration has been read by now.
                        if (document.getXmlEncoding() == null) {
                            document.setXmlEncoding(
                                    (String) parser.getProperty(SimpleXmlPullParser.PROPERTY_XMLDECL_ENCODING));
                        }
                        Object standalone = parser.getProperty(SimpleXmlPullParser.PROPERTY_XMLDECL_STANDALONE);
                        document.setXmlStandalone(Boolean.TRUE.equals(standalone));
                        String v = (String) parser.getProperty(SimpleXmlPullParser.PROPERTY_XMLDECL_VERSION);
                        if (v != null) {
                            document.setXmlVersion("1.1".equals(v) ? "1.1" : "1.0");
                        }
                    }
                    seenRoot = true;
                    Element element = startElement(parser, document);
                    node.appendChild(element);
                    node = element;
                    break;
                }
                case XmlPullParser.END_TAG:
                    node = node.getParentNode();
                    break;
                case XmlPullParser.TEXT:
                case XmlPullParser.IGNORABLE_WHITESPACE:
                case XmlPullParser.ENTITY_REF: {
                    String text = parser.getText();
                    if (text != null && !text.isEmpty()) {
                        appendText(document, node, text);
                    }
                    break;
                }
                case XmlPullParser.CDSECT:
                    if (coalescing) {
                        String text = parser.getText();
                        if (!text.isEmpty()) {
                            appendText(document, node, text);
                        }
                    } else {
                        node.appendChild(document.createCDATASection(parser.getText()));
                    }
                    break;
                case XmlPullParser.COMMENT:
                    if (!ignoreComments) {
                        node.appendChild(document.createComment(parser.getText()));
                    }
                    break;
                case XmlPullParser.PROCESSING_INSTRUCTION: {
                    String text = parser.getText();
                    int sp = 0;
                    while (sp < text.length() && !Character.isWhitespace(text.charAt(sp))) {
                        sp++;
                    }
                    int dataStart = sp;
                    while (dataStart < text.length() && Character.isWhitespace(text.charAt(dataStart))) {
                        dataStart++;
                    }
                    node.appendChild(document.createProcessingInstruction(text.substring(0, sp),
                            text.substring(dataStart)));
                    break;
                }
                case XmlPullParser.DOCDECL:
                    if (seenRoot || document.getDoctype() != null) {
                        throw new BuildError("DOCTYPE is not allowed here.");
                    }
                    document.appendChild(parseDoctype(parser, document, parser.getText()));
                    break;
                default:
                    break;
            }
        }
        if (!seenRoot) {
            throw new BuildError("Premature end of file.");
        }
    }

    private Element startElement(XmlPullParser parser, DocumentImpl document) throws XmlPullParserException {
        Element element;
        if (namespaceAware) {
            String ns = parser.getNamespace();
            String prefix = parser.getPrefix();
            String qName = prefix == null || prefix.isEmpty() ? parser.getName() : prefix + ":" + parser.getName();
            element = document.createElementNS(ns == null || ns.isEmpty() ? null : ns, qName);
            int depth = parser.getDepth();
            int nsStart = parser.getNamespaceCount(depth - 1);
            int nsEnd = parser.getNamespaceCount(depth);
            for (int i = nsStart; i < nsEnd; i++) {
                String p = parser.getNamespacePrefix(i);
                String attrName = p == null || p.isEmpty() ? "xmlns" : "xmlns:" + p;
                if (element.hasAttributeNS(XMLNS_NS, p == null || p.isEmpty() ? "xmlns" : p)) {
                    throw duplicateAttribute(attrName, qName);
                }
                element.setAttributeNS(XMLNS_NS, attrName, parser.getNamespaceUri(i));
            }
            for (int i = 0; i < parser.getAttributeCount(); i++) {
                String attrNs = parser.getAttributeNamespace(i);
                String attrPrefix = parser.getAttributePrefix(i);
                String attrLocal = parser.getAttributeName(i);
                String attrQName = attrPrefix == null || attrPrefix.isEmpty() ? attrLocal : attrPrefix + ":" + attrLocal;
                attrNs = attrNs == null || attrNs.isEmpty() ? null : attrNs;
                if (element.hasAttributeNS(attrNs, attrLocal)) {
                    throw duplicateAttribute(attrQName, qName);
                }
                element.setAttributeNS(attrNs, attrQName, parser.getAttributeValue(i));
            }
        } else {
            String name = parser.getName();
            element = document.createElement(name);
            for (int i = 0; i < parser.getAttributeCount(); i++) {
                String attrName = parser.getAttributeName(i);
                if (element.hasAttribute(attrName)) {
                    throw duplicateAttribute(attrName, name);
                }
                element.setAttribute(attrName, parser.getAttributeValue(i));
            }
        }
        return element;
    }

    private static BuildError duplicateAttribute(String attr, String element) {
        return new BuildError("Attribute \"" + attr + "\" was already specified for element \"" + element + "\".");
    }

    private static void appendText(DocumentImpl document, Node parent, String text) {
        Node last = parent.getLastChild();
        if (last != null && last.getNodeType() == Node.TEXT_NODE) {
            ((Text) last).appendData(text);
        } else {
            parent.appendChild(document.createTextNode(text));
        }
    }

    private static boolean isWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return false;
            }
        }
        return true;
    }

    /** Parses the text of a DOCDECL token ("DOCTYPE name PUBLIC "p" "s" [subset]"). */
    private static DocumentTypeImpl parseDoctype(SimpleXmlPullParser parser, DocumentImpl document, String text) {
        int i = text.startsWith("DOCTYPE") ? 7 : 0;
        int n = text.length();
        i = skipSpace(text, i);
        int start = i;
        while (i < n && text.charAt(i) > ' ' && text.charAt(i) != '[') {
            i++;
        }
        String name = text.substring(start, i);
        i = skipSpace(text, i);
        String publicId = null;
        String systemId = null;
        if (text.startsWith("PUBLIC", i)) {
            i = skipSpace(text, i + 6);
            int[] end = new int[1];
            publicId = quoted(text, i, end);
            i = skipSpace(text, end[0]);
            systemId = quoted(text, i, end);
            i = skipSpace(text, end[0]);
        } else if (text.startsWith("SYSTEM", i)) {
            i = skipSpace(text, i + 6);
            int[] end = new int[1];
            systemId = quoted(text, i, end);
            i = skipSpace(text, end[0]);
        }
        String subset = null;
        if (i < n && text.charAt(i) == '[') {
            int close = text.lastIndexOf(']');
            subset = text.substring(i + 1, close > i ? close : n);
        }
        DocumentTypeImpl doctype = new DocumentTypeImpl(document, name, publicId, systemId, subset);
        String[] entities = (String[]) parser.getProperty(SimpleXmlPullParser.PROPERTY_DTD_ENTITIES);
        if (entities != null) {
            for (int e = 0; e + 1 < entities.length; e += 2) {
                doctype.entities.add(new EntityImpl(document, entities[e], null, null, null, entities[e + 1]));
            }
        }
        return doctype;
    }

    private static int skipSpace(String s, int i) {
        while (i < s.length() && s.charAt(i) <= ' ') {
            i++;
        }
        return i;
    }

    private static String quoted(String s, int i, int[] end) {
        if (i >= s.length() || (s.charAt(i) != '"' && s.charAt(i) != '\'')) {
            end[0] = i;
            return null;
        }
        int close = s.indexOf(s.charAt(i), i + 1);
        if (close < 0) {
            end[0] = s.length();
            return s.substring(i + 1);
        }
        end[0] = close + 1;
        return s.substring(i + 1, close);
    }

    public void setEntityResolver(EntityResolver resolver) {
        entityResolver = resolver;
    }

    public void setErrorHandler(ErrorHandler handler) {
        errorHandler = handler;
    }

    void setIgnoreComments(boolean value) {
        ignoreComments = value;
    }

    void setCoalescing(boolean value) {
        coalescing = value;
    }

    void setIgnoreElementContentWhitespace(boolean value) {
        // Without a DTD no whitespace is element content whitespace, as on Android and the JDK.
        ignoreElementContentWhitespace = value;
    }

    void setNamespaceAware(boolean value) {
        namespaceAware = value;
    }
}
