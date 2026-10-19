package org.apache.harmony.xml.transform;

import java.util.ArrayList;
import java.util.List;
import org.apache.harmony.xml.dom.DOMImplementationImpl;
import org.apache.harmony.xml.dom.DocumentImpl;
import org.apache.harmony.xml.dom.DocumentTypeImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

/** Builds a namespace-aware DOM document from SAX events. */
class SaxToDom implements ContentHandler, LexicalHandler {
    private static final String XMLNS_NS = "http://www.w3.org/2000/xmlns/";

    final DocumentImpl document;
    private Node current;
    private boolean inCData;
    private boolean inDtd;
    private final List<String[]> pendingPrefixes = new ArrayList<String[]>();

    SaxToDom() {
        document = new DocumentImpl(DOMImplementationImpl.getInstance(), null, null, null, null);
        current = document;
    }

    public void setDocumentLocator(Locator locator) {
    }

    public void startDocument() throws SAXException {
    }

    public void endDocument() throws SAXException {
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        pendingPrefixes.add(new String[] {prefix == null ? "" : prefix, uri == null ? "" : uri});
    }

    public void endPrefixMapping(String prefix) throws SAXException {
    }

    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        String name = qName != null && !qName.isEmpty() ? qName : localName;
        Element e = document.createElementNS(uri == null || uri.isEmpty() ? null : uri, name);
        for (String[] p : pendingPrefixes) {
            String attr = p[0].isEmpty() ? "xmlns" : "xmlns:" + p[0];
            e.setAttributeNS(XMLNS_NS, attr, p[1]);
        }
        pendingPrefixes.clear();
        for (int i = 0; i < atts.getLength(); i++) {
            String an = atts.getQName(i);
            if (an == null || an.isEmpty()) {
                an = atts.getLocalName(i);
            }
            if (an.equals("xmlns") || an.startsWith("xmlns:")) {
                e.setAttributeNS(XMLNS_NS, an, atts.getValue(i));
                continue;
            }
            String ans = atts.getURI(i);
            e.setAttributeNS(ans == null || ans.isEmpty() ? null : ans, an, atts.getValue(i));
        }
        current.appendChild(e);
        current = e;
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        current = current.getParentNode();
    }

    public void characters(char[] ch, int start, int length) throws SAXException {
        if (current == document) {
            return;
        }
        String s = new String(ch, start, length);
        Node last = current.getLastChild();
        if (inCData) {
            if (last != null && last.getNodeType() == Node.CDATA_SECTION_NODE) {
                ((Text) last).appendData(s);
            } else {
                current.appendChild(document.createCDATASection(s));
            }
        } else if (last != null && last.getNodeType() == Node.TEXT_NODE) {
            ((Text) last).appendData(s);
        } else {
            current.appendChild(document.createTextNode(s));
        }
    }

    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        characters(ch, start, length);
    }

    public void processingInstruction(String target, String data) throws SAXException {
        current.appendChild(document.createProcessingInstruction(target, data));
    }

    public void skippedEntity(String name) throws SAXException {
    }

    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        inDtd = true;
        document.appendChild(new DocumentTypeImpl(document, name, publicId, systemId, null));
    }

    public void endDTD() throws SAXException {
        inDtd = false;
    }

    public void startEntity(String name) throws SAXException {
    }

    public void endEntity(String name) throws SAXException {
    }

    public void startCDATA() throws SAXException {
        inCData = true;
        current.appendChild(document.createCDATASection(""));
    }

    public void endCDATA() throws SAXException {
        inCData = false;
    }

    public void comment(char[] ch, int start, int length) throws SAXException {
        if (!inDtd) {
            current.appendChild(document.createComment(new String(ch, start, length)));
        }
    }

    Document getDocument() {
        return document;
    }
}
