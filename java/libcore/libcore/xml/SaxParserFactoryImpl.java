package libcore.xml;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import java.util.HashMap;
import java.util.Map;

/** SAXParserFactory over PullSaxReader. Validation is not supported. */
public final class SaxParserFactoryImpl extends SAXParserFactory {
    private static final String NAMESPACES = "http://xml.org/sax/features/namespaces";
    private static final String VALIDATION = "http://xml.org/sax/features/validation";
    private final Map<String, Boolean> features = new HashMap<String, Boolean>();

    public boolean getFeature(String name) throws SAXNotRecognizedException {
        if (name == null) throw new NullPointerException("name == null");
        if (!name.startsWith("http://xml.org/sax/features/")) throw new SAXNotRecognizedException(name);
        if (NAMESPACES.equals(name)) return isNamespaceAware();
        if (VALIDATION.equals(name)) return isValidating();
        Boolean v = features.get(name);
        if (v != null) return v;
        try {
            return new PullSaxReader().getFeature(name);
        } catch (SAXNotSupportedException e) {
            throw new SAXNotRecognizedException(name);
        }
    }

    public void setFeature(String name, boolean value) throws SAXNotRecognizedException {
        if (name == null) throw new NullPointerException("name == null");
        if (!name.startsWith("http://xml.org/sax/features/")) throw new SAXNotRecognizedException(name);
        if (NAMESPACES.equals(name)) setNamespaceAware(value);
        else if (VALIDATION.equals(name)) setValidating(value);
        else features.put(name, value);
    }

    public SAXParser newSAXParser() throws ParserConfigurationException {
        if (isValidating()) throw new ParserConfigurationException("No validating SAXParser implementation available");
        try {
            return new SaxParserImpl(features, isNamespaceAware());
        } catch (Exception e) {
            throw new ParserConfigurationException(e.toString());
        }
    }
}
