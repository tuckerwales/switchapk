package javax.xml.parsers;

import javax.xml.validation.Schema;
import org.apache.harmony.xml.parsers.DocumentBuilderFactoryImpl;

public abstract class DocumentBuilderFactory {
    private boolean validating = false;
    private boolean namespaceAware = false;
    private boolean whitespace = false;
    private boolean expandEntityRef = true;
    private boolean ignoreComments = false;
    private boolean coalescing = false;

    protected DocumentBuilderFactory() {
    }

    public static DocumentBuilderFactory newDefaultNSInstance() {
        DocumentBuilderFactory f = new DocumentBuilderFactoryImpl();
        f.setNamespaceAware(true);
        return f;
    }

    public static DocumentBuilderFactory newNSInstance() {
        return newDefaultNSInstance();
    }

    public static DocumentBuilderFactory newNSInstance(String factoryClassName, ClassLoader classLoader) {
        DocumentBuilderFactory f = newInstance(factoryClassName, classLoader);
        f.setNamespaceAware(true);
        return f;
    }

    public static DocumentBuilderFactory newDefaultInstance() {
        return new DocumentBuilderFactoryImpl();
    }

    public static DocumentBuilderFactory newInstance() {
        // instantiate the class directly rather than using reflection
        return new DocumentBuilderFactoryImpl();
    }

    public static DocumentBuilderFactory newInstance(String factoryClassName, ClassLoader classLoader) {
        if (factoryClassName == null) {
            throw new FactoryConfigurationError("factoryClassName == null");
        }
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        try {
            Class<?> type = classLoader != null ? classLoader.loadClass(factoryClassName)
                    : Class.forName(factoryClassName);
            return (DocumentBuilderFactory) type.newInstance();
        } catch (ClassNotFoundException e) {
            throw new FactoryConfigurationError(e);
        } catch (InstantiationException e) {
            throw new FactoryConfigurationError(e);
        } catch (IllegalAccessException e) {
            throw new FactoryConfigurationError(e);
        }
    }

    public abstract DocumentBuilder newDocumentBuilder() throws ParserConfigurationException;

    public void setNamespaceAware(boolean awareness) {
        this.namespaceAware = awareness;
    }

    public void setValidating(boolean validating) {
        this.validating = validating;
    }

    public void setIgnoringElementContentWhitespace(boolean whitespace) {
        this.whitespace = whitespace;
    }

    public void setExpandEntityReferences(boolean expandEntityRef) {
        this.expandEntityRef = expandEntityRef;
    }

    public void setIgnoringComments(boolean ignoreComments) {
        this.ignoreComments = ignoreComments;
    }

    public void setCoalescing(boolean coalescing) {
        this.coalescing = coalescing;
    }

    public boolean isNamespaceAware() {
        return namespaceAware;
    }

    public boolean isValidating() {
        return validating;
    }

    public boolean isIgnoringElementContentWhitespace() {
        return whitespace;
    }

    public boolean isExpandEntityReferences() {
        return expandEntityRef;
    }

    public boolean isIgnoringComments() {
        return ignoreComments;
    }

    public boolean isCoalescing() {
        return coalescing;
    }

    public abstract void setAttribute(String name, Object value) throws IllegalArgumentException;

    public abstract Object getAttribute(String name) throws IllegalArgumentException;

    public abstract void setFeature(String name, boolean value) throws ParserConfigurationException;

    public abstract boolean getFeature(String name) throws ParserConfigurationException;

    public Schema getSchema() {
        throw new UnsupportedOperationException("This parser does not support specification \"Unknown\" version "
                + "\"Unknown\"");
    }

    public void setSchema(Schema schema) {
        throw new UnsupportedOperationException("This parser does not support specification \"Unknown\" version "
                + "\"Unknown\"");
    }

    public void setXIncludeAware(final boolean state) {
        throw new UnsupportedOperationException("This parser does not support specification \"Unknown\" version "
                + "\"Unknown\"");
    }

    public boolean isXIncludeAware() {
        throw new UnsupportedOperationException("This parser does not support specification \"Unknown\" version "
                + "\"Unknown\"");
    }
}
