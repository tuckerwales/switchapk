package javax.xml.xpath;

public interface XPath {
    void reset();
    void setXPathVariableResolver(javax.xml.xpath.XPathVariableResolver p0);
    javax.xml.xpath.XPathVariableResolver getXPathVariableResolver();
    void setXPathFunctionResolver(javax.xml.xpath.XPathFunctionResolver p0);
    javax.xml.xpath.XPathFunctionResolver getXPathFunctionResolver();
    void setNamespaceContext(javax.xml.namespace.NamespaceContext p0);
    javax.xml.namespace.NamespaceContext getNamespaceContext();
    javax.xml.xpath.XPathExpression compile(java.lang.String p0) throws javax.xml.xpath.XPathExpressionException;
    java.lang.Object evaluate(java.lang.String p0, java.lang.Object p1, javax.xml.namespace.QName p2) throws javax.xml.xpath.XPathExpressionException;
    java.lang.String evaluate(java.lang.String p0, java.lang.Object p1) throws javax.xml.xpath.XPathExpressionException;
    java.lang.Object evaluate(java.lang.String p0, org.xml.sax.InputSource p1, javax.xml.namespace.QName p2) throws javax.xml.xpath.XPathExpressionException;
    java.lang.String evaluate(java.lang.String p0, org.xml.sax.InputSource p1) throws javax.xml.xpath.XPathExpressionException;
}
