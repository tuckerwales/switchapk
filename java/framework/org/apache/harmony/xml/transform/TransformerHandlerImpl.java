package org.apache.harmony.xml.transform;

import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.TransformerHandler;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/** Identity TransformerHandler: collects SAX events into a DOM and writes it on endDocument(). */
final class TransformerHandlerImpl implements TransformerHandler {
    private final TransformerImpl transformer = new TransformerImpl();
    private SaxToDom builder = new SaxToDom();
    private Result result;
    private String systemId;

    public void setResult(Result result) throws IllegalArgumentException {
        if (result == null) {
            throw new IllegalArgumentException("result == null");
        }
        this.result = result;
    }

    public void setSystemId(String systemID) {
        this.systemId = systemID;
    }

    public String getSystemId() {
        return systemId;
    }

    public Transformer getTransformer() {
        return transformer;
    }

    public void setDocumentLocator(Locator locator) {
    }

    public void startDocument() throws SAXException {
        builder = new SaxToDom();
    }

    public void endDocument() throws SAXException {
        if (result == null) {
            throw new SAXException("TransformerHandler has no Result");
        }
        try {
            transformer.transform(new DOMSource(builder.getDocument(), systemId), result);
        } catch (TransformerException e) {
            throw new SAXException(e);
        }
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        builder.startPrefixMapping(prefix, uri);
    }

    public void endPrefixMapping(String prefix) throws SAXException {
    }

    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        builder.startElement(uri, localName, qName, atts);
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        builder.endElement(uri, localName, qName);
    }

    public void characters(char[] ch, int start, int length) throws SAXException {
        builder.characters(ch, start, length);
    }

    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        builder.ignorableWhitespace(ch, start, length);
    }

    public void processingInstruction(String target, String data) throws SAXException {
        builder.processingInstruction(target, data);
    }

    public void skippedEntity(String name) throws SAXException {
    }

    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        builder.startDTD(name, publicId, systemId);
    }

    public void endDTD() throws SAXException {
        builder.endDTD();
    }

    public void startEntity(String name) throws SAXException {
    }

    public void endEntity(String name) throws SAXException {
    }

    public void startCDATA() throws SAXException {
        builder.startCDATA();
    }

    public void endCDATA() throws SAXException {
        builder.endCDATA();
    }

    public void comment(char[] ch, int start, int length) throws SAXException {
        builder.comment(ch, start, length);
    }

    public void notationDecl(String name, String publicId, String systemId) throws SAXException {
    }

    public void unparsedEntityDecl(String name, String publicId, String systemId, String notationName)
            throws SAXException {
    }
}
