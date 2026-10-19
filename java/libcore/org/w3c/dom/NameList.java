package org.w3c.dom;

public interface NameList {
    java.lang.String getName(int p0);
    java.lang.String getNamespaceURI(int p0);
    int getLength();
    boolean contains(java.lang.String p0);
    boolean containsNS(java.lang.String p0, java.lang.String p1);
}
