package javax.xml.transform.sax;

public interface TemplatesHandler extends org.xml.sax.ContentHandler {
    javax.xml.transform.Templates getTemplates();
    void setSystemId(java.lang.String p0);
    java.lang.String getSystemId();
}
