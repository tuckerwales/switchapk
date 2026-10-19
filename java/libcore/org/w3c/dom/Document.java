package org.w3c.dom;

public interface Document extends org.w3c.dom.Node {
    org.w3c.dom.DocumentType getDoctype();
    org.w3c.dom.DOMImplementation getImplementation();
    org.w3c.dom.Element getDocumentElement();
    org.w3c.dom.Element createElement(java.lang.String p0) throws org.w3c.dom.DOMException;
    org.w3c.dom.DocumentFragment createDocumentFragment();
    org.w3c.dom.Text createTextNode(java.lang.String p0);
    org.w3c.dom.Comment createComment(java.lang.String p0);
    org.w3c.dom.CDATASection createCDATASection(java.lang.String p0) throws org.w3c.dom.DOMException;
    org.w3c.dom.ProcessingInstruction createProcessingInstruction(java.lang.String p0, java.lang.String p1) throws org.w3c.dom.DOMException;
    org.w3c.dom.Attr createAttribute(java.lang.String p0) throws org.w3c.dom.DOMException;
    org.w3c.dom.EntityReference createEntityReference(java.lang.String p0) throws org.w3c.dom.DOMException;
    org.w3c.dom.NodeList getElementsByTagName(java.lang.String p0);
    org.w3c.dom.Node importNode(org.w3c.dom.Node p0, boolean p1) throws org.w3c.dom.DOMException;
    org.w3c.dom.Element createElementNS(java.lang.String p0, java.lang.String p1) throws org.w3c.dom.DOMException;
    org.w3c.dom.Attr createAttributeNS(java.lang.String p0, java.lang.String p1) throws org.w3c.dom.DOMException;
    org.w3c.dom.NodeList getElementsByTagNameNS(java.lang.String p0, java.lang.String p1);
    org.w3c.dom.Element getElementById(java.lang.String p0);
    java.lang.String getInputEncoding();
    java.lang.String getXmlEncoding();
    boolean getXmlStandalone();
    void setXmlStandalone(boolean p0) throws org.w3c.dom.DOMException;
    java.lang.String getXmlVersion();
    void setXmlVersion(java.lang.String p0) throws org.w3c.dom.DOMException;
    boolean getStrictErrorChecking();
    void setStrictErrorChecking(boolean p0);
    java.lang.String getDocumentURI();
    void setDocumentURI(java.lang.String p0);
    org.w3c.dom.Node adoptNode(org.w3c.dom.Node p0) throws org.w3c.dom.DOMException;
    org.w3c.dom.DOMConfiguration getDomConfig();
    void normalizeDocument();
    org.w3c.dom.Node renameNode(org.w3c.dom.Node p0, java.lang.String p1, java.lang.String p2) throws org.w3c.dom.DOMException;
}
