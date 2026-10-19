package org.w3c.dom.ls;

public interface LSParser {
    short ACTION_APPEND_AS_CHILDREN = 1;
    short ACTION_REPLACE_CHILDREN = 2;
    short ACTION_INSERT_BEFORE = 3;
    short ACTION_INSERT_AFTER = 4;
    short ACTION_REPLACE = 5;
    org.w3c.dom.DOMConfiguration getDomConfig();
    org.w3c.dom.ls.LSParserFilter getFilter();
    void setFilter(org.w3c.dom.ls.LSParserFilter p0);
    boolean getAsync();
    boolean getBusy();
    org.w3c.dom.Document parse(org.w3c.dom.ls.LSInput p0) throws org.w3c.dom.DOMException, org.w3c.dom.ls.LSException;
    org.w3c.dom.Document parseURI(java.lang.String p0) throws org.w3c.dom.DOMException, org.w3c.dom.ls.LSException;
    org.w3c.dom.Node parseWithContext(org.w3c.dom.ls.LSInput p0, org.w3c.dom.Node p1, short p2) throws org.w3c.dom.DOMException, org.w3c.dom.ls.LSException;
    void abort();
}
