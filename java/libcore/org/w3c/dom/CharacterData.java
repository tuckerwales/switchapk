package org.w3c.dom;

public interface CharacterData extends org.w3c.dom.Node {
    java.lang.String getData() throws org.w3c.dom.DOMException;
    void setData(java.lang.String p0) throws org.w3c.dom.DOMException;
    int getLength();
    java.lang.String substringData(int p0, int p1) throws org.w3c.dom.DOMException;
    void appendData(java.lang.String p0) throws org.w3c.dom.DOMException;
    void insertData(int p0, java.lang.String p1) throws org.w3c.dom.DOMException;
    void deleteData(int p0, int p1) throws org.w3c.dom.DOMException;
    void replaceData(int p0, int p1, java.lang.String p2) throws org.w3c.dom.DOMException;
}
