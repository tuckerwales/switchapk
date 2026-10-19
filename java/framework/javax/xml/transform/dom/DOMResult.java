package javax.xml.transform.dom;

import javax.xml.transform.Result;
import org.w3c.dom.Node;

public class DOMResult implements Result {
    public static final String FEATURE = "http://javax.xml.transform.dom.DOMResult/feature";

    private Node node;
    private Node nextSibling;
    private String systemId;

    public DOMResult() {
    }

    public DOMResult(Node node) {
        setNode(node);
    }

    public DOMResult(Node node, String systemId) {
        setNode(node);
        setSystemId(systemId);
    }

    public DOMResult(Node node, Node nextSibling) {
        this(node, nextSibling, null);
    }

    public DOMResult(Node node, Node nextSibling, String systemId) {
        if (nextSibling != null) {
            if (node == null) {
                throw new IllegalArgumentException("Cannot create a DOMResult when the nextSibling is contained by "
                        + "the \"null\" node.");
            }
            if ((node.compareDocumentPosition(nextSibling) & Node.DOCUMENT_POSITION_CONTAINED_BY) == 0) {
                throw new IllegalArgumentException("Cannot create a DOMResult when the nextSibling is not contained "
                        + "by the node.");
            }
        }
        this.node = node;
        this.nextSibling = nextSibling;
        this.systemId = systemId;
    }

    public void setNode(Node node) {
        if (nextSibling != null) {
            if (node == null) {
                throw new IllegalStateException("Cannot create a DOMResult when the nextSibling is contained by the "
                        + "\"null\" node.");
            }
            if ((node.compareDocumentPosition(nextSibling) & Node.DOCUMENT_POSITION_CONTAINED_BY) == 0) {
                throw new IllegalArgumentException("Cannot create a DOMResult when the nextSibling is not contained "
                        + "by the node.");
            }
        }
        this.node = node;
    }

    public Node getNode() {
        return node;
    }

    public void setNextSibling(Node nextSibling) {
        if (nextSibling != null) {
            if (node == null) {
                throw new IllegalStateException("Cannot create a DOMResult when the nextSibling is contained by the "
                        + "\"null\" node.");
            }
            if ((node.compareDocumentPosition(nextSibling) & Node.DOCUMENT_POSITION_CONTAINED_BY) == 0) {
                throw new IllegalArgumentException("Cannot create a DOMResult when the nextSibling is not contained "
                        + "by the node.");
            }
        }
        this.nextSibling = nextSibling;
    }

    public Node getNextSibling() {
        return nextSibling;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public String getSystemId() {
        return systemId;
    }
}
