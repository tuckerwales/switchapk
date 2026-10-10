package org.xml.sax.ext;

import org.xml.sax.SAXException;

public interface LexicalHandler {
    void startDTD(String name, String publicId, String systemId) throws SAXException;
    void endDTD() throws SAXException;
    void startEntity(String name) throws SAXException;
    void endEntity(String name) throws SAXException;
    void startCDATA() throws SAXException;
    void endCDATA() throws SAXException;
    void comment(char[] ch, int start, int length) throws SAXException;
}
