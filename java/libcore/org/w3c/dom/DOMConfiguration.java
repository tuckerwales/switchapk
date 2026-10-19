package org.w3c.dom;

public interface DOMConfiguration {
    void setParameter(java.lang.String p0, java.lang.Object p1) throws org.w3c.dom.DOMException;
    java.lang.Object getParameter(java.lang.String p0) throws org.w3c.dom.DOMException;
    boolean canSetParameter(java.lang.String p0, java.lang.Object p1);
    org.w3c.dom.DOMStringList getParameterNames();
}
