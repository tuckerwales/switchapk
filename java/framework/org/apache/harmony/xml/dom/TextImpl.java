package org.apache.harmony.xml.dom;

import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

public class TextImpl extends CharacterDataImpl implements Text {
    public TextImpl(DocumentImpl document, String data) {
        super(document, data);
    }

    public String getNodeName() {
        return "#text";
    }

    public short getNodeType() {
        return Node.TEXT_NODE;
    }

    NodeImpl cloneInto(DocumentImpl target, boolean deep) {
        return new TextImpl(target, getData());
    }

    public final Text splitText(int offset) throws DOMException {
        if (offset < 0 || offset > getLength()) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "INDEX_SIZE_ERR: The index or size is negative, or "
                    + "greater than the allowed value.");
        }
        String data = getData();
        TextImpl newText = (TextImpl) cloneInto(document, false);
        newText.setData(data.substring(offset));
        setData(data.substring(0, offset));
        Node parent = getParentNode();
        if (parent != null) {
            parent.insertBefore(newText, getNextSibling());
        }
        return newText;
    }

    public final boolean isElementContentWhitespace() {
        return false;
    }

    private static boolean isTextual(Node n) {
        return n != null && (n.getNodeType() == TEXT_NODE || n.getNodeType() == CDATA_SECTION_NODE);
    }

    private Node firstLogicalText() {
        Node n = this;
        while (isTextual(n.getPreviousSibling())) {
            n = n.getPreviousSibling();
        }
        return n;
    }

    public final String getWholeText() {
        StringBuilder sb = new StringBuilder();
        for (Node n = firstLogicalText(); isTextual(n); n = n.getNextSibling()) {
            sb.append(n.getNodeValue());
        }
        return sb.toString();
    }

    public final Text replaceWholeText(String content) throws DOMException {
        Node parent = getParentNode();
        Node first = firstLogicalText();
        Text result = null;
        if (parent != null) {
            Node n = first;
            while (isTextual(n)) {
                Node next = n.getNextSibling();
                if (n == this && content != null && !content.isEmpty()) {
                    result = this;
                } else {
                    parent.removeChild(n);
                }
                n = next;
            }
        } else if (content != null && !content.isEmpty()) {
            result = this;
        }
        if (result != null) {
            setData(content);
        }
        return result;
    }
}
