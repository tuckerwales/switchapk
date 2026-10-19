package org.w3c.dom;

public interface Element extends org.w3c.dom.Node {
    java.lang.String getTagName();
    java.lang.String getAttribute(java.lang.String p0);
    void setAttribute(java.lang.String p0, java.lang.String p1) throws org.w3c.dom.DOMException;
    void removeAttribute(java.lang.String p0) throws org.w3c.dom.DOMException;
    org.w3c.dom.Attr getAttributeNode(java.lang.String p0);
    org.w3c.dom.Attr setAttributeNode(org.w3c.dom.Attr p0) throws org.w3c.dom.DOMException;
    org.w3c.dom.Attr removeAttributeNode(org.w3c.dom.Attr p0) throws org.w3c.dom.DOMException;
    org.w3c.dom.NodeList getElementsByTagName(java.lang.String p0);
    java.lang.String getAttributeNS(java.lang.String p0, java.lang.String p1) throws org.w3c.dom.DOMException;
    void setAttributeNS(java.lang.String p0, java.lang.String p1, java.lang.String p2) throws org.w3c.dom.DOMException;
    void removeAttributeNS(java.lang.String p0, java.lang.String p1) throws org.w3c.dom.DOMException;
    org.w3c.dom.Attr getAttributeNodeNS(java.lang.String p0, java.lang.String p1) throws org.w3c.dom.DOMException;
    org.w3c.dom.Attr setAttributeNodeNS(org.w3c.dom.Attr p0) throws org.w3c.dom.DOMException;
    org.w3c.dom.NodeList getElementsByTagNameNS(java.lang.String p0, java.lang.String p1) throws org.w3c.dom.DOMException;
    boolean hasAttribute(java.lang.String p0);
    boolean hasAttributeNS(java.lang.String p0, java.lang.String p1) throws org.w3c.dom.DOMException;
    org.w3c.dom.TypeInfo getSchemaTypeInfo();
    void setIdAttribute(java.lang.String p0, boolean p1) throws org.w3c.dom.DOMException;
    void setIdAttributeNS(java.lang.String p0, java.lang.String p1, boolean p2) throws org.w3c.dom.DOMException;
    void setIdAttributeNode(org.w3c.dom.Attr p0, boolean p1) throws org.w3c.dom.DOMException;
}
