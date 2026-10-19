package javax.xml.transform;

public interface ErrorListener {
    void warning(javax.xml.transform.TransformerException p0) throws javax.xml.transform.TransformerException;
    void error(javax.xml.transform.TransformerException p0) throws javax.xml.transform.TransformerException;
    void fatalError(javax.xml.transform.TransformerException p0) throws javax.xml.transform.TransformerException;
}
