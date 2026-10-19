package org.apache.harmony.xml.dom;

import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.DOMException;
import org.w3c.dom.DocumentType;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public final class DocumentTypeImpl extends LeafNodeImpl implements DocumentType {
    private final String qualifiedName;
    private final String publicId;
    private final String systemId;
    private final String internalSubset;
    /** Framework-internal: filled by the document builder. */
    public final List<Node> entities = new ArrayList<Node>();
    final List<Node> notations = new ArrayList<Node>();

    public DocumentTypeImpl(DocumentImpl document, String qualifiedName, String publicId, String systemId,
            String internalSubset) {
        super(document);
        this.qualifiedName = qualifiedName;
        this.publicId = publicId;
        this.systemId = systemId;
        this.internalSubset = internalSubset;
    }

    public String getNodeName() {
        return qualifiedName;
    }

    public short getNodeType() {
        return Node.DOCUMENT_TYPE_NODE;
    }

    public String getTextContent() {
        return null;
    }

    public void setTextContent(String textContent) {
    }

    public String getName() {
        return qualifiedName;
    }

    public NamedNodeMap getEntities() {
        return new ReadOnlyMap(entities);
    }

    public NamedNodeMap getNotations() {
        return new ReadOnlyMap(notations);
    }

    public String getPublicId() {
        return publicId;
    }

    public String getSystemId() {
        return systemId;
    }

    public String getInternalSubset() {
        return internalSubset;
    }

    NodeImpl cloneInto(DocumentImpl target, boolean deep) {
        DocumentTypeImpl copy = new DocumentTypeImpl(target, qualifiedName, publicId, systemId, internalSubset);
        for (Node e : entities) {
            copy.entities.add(((NodeImpl) e).cloneInto(target, deep));
        }
        for (Node n : notations) {
            copy.notations.add(((NodeImpl) n).cloneInto(target, deep));
        }
        return copy;
    }

    static final class ReadOnlyMap implements NamedNodeMap {
        private final List<Node> nodes;

        ReadOnlyMap(List<Node> nodes) {
            this.nodes = nodes;
        }

        private static DOMException readOnly() {
            return new DOMException(DOMException.NO_MODIFICATION_ALLOWED_ERR, "NO_MODIFICATION_ALLOWED_ERR: An "
                    + "attempt is made to modify an object where modifications are not allowed.");
        }

        public Node getNamedItem(String name) {
            for (Node n : nodes) {
                if (n.getNodeName().equals(name)) {
                    return n;
                }
            }
            return null;
        }

        public Node setNamedItem(Node arg) {
            throw readOnly();
        }

        public Node removeNamedItem(String name) {
            throw readOnly();
        }

        public Node item(int index) {
            return index < 0 || index >= nodes.size() ? null : nodes.get(index);
        }

        public int getLength() {
            return nodes.size();
        }

        public Node getNamedItemNS(String namespaceURI, String localName) {
            return namespaceURI == null ? getNamedItem(localName) : null;
        }

        public Node setNamedItemNS(Node arg) {
            throw readOnly();
        }

        public Node removeNamedItemNS(String namespaceURI, String localName) {
            throw readOnly();
        }
    }
}
