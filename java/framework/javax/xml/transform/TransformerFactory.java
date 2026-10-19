package javax.xml.transform;

import org.apache.harmony.xml.transform.TransformerFactoryImpl;

public abstract class TransformerFactory {
    protected TransformerFactory() {
    }

    public static TransformerFactory newDefaultInstance() {
        return new TransformerFactoryImpl();
    }

    public static TransformerFactory newInstance() throws TransformerFactoryConfigurationError {
        // Instantiate the class directly rather than using service lookup, like Android.
        return new TransformerFactoryImpl();
    }

    public static TransformerFactory newInstance(String factoryClassName, ClassLoader classLoader)
            throws TransformerFactoryConfigurationError {
        if (factoryClassName == null) {
            throw new TransformerFactoryConfigurationError("factoryClassName == null");
        }
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        try {
            Class<?> type = classLoader != null ? classLoader.loadClass(factoryClassName)
                    : Class.forName(factoryClassName);
            return (TransformerFactory) type.newInstance();
        } catch (ClassNotFoundException e) {
            throw new TransformerFactoryConfigurationError(e);
        } catch (InstantiationException e) {
            throw new TransformerFactoryConfigurationError(e);
        } catch (IllegalAccessException e) {
            throw new TransformerFactoryConfigurationError(e);
        }
    }

    public abstract Transformer newTransformer(Source source) throws TransformerConfigurationException;

    public abstract Transformer newTransformer() throws TransformerConfigurationException;

    public abstract Templates newTemplates(Source source) throws TransformerConfigurationException;

    public abstract Source getAssociatedStylesheet(Source source, String media, String title, String charset)
            throws TransformerConfigurationException;

    public abstract void setURIResolver(URIResolver resolver);

    public abstract URIResolver getURIResolver();

    public abstract void setFeature(String name, boolean value) throws TransformerConfigurationException;

    public abstract boolean getFeature(String name);

    public abstract void setAttribute(String name, Object value);

    public abstract Object getAttribute(String name);

    public abstract void setErrorListener(ErrorListener listener);

    public abstract ErrorListener getErrorListener();
}
