package org.apache.harmony.xml.parsers;

import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/** Same name as Android's factory; parsers read through PullXMLReader instead of expat. */
public class SAXParserFactoryImpl extends SAXParserFactory {
    private static final String NAMESPACES = "http://xml.org/sax/features/namespaces";
    private static final String VALIDATION = "http://xml.org/sax/features/validation";

    private final Map<String, Boolean> features = new HashMap<String, Boolean>();

    public boolean getFeature(String name) throws SAXNotRecognizedException {
        if (name == null) {
            throw new NullPointerException("name == null");
        }
        if (!name.startsWith("http://xml.org/sax/features/")) {
            throw new SAXNotRecognizedException(name);
        }
        Boolean value = features.get(name);
        return value != null && value;
    }

    public boolean isNamespaceAware() {
        try {
            return getFeature(NAMESPACES);
        } catch (SAXNotRecognizedException ex) {
            throw new AssertionError(ex);
        }
    }

    public boolean isValidating() {
        return false;
    }

    public SAXParser newSAXParser() throws ParserConfigurationException {
        if (Boolean.TRUE.equals(features.get(VALIDATION))) {
            throw new ParserConfigurationException("No validating SAXParser implementation available");
        }
        try {
            return new SAXParserImpl(features);
        } catch (Exception ex) {
            throw new ParserConfigurationException(ex.toString());
        }
    }

    public void setFeature(String name, boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name == null) {
            throw new NullPointerException("name == null");
        }
        if (!name.startsWith("http://xml.org/sax/features/")) {
            throw new SAXNotRecognizedException(name);
        }
        if (VALIDATION.equals(name) && value) {
            throw new SAXNotSupportedException("validation");
        }
        features.put(name, value);
    }

    public void setNamespaceAware(boolean value) {
        try {
            setFeature(NAMESPACES, value);
        } catch (SAXException ex) {
            throw new AssertionError(ex);
        }
    }

    public void setValidating(boolean validating) {
        try {
            setFeature(VALIDATION, validating);
        } catch (SAXException ex) {
            throw new AssertionError(ex);
        }
    }
}
