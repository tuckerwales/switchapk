package org.w3c.dom;

public interface DOMImplementation {
    boolean hasFeature(java.lang.String p0, java.lang.String p1);
    org.w3c.dom.DocumentType createDocumentType(java.lang.String p0, java.lang.String p1, java.lang.String p2) throws org.w3c.dom.DOMException;
    org.w3c.dom.Document createDocument(java.lang.String p0, java.lang.String p1, org.w3c.dom.DocumentType p2) throws org.w3c.dom.DOMException;
    java.lang.Object getFeature(java.lang.String p0, java.lang.String p1);
}
