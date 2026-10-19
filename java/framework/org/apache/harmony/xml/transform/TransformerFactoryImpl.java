package org.apache.harmony.xml.transform;

import java.util.HashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.xml.sax.XMLFilter;

/**
 * Transformer factory supporting the identity transform only (Android ships Xalan here). Asking for
 * a stylesheet-based transformer or templates throws TransformerConfigurationException.
 */
public class TransformerFactoryImpl extends SAXTransformerFactory {
    private final Map<String, Object> attributes = new HashMap<String, Object>();
    private URIResolver uriResolver;
    private ErrorListener errorListener;
    private boolean secureProcessing;

    public Transformer newTransformer(Source source) throws TransformerConfigurationException {
        if (source == null) {
            return newTransformer();
        }
        throw new TransformerConfigurationException("XSLT stylesheets are not supported");
    }

    public Transformer newTransformer() throws TransformerConfigurationException {
        TransformerImpl t = new TransformerImpl();
        if (uriResolver != null) {
            t.setURIResolver(uriResolver);
        }
        if (errorListener != null) {
            t.setErrorListener(errorListener);
        }
        return t;
    }

    public Templates newTemplates(Source source) throws TransformerConfigurationException {
        throw new TransformerConfigurationException("XSLT stylesheets are not supported");
    }

    public Source getAssociatedStylesheet(Source source, String media, String title, String charset)
            throws TransformerConfigurationException {
        return null;
    }

    public void setURIResolver(URIResolver resolver) {
        uriResolver = resolver;
    }

    public URIResolver getURIResolver() {
        return uriResolver;
    }

    public void setFeature(String name, boolean value) throws TransformerConfigurationException {
        if (name == null) {
            throw new NullPointerException("name == null");
        }
        if (XMLConstants.FEATURE_SECURE_PROCESSING.equals(name)) {
            secureProcessing = value;
            return;
        }
        throw new TransformerConfigurationException("Feature not supported: " + name);
    }

    public boolean getFeature(String name) {
        if (name == null) {
            throw new NullPointerException("name == null");
        }
        if (XMLConstants.FEATURE_SECURE_PROCESSING.equals(name)) {
            return secureProcessing;
        }
        return name.equals(DOMSource.FEATURE) || name.equals(DOMResult.FEATURE) || name.equals(StreamSource.FEATURE)
                || name.equals(StreamResult.FEATURE) || name.equals(SAXSource.FEATURE)
                || name.equals(SAXResult.FEATURE) || name.equals(SAXTransformerFactory.FEATURE);
    }

    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    public Object getAttribute(String name) {
        if (!attributes.containsKey(name)) {
            throw new IllegalArgumentException("attribute not recognized: " + name);
        }
        return attributes.get(name);
    }

    public void setErrorListener(ErrorListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener == null");
        }
        errorListener = listener;
    }

    public ErrorListener getErrorListener() {
        return errorListener;
    }

    public TransformerHandler newTransformerHandler(Source src) throws TransformerConfigurationException {
        if (src == null) {
            return newTransformerHandler();
        }
        throw new TransformerConfigurationException("XSLT stylesheets are not supported");
    }

    public TransformerHandler newTransformerHandler(Templates templates) throws TransformerConfigurationException {
        throw new TransformerConfigurationException("XSLT stylesheets are not supported");
    }

    public TransformerHandler newTransformerHandler() throws TransformerConfigurationException {
        return new TransformerHandlerImpl();
    }

    public TemplatesHandler newTemplatesHandler() throws TransformerConfigurationException {
        throw new TransformerConfigurationException("XSLT stylesheets are not supported");
    }

    public XMLFilter newXMLFilter(Source src) throws TransformerConfigurationException {
        throw new TransformerConfigurationException("XSLT stylesheets are not supported");
    }

    public XMLFilter newXMLFilter(Templates templates) throws TransformerConfigurationException {
        throw new TransformerConfigurationException("XSLT stylesheets are not supported");
    }
}
