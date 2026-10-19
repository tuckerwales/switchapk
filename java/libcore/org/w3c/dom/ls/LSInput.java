package org.w3c.dom.ls;

public interface LSInput {
    java.io.Reader getCharacterStream();
    void setCharacterStream(java.io.Reader p0);
    java.io.InputStream getByteStream();
    void setByteStream(java.io.InputStream p0);
    java.lang.String getStringData();
    void setStringData(java.lang.String p0);
    java.lang.String getSystemId();
    void setSystemId(java.lang.String p0);
    java.lang.String getPublicId();
    void setPublicId(java.lang.String p0);
    java.lang.String getBaseURI();
    void setBaseURI(java.lang.String p0);
    java.lang.String getEncoding();
    void setEncoding(java.lang.String p0);
    boolean getCertifiedText();
    void setCertifiedText(boolean p0);
}
