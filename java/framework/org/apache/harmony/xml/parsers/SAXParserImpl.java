package org.apache.harmony.xml.parsers;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.SAXParser;
import org.xml.sax.Parser;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderAdapter;

final class SAXParserImpl extends SAXParser {
    private final Map<String, Boolean> initialFeatures;
    private PullXMLReader reader;
    private Parser parser;

    SAXParserImpl(Map<String, Boolean> initialFeatures) throws SAXNotRecognizedException, SAXNotSupportedException {
        this.initialFeatures = initialFeatures.isEmpty()
                ? Collections.<String, Boolean>emptyMap() : new HashMap<String, Boolean>(initialFeatures);
        resetInternal();
    }

    private void resetInternal() throws SAXNotSupportedException, SAXNotRecognizedException {
        reader = new PullXMLReader();
        // SAXParserFactory defaults to not namespace aware, unlike a bare XMLReader.
        reader.setFeature("http://xml.org/sax/features/namespaces", false);
        for (Map.Entry<String, Boolean> entry : initialFeatures.entrySet()) {
            reader.setFeature(entry.getKey(), entry.getValue());
        }
    }

    public void reset() {
        try {
            resetInternal();
        } catch (SAXException e) {
            throw new AssertionError(e);
        }
    }

    public Parser getParser() {
        if (parser == null) {
            parser = new XMLReaderAdapter(reader);
        }
        return parser;
    }

    public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        return reader.getProperty(name);
    }

    public XMLReader getXMLReader() {
        return reader;
    }

    public boolean isNamespaceAware() {
        try {
            return reader.getFeature("http://xml.org/sax/features/namespaces");
        } catch (SAXException ex) {
            return false;
        }
    }

    public boolean isValidating() {
        return false;
    }

    public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
        reader.setProperty(name, value);
    }
}
