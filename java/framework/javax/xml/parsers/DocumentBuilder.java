package javax.xml.parsers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.validation.Schema;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public abstract class DocumentBuilder {
    protected DocumentBuilder() {
    }

    public void reset() {
        // Matches the reference: the base class does not know how to reset.
        throw new UnsupportedOperationException("This DocumentBuilder, \"" + getClass().getName()
                + "\", does not support the reset functionality.");
    }

    public Document parse(InputStream is) throws SAXException, IOException {
        if (is == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }
        return parse(new InputSource(is));
    }

    public Document parse(InputStream is, String systemId) throws SAXException, IOException {
        if (is == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }
        InputSource in = new InputSource(is);
        in.setSystemId(systemId);
        return parse(in);
    }

    public Document parse(String uri) throws SAXException, IOException {
        if (uri == null) {
            throw new IllegalArgumentException("URI cannot be null");
        }
        return parse(new InputSource(uri));
    }

    public Document parse(File f) throws SAXException, IOException {
        if (f == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        String escapedURI = FilePathToURI.filepath2URI(f.getAbsolutePath());
        return parse(new InputSource(escapedURI));
    }

    public abstract Document parse(InputSource is) throws SAXException, IOException;

    public abstract boolean isNamespaceAware();

    public abstract boolean isValidating();

    public abstract void setEntityResolver(EntityResolver er);

    public abstract void setErrorHandler(ErrorHandler eh);

    public abstract Document newDocument();

    public abstract DOMImplementation getDOMImplementation();

    public Schema getSchema() {
        throw new UnsupportedOperationException("This parser does not support specification \"Unknown\" version "
                + "\"Unknown\"");
    }

    public boolean isXIncludeAware() {
        throw new UnsupportedOperationException("This parser does not support specification \"Unknown\" version "
                + "\"Unknown\"");
    }
}
