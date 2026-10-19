package org.w3c.dom;

public interface Entity extends org.w3c.dom.Node {
    java.lang.String getPublicId();
    java.lang.String getSystemId();
    java.lang.String getNotationName();
    java.lang.String getInputEncoding();
    java.lang.String getXmlEncoding();
    java.lang.String getXmlVersion();
}
