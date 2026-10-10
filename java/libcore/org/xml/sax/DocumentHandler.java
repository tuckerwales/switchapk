package org.xml.sax;

/** SAX1 document handler (deprecated in SAX2). */
@Deprecated
public interface DocumentHandler {
    void setDocumentLocator(Locator locator);
    void startDocument() throws SAXException;
    void endDocument() throws SAXException;
    void startElement(String name, AttributeList atts) throws SAXException;
    void endElement(String name) throws SAXException;
    void characters(char[] ch, int start, int length) throws SAXException;
    void ignorableWhitespace(char[] ch, int start, int length) throws SAXException;
    void processingInstruction(String target, String data) throws SAXException;
}
