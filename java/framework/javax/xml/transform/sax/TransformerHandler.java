package javax.xml.transform.sax;

public interface TransformerHandler extends org.xml.sax.ContentHandler, org.xml.sax.ext.LexicalHandler, org.xml.sax.DTDHandler {
    void setResult(javax.xml.transform.Result p0) throws java.lang.IllegalArgumentException;
    void setSystemId(java.lang.String p0);
    java.lang.String getSystemId();
    javax.xml.transform.Transformer getTransformer();
}
