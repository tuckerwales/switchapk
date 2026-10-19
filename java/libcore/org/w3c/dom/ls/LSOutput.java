package org.w3c.dom.ls;

public interface LSOutput {
    java.io.Writer getCharacterStream();
    void setCharacterStream(java.io.Writer p0);
    java.io.OutputStream getByteStream();
    void setByteStream(java.io.OutputStream p0);
    java.lang.String getSystemId();
    void setSystemId(java.lang.String p0);
    java.lang.String getEncoding();
    void setEncoding(java.lang.String p0);
}
