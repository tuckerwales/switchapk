package javax.xml.xpath;

public interface XPathExpression {
    java.lang.Object evaluate(java.lang.Object p0, javax.xml.namespace.QName p1) throws javax.xml.xpath.XPathExpressionException;
    java.lang.String evaluate(java.lang.Object p0) throws javax.xml.xpath.XPathExpressionException;
    java.lang.Object evaluate(org.xml.sax.InputSource p0, javax.xml.namespace.QName p1) throws javax.xml.xpath.XPathExpressionException;
    java.lang.String evaluate(org.xml.sax.InputSource p0) throws javax.xml.xpath.XPathExpressionException;
}
