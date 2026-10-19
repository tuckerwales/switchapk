package org.apache.harmony.xml.parsers;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/** Same name as Android's factory; builders read through the framework's pull parser. */
public class DocumentBuilderFactoryImpl extends DocumentBuilderFactory {
    private static final String NAMESPACES = "http://xml.org/sax/features/namespaces";
    private static final String VALIDATION = "http://xml.org/sax/features/validation";

    private boolean secureProcessing;

    public Object getAttribute(String name) throws IllegalArgumentException {
        throw new IllegalArgumentException(name);
    }

    public boolean getFeature(String name) throws ParserConfigurationException {
        if (name == null) {
            throw new NullPointerException("name == null");
        }
        if (NAMESPACES.equals(name)) {
            return isNamespaceAware();
        } else if (VALIDATION.equals(name)) {
            return isValidating();
        } else if (XMLConstants.FEATURE_SECURE_PROCESSING.equals(name)) {
            return secureProcessing;
        } else {
            throw new ParserConfigurationException(name);
        }
    }

    public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
        if (isValidating()) {
            throw new ParserConfigurationException("No validating DocumentBuilder implementation available");
        }
        DocumentBuilderImpl builder = new DocumentBuilderImpl();
        builder.setCoalescing(isCoalescing());
        builder.setIgnoreComments(isIgnoringComments());
        builder.setIgnoreElementContentWhitespace(isIgnoringElementContentWhitespace());
        builder.setNamespaceAware(isNamespaceAware());
        // Entity references are always expanded; see DocumentBuilderImpl.
        return builder;
    }

    public void setAttribute(String name, Object value) throws IllegalArgumentException {
        throw new IllegalArgumentException(name);
    }

    public void setFeature(String name, boolean value) throws ParserConfigurationException {
        if (name == null) {
            throw new NullPointerException("name == null");
        }
        if (NAMESPACES.equals(name)) {
            setNamespaceAware(value);
        } else if (VALIDATION.equals(name)) {
            setValidating(value);
        } else if (XMLConstants.FEATURE_SECURE_PROCESSING.equals(name)) {
            // External entities and DTDs are never fetched, so this is always effectively on.
            secureProcessing = value;
        } else {
            throw new ParserConfigurationException(name);
        }
    }
}
