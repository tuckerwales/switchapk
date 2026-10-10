package org.xml.sax.ext;

import org.xml.sax.SAXException;

public interface DeclHandler {
    void elementDecl(String name, String model) throws SAXException;
    void attributeDecl(String eName, String aName, String type, String mode, String value) throws SAXException;
    void internalEntityDecl(String name, String value) throws SAXException;
    void externalEntityDecl(String name, String publicId, String systemId) throws SAXException;
}
