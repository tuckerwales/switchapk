package libcore.xml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
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
 * SAX2 XMLReader driven by the xmlpull parser (nextToken). Not validating; DTDs are
 * skipped (no DTDHandler/DeclHandler events). Supports the namespaces and
 * namespace-prefixes features, the lexical-handler property and a Locator.
 */
public final class PullSaxReader implements XMLReader {
    private static final String FEATURES = "http://xml.org/sax/features/";
    private static final String NAMESPACES = FEATURES + "namespaces";
    private static final String NAMESPACE_PREFIXES = FEATURES + "namespace-prefixes";
    private static final String VALIDATION = FEATURES + "validation";
    private static final String EXTERNAL_GENERAL = FEATURES + "external-general-entities";
    private static final String EXTERNAL_PARAMETER = FEATURES + "external-parameter-entities";
    private static final String STRING_INTERNING = FEATURES + "string-interning";
    private static final String LEXICAL_HANDLER = "http://xml.org/sax/properties/lexical-handler";
    private static final String LEXICAL_HANDLER_ALT = "http://xml.org/sax/handlers/LexicalHandler";
    private static final String DECLARATION_HANDLER = "http://xml.org/sax/properties/declaration-handler";

    private boolean namespaces = true;
    private boolean namespacePrefixes = false;
    private ContentHandler contentHandler;
    private DTDHandler dtdHandler;
    private EntityResolver entityResolver;
    private ErrorHandler errorHandler;
    private LexicalHandler lexicalHandler;
    private Object declHandler;

    public boolean getFeature(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name == null) throw new NullPointerException("name == null");
        if (NAMESPACES.equals(name)) return namespaces;
        if (NAMESPACE_PREFIXES.equals(name)) return namespacePrefixes;
        if (VALIDATION.equals(name) || EXTERNAL_GENERAL.equals(name)
                || EXTERNAL_PARAMETER.equals(name) || STRING_INTERNING.equals(name)) return false;
        throw new SAXNotRecognizedException(name);
    }

    public void setFeature(String name, boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name == null) throw new NullPointerException("name == null");
        if (NAMESPACES.equals(name)) { namespaces = value; return; }
        if (NAMESPACE_PREFIXES.equals(name)) { namespacePrefixes = value; return; }
        if (VALIDATION.equals(name) || EXTERNAL_GENERAL.equals(name)
                || EXTERNAL_PARAMETER.equals(name) || STRING_INTERNING.equals(name)) {
            if (value) throw new SAXNotSupportedException(name);
            return;
        }
        throw new SAXNotRecognizedException(name);
    }

    public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name == null) throw new NullPointerException("name == null");
        if (LEXICAL_HANDLER.equals(name) || LEXICAL_HANDLER_ALT.equals(name)) return lexicalHandler;
        if (DECLARATION_HANDLER.equals(name)) return declHandler;
        throw new SAXNotRecognizedException(name);
    }

    public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name == null) throw new NullPointerException("name == null");
        if (LEXICAL_HANDLER.equals(name) || LEXICAL_HANDLER_ALT.equals(name)) {
            if (value != null && !(value instanceof LexicalHandler)) throw new SAXNotSupportedException(name);
            lexicalHandler = (LexicalHandler) value;
            return;
        }
        if (DECLARATION_HANDLER.equals(name)) {
            // accepted but never called: the pull parser skips the DTD
            declHandler = value;
            return;
        }
        throw new SAXNotRecognizedException(name);
    }

    public void setEntityResolver(EntityResolver resolver) { entityResolver = resolver; }
    public EntityResolver getEntityResolver() { return entityResolver; }
    public void setDTDHandler(DTDHandler handler) { dtdHandler = handler; }
    public DTDHandler getDTDHandler() { return dtdHandler; }
    public void setContentHandler(ContentHandler handler) { contentHandler = handler; }
    public ContentHandler getContentHandler() { return contentHandler; }
    public void setErrorHandler(ErrorHandler handler) { errorHandler = handler; }
    public ErrorHandler getErrorHandler() { return errorHandler; }

    public void parse(String systemId) throws IOException, SAXException {
        parse(new InputSource(systemId));
    }

    public void parse(InputSource input) throws IOException, SAXException {
        if (input == null) throw new NullPointerException("input == null");
        Reader reader = input.getCharacterStream();
        InputStream in = input.getByteStream();
        boolean close = false;
        if (reader == null && in == null) {
            String systemId = input.getSystemId();
            if (systemId == null) throw new SAXParseException("No input specified.", null);
            in = open(systemId);
            close = true;
        }
        try {
            SimpleXmlPullParser parser = new SimpleXmlPullParser();
            try {
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, namespaces);
                if (reader != null) parser.setInput(reader);
                else parser.setInput(in, input.getEncoding());
            } catch (XmlPullParserException e) {
                throw new SAXException(e);
            }
            run(parser, input.getPublicId(), input.getSystemId());
        } finally {
            if (close) in.close();
        }
    }

    private static InputStream open(String systemId) throws IOException {
        if (systemId.startsWith("file:")) {
            String path = systemId.substring(5);
            while (path.startsWith("//")) path = path.substring(1);
            return new FileInputStream(java.net.URLDecoder.decode(path, "UTF-8"));
        }
        if (systemId.indexOf(':') < 0) return new FileInputStream(systemId);
        return new java.net.URL(systemId).openStream();
    }

    private void run(final XmlPullParser parser, final String publicId, final String systemId)
            throws IOException, SAXException {
        ContentHandler ch = contentHandler;
        Locator locator = new Locator() {
            public String getPublicId() { return publicId; }
            public String getSystemId() { return systemId; }
            public int getLineNumber() { return parser.getLineNumber(); }
            public int getColumnNumber() { return parser.getColumnNumber(); }
        };
        if (ch != null) {
            ch.setDocumentLocator(locator);
            ch.startDocument();
        }
        AttributesImpl atts = new AttributesImpl();
        int[] holder = new int[2];
        try {
            while (true) {
                int type = parser.nextToken();
                switch (type) {
                    case XmlPullParser.START_TAG:
                        startElement(parser, ch, atts);
                        break;
                    case XmlPullParser.END_TAG:
                        if (ch != null) {
                            ch.endElement(uri(parser.getNamespace()), localName(parser), qName(parser));
                            if (namespaces) {
                                int depth = parser.getDepth();
                                for (int i = parser.getNamespaceCount(depth - 1);
                                        i < parser.getNamespaceCount(depth); i++) {
                                    ch.endPrefixMapping(prefix(parser.getNamespacePrefix(i)));
                                }
                            }
                        }
                        break;
                    case XmlPullParser.TEXT:
                    case XmlPullParser.ENTITY_REF:
                        if (ch != null) {
                            char[] text = parser.getTextCharacters(holder);
                            if (text != null && holder[1] > 0) ch.characters(text, holder[0], holder[1]);
                        }
                        break;
                    case XmlPullParser.IGNORABLE_WHITESPACE:
                        // whitespace outside the root element is not reported by SAX
                        break;
                    case XmlPullParser.CDSECT:
                        if (lexicalHandler != null) lexicalHandler.startCDATA();
                        if (ch != null) {
                            String text = parser.getText();
                            if (text != null && text.length() > 0) ch.characters(text.toCharArray(), 0, text.length());
                        }
                        if (lexicalHandler != null) lexicalHandler.endCDATA();
                        break;
                    case XmlPullParser.COMMENT:
                        if (lexicalHandler != null) {
                            String text = parser.getText();
                            lexicalHandler.comment(text.toCharArray(), 0, text.length());
                        }
                        break;
                    case XmlPullParser.PROCESSING_INSTRUCTION:
                        if (ch != null) {
                            String text = parser.getText();
                            int end = 0;
                            while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;
                            int dataStart = end;
                            while (dataStart < text.length() && Character.isWhitespace(text.charAt(dataStart))) dataStart++;
                            ch.processingInstruction(text.substring(0, end), text.substring(dataStart));
                        }
                        break;
                    case XmlPullParser.END_DOCUMENT:
                        if (ch != null) ch.endDocument();
                        return;
                    default:
                        // DOCDECL: the DTD is skipped
                        break;
                }
            }
        } catch (XmlPullParserException e) {
            SAXParseException spe = new SAXParseException(e.getMessage(), publicId, systemId,
                    parser.getLineNumber(), parser.getColumnNumber(), e);
            if (errorHandler != null) errorHandler.fatalError(spe);
            throw spe;
        }
    }

    private void startElement(XmlPullParser parser, ContentHandler ch, AttributesImpl atts)
            throws SAXException, XmlPullParserException {
        if (ch == null) return;
        atts.clear();
        if (namespaces) {
            int depth = parser.getDepth();
            int from = parser.getNamespaceCount(depth - 1);
            int to = parser.getNamespaceCount(depth);
            for (int i = from; i < to; i++) {
                String p = prefix(parser.getNamespacePrefix(i));
                String u = parser.getNamespaceUri(i);
                ch.startPrefixMapping(p, u);
                if (namespacePrefixes) {
                    atts.addAttribute("", "", p.length() == 0 ? "xmlns" : "xmlns:" + p, "CDATA", u);
                }
            }
        }
        int n = parser.getAttributeCount();
        for (int i = 0; i < n; i++) {
            String name = parser.getAttributeName(i);
            if (namespaces) {
                String p = parser.getAttributePrefix(i);
                String q = p == null || p.length() == 0 ? name : p + ":" + name;
                atts.addAttribute(uri(parser.getAttributeNamespace(i)), name, q,
                        parser.getAttributeType(i), parser.getAttributeValue(i));
            } else {
                atts.addAttribute("", name, name, parser.getAttributeType(i), parser.getAttributeValue(i));
            }
        }
        ch.startElement(uri(parser.getNamespace()), localName(parser), qName(parser), atts);
    }

    private String localName(XmlPullParser parser) { return namespaces ? parser.getName() : ""; }

    private static String qName(XmlPullParser parser) {
        String p = parser.getPrefix();
        String n = parser.getName();
        return p == null || p.length() == 0 ? n : p + ":" + n;
    }

    private String uri(String u) { return !namespaces || u == null ? "" : u; }
    private static String prefix(String p) { return p == null ? "" : p; }
}
