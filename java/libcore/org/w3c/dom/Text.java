package org.w3c.dom;

public interface Text extends org.w3c.dom.CharacterData {
    org.w3c.dom.Text splitText(int p0) throws org.w3c.dom.DOMException;
    boolean isElementContentWhitespace();
    java.lang.String getWholeText();
    org.w3c.dom.Text replaceWholeText(java.lang.String p0) throws org.w3c.dom.DOMException;
}
