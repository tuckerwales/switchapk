package javax.xml.xpath;

import org.apache.harmony.xml.xpath.XPathFactoryImpl;

public abstract class XPathFactory {
    public static final String DEFAULT_PROPERTY_NAME = "javax.xml.xpath.XPathFactory";
    public static final String DEFAULT_OBJECT_MODEL_URI = "http://java.sun.com/jaxp/xpath/dom";

    protected XPathFactory() {
    }

    public static XPathFactory newDefaultInstance() {
        return new XPathFactoryImpl();
    }

    public static XPathFactory newInstance() {
        try {
            return newInstance(DEFAULT_OBJECT_MODEL_URI);
        } catch (XPathFactoryConfigurationException e) {
            throw new RuntimeException("XPathFactory#newInstance() failed to create an XPathFactory for the default "
                    + "object model: " + DEFAULT_OBJECT_MODEL_URI + " with the XPathFactoryConfigurationException: "
                    + e.toString());
        }
    }

    public static XPathFactory newInstance(String uri) throws XPathFactoryConfigurationException {
        if (uri == null) {
            throw new NullPointerException("uri == null");
        }
        if (uri.length() == 0) {
            throw new IllegalArgumentException("XPathFactory#newInstance(String uri) cannot be called with uri == \"\"");
        }
        if (!DEFAULT_OBJECT_MODEL_URI.equals(uri)) {
            throw new XPathFactoryConfigurationException("No XPathFactory implementation found for the object model: "
                    + uri);
        }
        return new XPathFactoryImpl();
    }

    public static XPathFactory newInstance(String uri, String factoryClassName, ClassLoader classLoader)
            throws XPathFactoryConfigurationException {
        if (uri == null) {
            throw new NullPointerException("uri == null");
        }
        if (uri.length() == 0) {
            throw new IllegalArgumentException("XPathFactory#newInstance(String uri) cannot be called with uri == \"\"");
        }
        if (factoryClassName == null) {
            throw new XPathFactoryConfigurationException("factoryClassName cannot be null.");
        }
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        try {
            Class<?> type = classLoader != null ? classLoader.loadClass(factoryClassName)
                    : Class.forName(factoryClassName);
            XPathFactory factory = (XPathFactory) type.newInstance();
            if (factory.isObjectModelSupported(uri)) {
                return factory;
            }
        } catch (Exception e) {
            throw new XPathFactoryConfigurationException(e);
        }
        throw new XPathFactoryConfigurationException("No XPathFactory implementation found for the object model: "
                + uri);
    }

    public abstract boolean isObjectModelSupported(String objectModel);

    public abstract void setFeature(String name, boolean value) throws XPathFactoryConfigurationException;

    public abstract boolean getFeature(String name) throws XPathFactoryConfigurationException;

    public abstract void setXPathVariableResolver(XPathVariableResolver resolver);

    public abstract void setXPathFunctionResolver(XPathFunctionResolver resolver);

    public abstract XPath newXPath();

    public void setProperty(String name, String value) {
        if (name == null) {
            throw new NullPointerException("the name parameter is null");
        }
        throw new IllegalArgumentException("cannot set the property " + name);
    }

    public String getProperty(String name) {
        if (name == null) {
            throw new NullPointerException("the name parameter is null");
        }
        throw new IllegalArgumentException("cannot get the property " + name);
    }
}
