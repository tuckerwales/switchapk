package org.w3c.dom.ls;

public interface DOMImplementationLS {
    short MODE_SYNCHRONOUS = 1;
    short MODE_ASYNCHRONOUS = 2;
    org.w3c.dom.ls.LSParser createLSParser(short p0, java.lang.String p1) throws org.w3c.dom.DOMException;
    org.w3c.dom.ls.LSSerializer createLSSerializer();
    org.w3c.dom.ls.LSInput createLSInput();
    org.w3c.dom.ls.LSOutput createLSOutput();
}
