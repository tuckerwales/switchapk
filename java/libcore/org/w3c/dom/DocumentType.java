package org.w3c.dom;

public interface DocumentType extends org.w3c.dom.Node {
    java.lang.String getName();
    org.w3c.dom.NamedNodeMap getEntities();
    org.w3c.dom.NamedNodeMap getNotations();
    java.lang.String getPublicId();
    java.lang.String getSystemId();
    java.lang.String getInternalSubset();
}
