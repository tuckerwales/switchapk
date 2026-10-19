package org.apache.harmony.xml.xpath;

import java.util.List;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFunctionResolver;
import javax.xml.xpath.XPathVariableResolver;
import org.apache.harmony.xml.dom.DOMImplementationImpl;
import org.apache.harmony.xml.dom.DocumentImpl;
import org.apache.harmony.xml.dom.NodeListImpl;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

final class XPathExpressionImpl implements XPathExpression {
    private final XPathEngine.Expr expr;
    private final XPathEngine.Env env;

    XPathExpressionImpl(XPathEngine.Expr expr, XPathVariableResolver variables, XPathFunctionResolver functions) {
        this.expr = expr;
        this.env = new XPathEngine.Env(variables, functions);
    }

    static void checkReturnType(QName returnType) {
        if (returnType == null) {
            throw new NullPointerException("returnType == null");
        }
        if (!returnType.equals(XPathConstants.NUMBER) && !returnType.equals(XPathConstants.STRING)
                && !returnType.equals(XPathConstants.BOOLEAN) && !returnType.equals(XPathConstants.NODE)
                && !returnType.equals(XPathConstants.NODESET)) {
            throw new IllegalArgumentException("Unknown return type: " + returnType);
        }
    }

    public Object evaluate(Object item, QName returnType) throws XPathExpressionException {
        checkReturnType(returnType);
        Node context = item instanceof Node ? (Node) item
                : new DocumentImpl(DOMImplementationImpl.getInstance(), null, null, null, null);
        Object value;
        try {
            value = expr.eval(new XPathEngine.Context(context, 1, 1, env));
        } catch (RuntimeException e) {
            throw new XPathExpressionException(e);
        }
        return convert(value, returnType);
    }

    static Object convert(Object value, QName returnType) throws XPathExpressionException {
        if (returnType.equals(XPathConstants.STRING)) {
            return XPathEngine.toStringValue(value);
        }
        if (returnType.equals(XPathConstants.NUMBER)) {
            return XPathEngine.toNumber(value);
        }
        if (returnType.equals(XPathConstants.BOOLEAN)) {
            return XPathEngine.toBoolean(value);
        }
        if (!XPathEngine.isNodeSet(value)) {
            throw new XPathExpressionException("Can not convert #" + XPathEngine.typeName(value) + " to a NodeList!");
        }
        List<Node> nodes = XPathEngine.asNodeSet(value);
        if (returnType.equals(XPathConstants.NODE)) {
            return nodes.isEmpty() ? null : nodes.get(0);
        }
        return new NodeListImpl(nodes);
    }

    public String evaluate(Object item) throws XPathExpressionException {
        return (String) evaluate(item, XPathConstants.STRING);
    }

    public Object evaluate(InputSource source, QName returnType) throws XPathExpressionException {
        if (source == null) {
            throw new NullPointerException("source == null");
        }
        checkReturnType(returnType);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return evaluate(factory.newDocumentBuilder().parse(source), returnType);
        } catch (XPathExpressionException e) {
            throw e;
        } catch (Exception e) {
            throw new XPathExpressionException(e);
        }
    }

    public String evaluate(InputSource source) throws XPathExpressionException {
        return (String) evaluate(source, XPathConstants.STRING);
    }
}
