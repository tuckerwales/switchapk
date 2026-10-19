package org.w3c.dom;

public interface NamedNodeMap {
    org.w3c.dom.Node getNamedItem(java.lang.String p0);
    org.w3c.dom.Node setNamedItem(org.w3c.dom.Node p0) throws org.w3c.dom.DOMException;
    org.w3c.dom.Node removeNamedItem(java.lang.String p0) throws org.w3c.dom.DOMException;
    org.w3c.dom.Node item(int p0);
    int getLength();
    org.w3c.dom.Node getNamedItemNS(java.lang.String p0, java.lang.String p1) throws org.w3c.dom.DOMException;
    org.w3c.dom.Node setNamedItemNS(org.w3c.dom.Node p0) throws org.w3c.dom.DOMException;
    org.w3c.dom.Node removeNamedItemNS(java.lang.String p0, java.lang.String p1) throws org.w3c.dom.DOMException;
}
