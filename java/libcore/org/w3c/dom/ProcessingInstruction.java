package org.w3c.dom;

public interface ProcessingInstruction extends org.w3c.dom.Node {
    java.lang.String getTarget();
    java.lang.String getData();
    void setData(java.lang.String p0) throws org.w3c.dom.DOMException;
}
