package org.w3c.dom;

public interface Attr extends org.w3c.dom.Node {
    java.lang.String getName();
    boolean getSpecified();
    java.lang.String getValue();
    void setValue(java.lang.String p0) throws org.w3c.dom.DOMException;
    org.w3c.dom.Element getOwnerElement();
    org.w3c.dom.TypeInfo getSchemaTypeInfo();
    boolean isId();
}
