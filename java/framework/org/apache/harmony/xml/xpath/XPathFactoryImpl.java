package org.apache.harmony.xml.xpath;

import javax.xml.XMLConstants;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import javax.xml.xpath.XPathFunctionResolver;
import javax.xml.xpath.XPathVariableResolver;

/** XPath factory for the DOM object model (Android ships Xalan's org.apache.xpath.jaxp here). */
public class XPathFactoryImpl extends XPathFactory {
    private XPathVariableResolver variableResolver;
    private XPathFunctionResolver functionResolver;
    private boolean secureProcessing;

    public boolean isObjectModelSupported(String objectModel) {
        if (objectModel == null) {
            throw new NullPointerException("objectModel == null");
        }
        if (objectModel.length() == 0) {
            throw new IllegalArgumentException("objectModel == \"\"");
        }
        return DEFAULT_OBJECT_MODEL_URI.equals(objectModel);
    }

    public void setFeature(String name, boolean value) throws XPathFactoryConfigurationException {
        if (name == null) {
            throw new NullPointerException("name == null");
        }
        if (XMLConstants.FEATURE_SECURE_PROCESSING.equals(name)) {
            secureProcessing = value;
            return;
        }
        throw new XPathFactoryConfigurationException("Feature not supported: " + name);
    }

    public boolean getFeature(String name) throws XPathFactoryConfigurationException {
        if (name == null) {
            throw new NullPointerException("name == null");
        }
        if (XMLConstants.FEATURE_SECURE_PROCESSING.equals(name)) {
            return secureProcessing;
        }
        throw new XPathFactoryConfigurationException("Feature not supported: " + name);
    }

    public void setXPathVariableResolver(XPathVariableResolver resolver) {
        if (resolver == null) {
            throw new NullPointerException("resolver == null");
        }
        variableResolver = resolver;
    }

    public void setXPathFunctionResolver(XPathFunctionResolver resolver) {
        if (resolver == null) {
            throw new NullPointerException("resolver == null");
        }
        functionResolver = resolver;
    }

    public XPath newXPath() {
        return new XPathImpl(variableResolver, functionResolver);
    }
}
