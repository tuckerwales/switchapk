package org.w3c.dom.ls;

public interface LSSerializer {
    org.w3c.dom.DOMConfiguration getDomConfig();
    java.lang.String getNewLine();
    void setNewLine(java.lang.String p0);
    org.w3c.dom.ls.LSSerializerFilter getFilter();
    void setFilter(org.w3c.dom.ls.LSSerializerFilter p0);
    boolean write(org.w3c.dom.Node p0, org.w3c.dom.ls.LSOutput p1) throws org.w3c.dom.ls.LSException;
    boolean writeToURI(org.w3c.dom.Node p0, java.lang.String p1) throws org.w3c.dom.ls.LSException;
    java.lang.String writeToString(org.w3c.dom.Node p0) throws org.w3c.dom.DOMException, org.w3c.dom.ls.LSException;
}
