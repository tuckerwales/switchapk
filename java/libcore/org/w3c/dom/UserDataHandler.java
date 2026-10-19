package org.w3c.dom;

public interface UserDataHandler {
    short NODE_CLONED = 1;
    short NODE_IMPORTED = 2;
    short NODE_DELETED = 3;
    short NODE_RENAMED = 4;
    short NODE_ADOPTED = 5;
    void handle(short p0, java.lang.String p1, java.lang.Object p2, org.w3c.dom.Node p3, org.w3c.dom.Node p4);
}
