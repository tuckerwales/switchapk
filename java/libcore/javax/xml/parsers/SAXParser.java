package javax.xml.parsers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.xml.sax.HandlerBase;
import org.xml.sax.InputSource;
import org.xml.sax.Parser;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/** Wrapper around a SAX2 XMLReader (see SAXParserFactory). SAX1 HandlerBase parsing is unsupported. */
public abstract class SAXParser {
    protected SAXParser() {}

    public void reset() {
        throw new UnsupportedOperationException("reset() is not supported");
    }

    public void parse(InputStream is, HandlerBase hb) throws SAXException, IOException {
        if (is == null) throw new IllegalArgumentException("InputStream cannot be null");
        parse(new InputSource(is), hb);
    }

    public void parse(InputStream is, HandlerBase hb, String systemId) throws SAXException, IOException {
        if (is == null) throw new IllegalArgumentException("InputStream cannot be null");
        InputSource input = new InputSource(is);
        input.setSystemId(systemId);
        parse(input, hb);
    }

    public void parse(InputStream is, DefaultHandler dh) throws SAXException, IOException {
        if (is == null) throw new IllegalArgumentException("InputStream cannot be null");
        parse(new InputSource(is), dh);
    }

    public void parse(InputStream is, DefaultHandler dh, String systemId) throws SAXException, IOException {
        if (is == null) throw new IllegalArgumentException("InputStream cannot be null");
        InputSource input = new InputSource(is);
        input.setSystemId(systemId);
        parse(input, dh);
    }

    public void parse(String uri, HandlerBase hb) throws SAXException, IOException {
        if (uri == null) throw new IllegalArgumentException("uri cannot be null");
        parse(new InputSource(uri), hb);
    }

    public void parse(String uri, DefaultHandler dh) throws SAXException, IOException {
        if (uri == null) throw new IllegalArgumentException("uri cannot be null");
        parse(new InputSource(uri), dh);
    }

    public void parse(File f, HandlerBase hb) throws SAXException, IOException {
        if (f == null) throw new IllegalArgumentException("File cannot be null");
        parse(new InputSource(f.toURI().toASCIIString()), hb);
    }

    public void parse(File f, DefaultHandler dh) throws SAXException, IOException {
        if (f == null) throw new IllegalArgumentException("File cannot be null");
        parse(new InputSource(f.toURI().toASCIIString()), dh);
    }

    public void parse(InputSource is, HandlerBase hb) throws SAXException, IOException {
        if (is == null) throw new IllegalArgumentException("InputSource cannot be null");
        throw new SAXException("SAX1 HandlerBase parsing is not supported");
    }

    public void parse(InputSource is, DefaultHandler dh) throws SAXException, IOException {
        if (is == null) throw new IllegalArgumentException("InputSource cannot be null");
        XMLReader reader = getXMLReader();
        if (dh != null) {
            reader.setContentHandler(dh);
            reader.setEntityResolver(dh);
            reader.setErrorHandler(dh);
            reader.setDTDHandler(dh);
        }
        reader.parse(is);
    }

    public abstract Parser getParser() throws SAXException;
    public abstract XMLReader getXMLReader() throws SAXException;
    public abstract boolean isNamespaceAware();
    public abstract boolean isValidating();
    public abstract void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException;
    public abstract Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException;

    public boolean isXIncludeAware() {
        throw new UnsupportedOperationException("This parser does not support specification \"XInclude\"");
    }
}
