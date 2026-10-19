package org.apache.harmony.xml.transform;

import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;

/** Replays a DOM tree as SAX events (namespaces on, namespace-prefixes off). */
final class DomToSax {
    private final ContentHandler handler;
    private final LexicalHandler lexical;

    DomToSax(ContentHandler handler, LexicalHandler lexical) {
        this.handler = handler;
        this.lexical = lexical;
    }

    void run(Node node) throws SAXException {
        handler.startDocument();
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            for (Node c = node.getFirstChild(); c != null; c = c.getNextSibling()) {
                walk(c);
            }
        } else {
            walk(node);
        }
        handler.endDocument();
    }

    private void walk(Node node) throws SAXException {
        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE: {
                List<String> prefixes = new ArrayList<String>();
                AttributesImpl atts = new AttributesImpl();
                NamedNodeMap map = node.getAttributes();
                for (int i = 0; i < map.getLength(); i++) {
                    Attr a = (Attr) map.item(i);
                    String name = a.getNodeName();
                    if (name.equals("xmlns") || name.startsWith("xmlns:")) {
                        String prefix = name.length() == 5 ? "" : name.substring(6);
                        prefixes.add(prefix);
                        handler.startPrefixMapping(prefix, a.getValue());
                        continue;
                    }
                    String uri = a.getNamespaceURI();
                    String local = a.getLocalName() != null ? a.getLocalName() : name;
                    atts.addAttribute(uri == null ? "" : uri, local, name, "CDATA", a.getValue());
                }
                String uri = node.getNamespaceURI();
                String local = node.getLocalName() != null ? node.getLocalName() : node.getNodeName();
                handler.startElement(uri == null ? "" : uri, local, node.getNodeName(), atts);
                for (Node c = node.getFirstChild(); c != null; c = c.getNextSibling()) {
                    walk(c);
                }
                handler.endElement(uri == null ? "" : uri, local, node.getNodeName());
                for (int i = prefixes.size() - 1; i >= 0; i--) {
                    handler.endPrefixMapping(prefixes.get(i));
                }
                break;
            }
            case Node.TEXT_NODE: {
                char[] ch = node.getNodeValue().toCharArray();
                handler.characters(ch, 0, ch.length);
                break;
            }
            case Node.CDATA_SECTION_NODE: {
                char[] ch = node.getNodeValue().toCharArray();
                if (lexical != null) {
                    lexical.startCDATA();
                }
                handler.characters(ch, 0, ch.length);
                if (lexical != null) {
                    lexical.endCDATA();
                }
                break;
            }
            case Node.COMMENT_NODE:
                if (lexical != null) {
                    char[] ch = node.getNodeValue().toCharArray();
                    lexical.comment(ch, 0, ch.length);
                }
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                handler.processingInstruction(node.getNodeName(), node.getNodeValue());
                break;
            case Node.ENTITY_REFERENCE_NODE:
            case Node.DOCUMENT_FRAGMENT_NODE:
                for (Node c = node.getFirstChild(); c != null; c = c.getNextSibling()) {
                    walk(c);
                }
                break;
            default:
                break;
        }
    }
}
