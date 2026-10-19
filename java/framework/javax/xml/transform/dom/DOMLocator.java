package javax.xml.transform.dom;

public interface DOMLocator extends javax.xml.transform.SourceLocator {
    org.w3c.dom.Node getOriginatingNode();
}
