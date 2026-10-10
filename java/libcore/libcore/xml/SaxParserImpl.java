package libcore.xml;

import java.util.Map;
import javax.xml.parsers.SAXParser;
import org.xml.sax.Parser;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/** SAXParser holding one PullSaxReader. */
final class SaxParserImpl extends SAXParser {
    private final PullSaxReader reader = new PullSaxReader();

    SaxParserImpl(Map<String, Boolean> features, boolean namespaceAware) throws SAXException {
        reader.setFeature("http://xml.org/sax/features/namespaces", namespaceAware);
        for (Map.Entry<String, Boolean> e : features.entrySet()) reader.setFeature(e.getKey(), e.getValue());
    }

    @Override public void reset() {
        reader.setContentHandler(null);
        reader.setDTDHandler(null);
        reader.setEntityResolver(null);
        reader.setErrorHandler(null);
    }

    public Parser getParser() throws SAXException { throw new SAXException("SAX1 Parser is not supported"); }
    public XMLReader getXMLReader() { return reader; }

    public boolean isNamespaceAware() {
        try {
            return reader.getFeature("http://xml.org/sax/features/namespaces");
        } catch (SAXException e) {
            return false;
        }
    }

    public boolean isValidating() { return false; }

    public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
        reader.setProperty(name, value);
    }

    public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        return reader.getProperty(name);
    }
}
