package javax.xml.transform.dom;

import javax.xml.transform.Source;
import org.w3c.dom.Node;

public class DOMSource implements Source {
    public static final String FEATURE = "http://javax.xml.transform.dom.DOMSource/feature";

    private Node node;
    private String systemID;

    public DOMSource() {
    }

    public DOMSource(Node n) {
        setNode(n);
    }

    public DOMSource(Node node, String systemID) {
        setNode(node);
        setSystemId(systemID);
    }

    public void setNode(Node node) {
        this.node = node;
    }

    public Node getNode() {
        return node;
    }

    public void setSystemId(String systemID) {
        this.systemID = systemID;
    }

    public String getSystemId() {
        return systemID;
    }

    public boolean isEmpty() {
        return systemID == null && (node == null || !node.hasChildNodes());
    }
}
