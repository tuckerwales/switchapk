package javax.xml.parsers;

import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/**
 * Factory for SAX parsers. The only implementation is libcore.xml.SaxParserFactoryImpl
 * (a SAX2 reader over the xmlpull parser). Schema validation (getSchema/setSchema) is
 * not provided because javax.xml.validation is not part of this libcore.
 */
public abstract class SAXParserFactory {
    private boolean validating = false;
    private boolean namespaceAware = false;

    protected SAXParserFactory() {}

    public static SAXParserFactory newInstance() { return new libcore.xml.SaxParserFactoryImpl(); }

    public static SAXParserFactory newInstance(String factoryClassName, ClassLoader classLoader) {
        if (factoryClassName == null) throw new FactoryConfigurationError("factoryClassName == null");
        try {
            Class<?> type = classLoader != null
                    ? classLoader.loadClass(factoryClassName) : Class.forName(factoryClassName);
            return (SAXParserFactory) type.newInstance();
        } catch (ClassNotFoundException e) {
            throw new FactoryConfigurationError(e);
        } catch (InstantiationException e) {
            throw new FactoryConfigurationError(e);
        } catch (IllegalAccessException e) {
            throw new FactoryConfigurationError(e);
        }
    }

    public abstract SAXParser newSAXParser() throws ParserConfigurationException, SAXException;

    public void setNamespaceAware(boolean awareness) { this.namespaceAware = awareness; }
    public void setValidating(boolean validating) { this.validating = validating; }
    public boolean isNamespaceAware() { return namespaceAware; }
    public boolean isValidating() { return validating; }

    public abstract void setFeature(String name, boolean value)
            throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException;
    public abstract boolean getFeature(String name)
            throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException;

    public void setXIncludeAware(boolean state) {
        throw new UnsupportedOperationException("This parser does not support specification \"XInclude\"");
    }

    public boolean isXIncludeAware() {
        throw new UnsupportedOperationException("This parser does not support specification \"XInclude\"");
    }
}
