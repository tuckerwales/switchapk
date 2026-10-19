package org.apache.harmony.xml.xpath;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFunctionResolver;
import javax.xml.xpath.XPathVariableResolver;
import org.xml.sax.InputSource;

final class XPathImpl implements XPath {
    private final XPathVariableResolver originalVariableResolver;
    private final XPathFunctionResolver originalFunctionResolver;
    private XPathVariableResolver variableResolver;
    private XPathFunctionResolver functionResolver;
    private NamespaceContext namespaceContext;

    XPathImpl(XPathVariableResolver variableResolver, XPathFunctionResolver functionResolver) {
        this.originalVariableResolver = variableResolver;
        this.originalFunctionResolver = functionResolver;
        this.variableResolver = variableResolver;
        this.functionResolver = functionResolver;
    }

    public void reset() {
        variableResolver = originalVariableResolver;
        functionResolver = originalFunctionResolver;
        namespaceContext = null;
    }

    public void setXPathVariableResolver(XPathVariableResolver resolver) {
        if (resolver == null) {
            throw new NullPointerException("resolver == null");
        }
        variableResolver = resolver;
    }

    public XPathVariableResolver getXPathVariableResolver() {
        return variableResolver;
    }

    public void setXPathFunctionResolver(XPathFunctionResolver resolver) {
        if (resolver == null) {
            throw new NullPointerException("resolver == null");
        }
        functionResolver = resolver;
    }

    public XPathFunctionResolver getXPathFunctionResolver() {
        return functionResolver;
    }

    public void setNamespaceContext(NamespaceContext nsContext) {
        if (nsContext == null) {
            throw new NullPointerException("nsContext == null");
        }
        namespaceContext = nsContext;
    }

    public NamespaceContext getNamespaceContext() {
        return namespaceContext;
    }

    public XPathExpression compile(String expression) throws XPathExpressionException {
        if (expression == null) {
            throw new NullPointerException("expression == null");
        }
        return new XPathExpressionImpl(XPathParser.parse(expression, namespaceContext), variableResolver,
                functionResolver);
    }

    public Object evaluate(String expression, Object item, QName returnType) throws XPathExpressionException {
        XPathExpressionImpl.checkReturnType(returnType);
        return compile(expression).evaluate(item, returnType);
    }

    public String evaluate(String expression, Object item) throws XPathExpressionException {
        return (String) evaluate(expression, item, XPathConstants.STRING);
    }

    public Object evaluate(String expression, InputSource source, QName returnType)
            throws XPathExpressionException {
        if (source == null) {
            throw new NullPointerException("source == null");
        }
        XPathExpressionImpl.checkReturnType(returnType);
        return compile(expression).evaluate(source, returnType);
    }

    public String evaluate(String expression, InputSource source) throws XPathExpressionException {
        return (String) evaluate(expression, source, XPathConstants.STRING);
    }
}
