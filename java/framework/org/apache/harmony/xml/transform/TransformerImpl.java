package org.apache.harmony.xml.transform;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.apache.harmony.xml.dom.DOMImplementationImpl;
import org.apache.harmony.xml.dom.DocumentImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;

/**
 * The identity transformer: copies a DOM, stream or SAX source to a stream, DOM or SAX result.
 * XSLT stylesheets are not supported.
 */
public final class TransformerImpl extends Transformer {
    static final String INDENT_AMOUNT = "{http://xml.apache.org/xslt}indent-amount";
    static final String INDENT_AMOUNT_XALAN = "{http://xml.apache.org/xalan}indent-amount";

    private static final Properties DEFAULTS = new Properties();

    static {
        DEFAULTS.setProperty(OutputKeys.METHOD, "xml");
        DEFAULTS.setProperty(OutputKeys.VERSION, "1.0");
        DEFAULTS.setProperty(OutputKeys.ENCODING, "UTF-8");
        DEFAULTS.setProperty(OutputKeys.INDENT, "no");
        DEFAULTS.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        DEFAULTS.setProperty(OutputKeys.STANDALONE, "no");
        DEFAULTS.setProperty(OutputKeys.MEDIA_TYPE, "text/xml");
        // Android's serializer does not indent by default even when indent="yes".
        DEFAULTS.setProperty(INDENT_AMOUNT, "0");
    }

    private Properties outputProperties = new Properties();
    private final Map<String, Object> parameters = new HashMap<String, Object>();
    private URIResolver uriResolver;
    private ErrorListener errorListener;

    public TransformerImpl() {
    }

    public void reset() {
        outputProperties = new Properties();
        parameters.clear();
        uriResolver = null;
        errorListener = null;
    }

    private static boolean isValidKey(String name) {
        if (name == null) {
            throw new NullPointerException("name == null");
        }
        if (name.startsWith("{")) {
            return true;
        }
        return name.equals(OutputKeys.METHOD) || name.equals(OutputKeys.VERSION) || name.equals(OutputKeys.ENCODING)
                || name.equals(OutputKeys.OMIT_XML_DECLARATION) || name.equals(OutputKeys.STANDALONE)
                || name.equals(OutputKeys.DOCTYPE_PUBLIC) || name.equals(OutputKeys.DOCTYPE_SYSTEM)
                || name.equals(OutputKeys.CDATA_SECTION_ELEMENTS) || name.equals(OutputKeys.INDENT)
                || name.equals(OutputKeys.MEDIA_TYPE);
    }

    public void setOutputProperty(String name, String value) throws IllegalArgumentException {
        if (!isValidKey(name)) {
            throw new IllegalArgumentException("output property not recognized: " + name);
        }
        if (name.equals(INDENT_AMOUNT_XALAN)) {
            name = INDENT_AMOUNT;
        }
        if (value == null) {
            outputProperties.remove(name);
        } else {
            outputProperties.setProperty(name, value);
        }
    }

    public String getOutputProperty(String name) throws IllegalArgumentException {
        if (!isValidKey(name)) {
            throw new IllegalArgumentException("output property not recognized: " + name);
        }
        if (name.equals(INDENT_AMOUNT_XALAN)) {
            name = INDENT_AMOUNT;
        }
        String value = outputProperties.getProperty(name);
        return value != null ? value : DEFAULTS.getProperty(name);
    }

    public void setOutputProperties(Properties oformat) {
        outputProperties = new Properties();
        if (oformat != null) {
            for (Enumeration<?> e = oformat.propertyNames(); e.hasMoreElements(); ) {
                String key = (String) e.nextElement();
                setOutputProperty(key, oformat.getProperty(key));
            }
        }
    }

    public Properties getOutputProperties() {
        Properties p = new Properties(DEFAULTS);
        for (Enumeration<?> e = outputProperties.propertyNames(); e.hasMoreElements(); ) {
            String key = (String) e.nextElement();
            p.setProperty(key, outputProperties.getProperty(key));
        }
        return p;
    }

    public void setParameter(String name, Object value) {
        if (name == null) {
            throw new NullPointerException("name == null");
        }
        if (value == null) {
            throw new IllegalArgumentException("value == null");
        }
        parameters.put(name, value);
    }

    public Object getParameter(String name) {
        return parameters.get(name);
    }

    public void clearParameters() {
        parameters.clear();
    }

    public void setURIResolver(URIResolver resolver) {
        uriResolver = resolver;
    }

    public URIResolver getURIResolver() {
        return uriResolver;
    }

    public void setErrorListener(ErrorListener listener) throws IllegalArgumentException {
        if (listener == null) {
            throw new IllegalArgumentException("listener == null");
        }
        errorListener = listener;
    }

    public ErrorListener getErrorListener() {
        return errorListener;
    }

    // ---- transform ------------------------------------------------------------------------

    public void transform(Source source, Result result) throws TransformerException {
        if (source == null) {
            throw new TransformerException("source == null");
        }
        if (result == null) {
            throw new TransformerException("result == null");
        }
        Node node = toNode(source);
        try {
            if (result instanceof StreamResult) {
                writeStream(node, (StreamResult) result);
            } else if (result instanceof DOMResult) {
                writeDom(node, (DOMResult) result);
            } else if (result instanceof SAXResult) {
                SAXResult sr = (SAXResult) result;
                ContentHandler handler = sr.getHandler();
                if (handler == null) {
                    throw new TransformerException("SAXResult has no ContentHandler");
                }
                LexicalHandler lexical = sr.getLexicalHandler();
                if (lexical == null && handler instanceof LexicalHandler) {
                    lexical = (LexicalHandler) handler;
                }
                new DomToSax(handler, lexical).run(node);
            } else {
                throw new TransformerException("Can't transform to a Result of type " + result.getClass().getName());
            }
        } catch (IOException e) {
            throw new TransformerException(e);
        } catch (SAXException e) {
            throw new TransformerException(e);
        }
    }

    private Node toNode(Source source) throws TransformerException {
        if (source instanceof DOMSource) {
            Node node = ((DOMSource) source).getNode();
            return node != null ? node : new DocumentImpl(DOMImplementationImpl.getInstance(), null, null, null, null);
        }
        try {
            if (source instanceof SAXSource) {
                SAXSource ss = (SAXSource) source;
                XMLReader reader = ss.getXMLReader();
                InputSource input = ss.getInputSource();
                if (reader != null) {
                    SaxToDom builder = new SaxToDom();
                    reader.setContentHandler(builder);
                    try {
                        reader.setProperty("http://xml.org/sax/properties/lexical-handler", builder);
                    } catch (SAXException ignored) {
                        // comments and CDATA bounds are then lost
                    }
                    reader.parse(input);
                    return builder.getDocument();
                }
                return parse(input);
            }
            if (source instanceof StreamSource) {
                StreamSource ss = (StreamSource) source;
                InputSource input = new InputSource(ss.getSystemId());
                input.setPublicId(ss.getPublicId());
                input.setByteStream(ss.getInputStream());
                input.setCharacterStream(ss.getReader());
                return parse(input);
            }
        } catch (IOException e) {
            throw new TransformerException(e);
        } catch (SAXException e) {
            throw new TransformerException(e);
        }
        throw new TransformerException("Can't transform a Source of type " + source.getClass().getName());
    }

    private static Document parse(InputSource input) throws SAXException, IOException, TransformerException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            return factory.newDocumentBuilder().parse(input);
        } catch (ParserConfigurationException e) {
            throw new TransformerException(e);
        }
    }

    private void writeDom(Node node, DOMResult result) {
        Node target = result.getNode();
        Document doc;
        if (target == null) {
            doc = new DocumentImpl(DOMImplementationImpl.getInstance(), null, null, null, null);
            result.setNode(doc);
            target = doc;
        } else {
            doc = target.getNodeType() == Node.DOCUMENT_NODE ? (Document) target : target.getOwnerDocument();
        }
        Node next = result.getNextSibling();
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            for (Node c = node.getFirstChild(); c != null; c = c.getNextSibling()) {
                if (c.getNodeType() == Node.DOCUMENT_TYPE_NODE) {
                    continue;
                }
                target.insertBefore(doc.importNode(c, true), next);
            }
        } else {
            target.insertBefore(doc.importNode(node, true), next);
        }
    }

    private void writeStream(Node node, StreamResult result) throws IOException, TransformerException {
        String encoding = getOutputProperty(OutputKeys.ENCODING);
        try {
            java.nio.charset.Charset.forName(encoding);
        } catch (RuntimeException e) {
            encoding = "UTF-8";
        }
        Writer writer = result.getWriter();
        OutputStream toClose = null;
        if (writer == null) {
            OutputStream os = result.getOutputStream();
            if (os == null) {
                String systemId = result.getSystemId();
                if (systemId == null) {
                    throw new TransformerException("Result object passed to ''{0}'' is invalid.");
                }
                os = new FileOutputStream(systemIdToFile(systemId));
                toClose = os;
            }
            writer = new OutputStreamWriter(os, encoding);
        }
        try {
            DomSerializer s = new DomSerializer(writer, encoding);
            s.method = getOutputProperty(OutputKeys.METHOD);
            s.indent = "yes".equals(getOutputProperty(OutputKeys.INDENT));
            try {
                s.indentAmount = Math.max(0, Integer.parseInt(getOutputProperty(INDENT_AMOUNT).trim()));
            } catch (NumberFormatException e) {
                s.indentAmount = 0;
            }
            s.omitDeclaration = "yes".equals(getOutputProperty(OutputKeys.OMIT_XML_DECLARATION));
            s.standalone = outputProperties.getProperty(OutputKeys.STANDALONE);
            s.version = getOutputProperty(OutputKeys.VERSION);
            s.doctypeSystem = outputProperties.getProperty(OutputKeys.DOCTYPE_SYSTEM);
            s.doctypePublic = outputProperties.getProperty(OutputKeys.DOCTYPE_PUBLIC);
            s.serialize(node);
            writer.flush();
        } finally {
            if (toClose != null) {
                toClose.close();
            }
        }
    }

    private static File systemIdToFile(String systemId) throws IOException {
        if (systemId.startsWith("file:")) {
            String path = systemId.substring(5);
            while (path.startsWith("//")) {
                path = path.substring(1);
            }
            return new File(URLDecoder.decode(path.replace("+", "%2B"), "UTF-8"));
        }
        return new File(systemId);
    }
}
