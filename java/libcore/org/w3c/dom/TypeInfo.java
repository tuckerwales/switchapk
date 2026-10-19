package org.w3c.dom;

public interface TypeInfo {
    int DERIVATION_RESTRICTION = 1;
    int DERIVATION_EXTENSION = 2;
    int DERIVATION_UNION = 4;
    int DERIVATION_LIST = 8;
    java.lang.String getTypeName();
    java.lang.String getTypeNamespace();
    boolean isDerivedFrom(java.lang.String p0, java.lang.String p1, int p2);
}
