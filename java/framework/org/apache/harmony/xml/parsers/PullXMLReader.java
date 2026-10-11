package org.apache.harmony.xml.parsers;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;
import org.xmlpull.v1.SimpleXmlPullParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * A SAX2 XMLReader over the framework's pull parser (Android uses expat, as ExpatReader). Features:
 * namespaces (default true) and namespace-prefixes; other standard features are accepted and
 * ignored. The lexical-handler property gets comments and CDATA bounds. No DTD processing: the
 * doctype is skipped and only the predefined and character entities are expanded.
 */
public class PullXMLReader implements XMLReader {
    private static final String FEATURE_PREFIX = "http://xml.org/sax/features/";
    private static final String NAMESPACES = FEATURE_PREFIX + "namespaces";
    private static final String NAMESPACE_PREFIXES = FEATURE_PREFIX + "namespace-prefixes";
    private static final String LEXICAL_HANDLER = "http://xml.org/sax/properties/lexical-handler";

    private ContentHandler contentHandler;
    private DTDHandler dtdHandler;
    private EntityResolver entityResolver;
    private ErrorHandler errorHandler;
    private LexicalHandler lexicalHandler;
    private boolean processNamespaces = true;
    private boolean processNamespacePrefixes;

    public boolean getFeature(String name) throws SAXNotRecognizedException {
        if (name == null) {
            throw new NullPointerException("name == null");
        }
        if (NAMESPACES.equals(name)) {
            return processNamespaces;
        }
        if (NAMESPACE_PREFIXES.equals(name)) {
            return processNamespacePrefixes;
        }
        if (name.startsWith(FEATURE_PREFIX)) {
            return false;
        }
        throw new SAXNotRecognizedException(name);
    }

    public void setFeature(String name, boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name == null) {
            throw new NullPointerException("name == null");
        }
        if (NAMESPACES.equals(name)) {
            processNamespaces = value;
            return;
        }
        if (NAMESPACE_PREFIXES.equals(name)) {
            processNamespacePrefixes = value;
            return;
        }
        if (name.startsWith(FEATURE_PREFIX)) {
            if (value && (name.endsWith("/validation") || name.endsWith("/external-general-entities")
                    || name.endsWith("/external-parameter-entities"))) {
                throw new SAXNotSupportedException(name);
            }
            return;
        }
        throw new SAXNotRecognizedException(name);
    }

    public Object getProperty(String name) throws SAXNotRecognizedException {
        if (name == null) {
            throw new NullPointerException("name == null");
        }
        if (LEXICAL_HANDLER.equals(name)) {
            return lexicalHandler;
        }
        throw new SAXNotRecognizedException(name);
    }

    public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name == null) {
            throw new NullPointerException("name == null");
        }
        if (LEXICAL_HANDLER.equals(name)) {
            if (value instanceof LexicalHandler || value == null) {
                lexicalHandler = (LexicalHandler) value;
                return;
            }
            throw new SAXNotSupportedException("value doesn't implement org.xml.sax.ext.LexicalHandler");
        }
        throw new SAXNotRecognizedException(name);
    }

    public void setEntityResolver(EntityResolver resolver) {
        entityResolver = resolver;
    }

    public EntityResolver getEntityResolver() {
        return entityResolver;
    }

    public void setDTDHandler(DTDHandler handler) {
        dtdHandler = handler;
    }

    public DTDHandler getDTDHandler() {
        return dtdHandler;
    }

    public void setContentHandler(ContentHandler handler) {
        contentHandler = handler;
    }

    public ContentHandler getContentHandler() {
        return contentHandler;
    }

    public void setErrorHandler(ErrorHandler handler) {
        errorHandler = handler;
    }

    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public void parse(String systemId) throws IOException, SAXException {
        parse(new InputSource(systemId));
    }

    public void parse(InputSource input) throws IOException, SAXException {
        final XmlPullParser parser = new SimpleXmlPullParser();
        final String publicId = input.getPublicId();
        final String systemId = input.getSystemId();
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, processNamespaces);
            Reader chars = input.getCharacterStream();
            InputStream bytes = input.getByteStream();
            boolean close = false;
            if (chars != null) {
                parser.setInput(chars);
            } else if (bytes != null) {
                parser.setInput(bytes, input.getEncoding());
            } else if (systemId != null) {
                bytes = new java.net.URL(systemId).openStream();
                close = true;
                parser.setInput(bytes, input.getEncoding());
            } else {
                throw new SAXParseException("No input specified.", publicId, systemId, -1, -1);
            }
            try {
                run(parser, publicId, systemId);
            } finally {
                if (close) {
                    bytes.close();
                }
            }
        } catch (XmlPullParserException e) {
            SAXParseException spe = new SAXParseException(e.getMessage(), publicId, systemId,
                    e.getLineNumber(), e.getColumnNumber(), e);
            if (errorHandler != null) {
                errorHandler.fatalError(spe);
            }
            throw spe;
        }
    }

    private void run(final XmlPullParser parser, final String publicId, final String systemId)
            throws XmlPullParserException, IOException, SAXException {
        ContentHandler ch = contentHandler;
        LexicalHandler lh = lexicalHandler;
        if (ch != null) {
            ch.setDocumentLocator(new Locator() {
                public String getPublicId() {
                    return publicId;
                }

                public String getSystemId() {
                    return systemId;
                }

                public int getLineNumber() {
                    return parser.getLineNumber();
                }

                public int getColumnNumber() {
                    return parser.getColumnNumber();
                }
            });
            ch.startDocument();
        }
        ArrayList<String> openPrefixes = new ArrayList<String>();
        ArrayList<Integer> prefixCounts = new ArrayList<Integer>();
        AttributesImpl attrs = new AttributesImpl();
        int type;
        while ((type = parser.nextToken()) != XmlPullParser.END_DOCUMENT) {
            switch (type) {
                case XmlPullParser.START_TAG: {
                    int nsStart = processNamespaces ? parser.getNamespaceCount(parser.getDepth() - 1) : 0;
                    int nsEnd = processNamespaces ? parser.getNamespaceCount(parser.getDepth()) : 0;
                    for (int i = nsStart; i < nsEnd; i++) {
                        String prefix = parser.getNamespacePrefix(i);
                        prefix = prefix == null ? "" : prefix;
                        openPrefixes.add(prefix);
                        if (ch != null) {
                            ch.startPrefixMapping(prefix, parser.getNamespaceUri(i));
                        }
                    }
                    prefixCounts.add(nsEnd - nsStart);
                    attrs.clear();
                    if (processNamespaces && processNamespacePrefixes) {
                        for (int i = nsStart; i < nsEnd; i++) {
                            String prefix = parser.getNamespacePrefix(i);
                            String qName = prefix == null || prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix;
                            attrs.addAttribute("", "", qName, "CDATA", parser.getNamespaceUri(i));
                        }
                    }
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        String name = parser.getAttributeName(i);
                        if (processNamespaces) {
                            String prefix = parser.getAttributePrefix(i);
                            String uri = parser.getAttributeNamespace(i);
                            String qName = prefix == null || prefix.isEmpty() ? name : prefix + ":" + name;
                            attrs.addAttribute(uri == null ? "" : uri, name, qName, "CDATA",
                                    parser.getAttributeValue(i));
                        } else {
                            // As Android's expat reader: without namespaces the local name is the qName.
                            attrs.addAttribute("", name, name, "CDATA", parser.getAttributeValue(i));
                        }
                    }
                    if (ch != null) {
                        if (processNamespaces) {
                            String uri = parser.getNamespace();
                            ch.startElement(uri == null ? "" : uri, parser.getName(), qName(parser), attrs);
                        } else {
                            ch.startElement("", "", parser.getName(), attrs);
                        }
                    }
                    break;
                }
                case XmlPullParser.END_TAG: {
                    if (ch != null) {
                        if (processNamespaces) {
                            String uri = parser.getNamespace();
                            ch.endElement(uri == null ? "" : uri, parser.getName(), qName(parser));
                        } else {
                            ch.endElement("", "", parser.getName());
                        }
                    }
                    int n = prefixCounts.isEmpty() ? 0 : prefixCounts.remove(prefixCounts.size() - 1);
                    int first = openPrefixes.size() - n;
                    for (int i = first; i < first + n; i++) {
                        if (ch != null) {
                            ch.endPrefixMapping(openPrefixes.get(i));
                        }
                    }
                    while (openPrefixes.size() > first) {
                        openPrefixes.remove(openPrefixes.size() - 1);
                    }
                    break;
                }
                case XmlPullParser.TEXT:
                case XmlPullParser.ENTITY_REF: {
                    if (ch != null && parser.getDepth() > 0) {
                        String text = parser.getText();
                        if (text != null && !text.isEmpty()) {
                            ch.characters(text.toCharArray(), 0, text.length());
                        }
                    }
                    break;
                }
                case XmlPullParser.CDSECT: {
                    String text = parser.getText();
                    if (lh != null) {
                        lh.startCDATA();
                    }
                    if (ch != null && text != null && !text.isEmpty()) {
                        ch.characters(text.toCharArray(), 0, text.length());
                    }
                    if (lh != null) {
                        lh.endCDATA();
                    }
                    break;
                }
                case XmlPullParser.COMMENT: {
                    String text = parser.getText();
                    if (lh != null && text != null) {
                        lh.comment(text.toCharArray(), 0, text.length());
                    }
                    break;
                }
                case XmlPullParser.PROCESSING_INSTRUCTION: {
                    if (ch != null) {
                        String text = parser.getText();
                        int sp = 0;
                        while (sp < text.length() && !Character.isWhitespace(text.charAt(sp))) {
                            sp++;
                        }
                        ch.processingInstruction(text.substring(0, sp), text.substring(sp).trim());
                    }
                    break;
                }
                default:
                    break;
            }
        }
        if (ch != null) {
            ch.endDocument();
        }
    }

    private static String qName(XmlPullParser parser) {
        String prefix = parser.getPrefix();
        return prefix == null || prefix.isEmpty() ? parser.getName() : prefix + ":" + parser.getName();
    }
}
