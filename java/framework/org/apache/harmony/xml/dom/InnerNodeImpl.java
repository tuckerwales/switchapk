package org.apache.harmony.xml.dom;

import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A node with children: Document, DocumentFragment, Element and EntityReference. Like Android,
 * getChildNodes() and getElementsByTagName() return snapshots, not live lists.
 */
public abstract class InnerNodeImpl extends LeafNodeImpl {
    final List<LeafNodeImpl> children = new ArrayList<LeafNodeImpl>();

    InnerNodeImpl(DocumentImpl document) {
        super(document);
    }

    public NodeList getChildNodes() {
        NodeListImpl list = new NodeListImpl();
        for (LeafNodeImpl node : children) {
            list.add(node);
        }
        return list;
    }

    public Node getFirstChild() {
        return children.isEmpty() ? null : children.get(0);
    }

    public Node getLastChild() {
        return children.isEmpty() ? null : children.get(children.size() - 1);
    }

    public boolean hasChildNodes() {
        return !children.isEmpty();
    }

    public Node appendChild(Node newChild) throws DOMException {
        return insertChildAt(newChild, children.size(), null);
    }

    public Node insertBefore(Node newChild, Node refChild) throws DOMException {
        if (refChild == null) {
            return insertChildAt(newChild, children.size(), null);
        }
        LeafNodeImpl ref = checkChild(refChild);
        if (newChild == refChild) {
            return newChild;
        }
        return insertChildAt(newChild, ref.index, null);
    }

    public Node replaceChild(Node newChild, Node oldChild) throws DOMException {
        LeafNodeImpl old = checkChild(oldChild);
        if (newChild == oldChild) {
            return oldChild;
        }
        insertChildAt(newChild, old.index, old);
        removeChild(old);
        return oldChild;
    }

    public Node removeChild(Node oldChild) throws DOMException {
        LeafNodeImpl child = checkChild(oldChild);
        children.remove(child.index);
        child.parent = null;
        reindex(child.index);
        return oldChild;
    }

    private LeafNodeImpl checkChild(Node node) {
        if (!(node instanceof LeafNodeImpl) || ((LeafNodeImpl) node).parent != this) {
            throw new DOMException(DOMException.NOT_FOUND_ERR, "NOT_FOUND_ERR: An attempt is made to reference a "
                    + "node in a context where it does not exist.");
        }
        return (LeafNodeImpl) node;
    }

    private void reindex(int from) {
        for (int i = from; i < children.size(); i++) {
            children.get(i).index = i;
        }
    }

    /** Whether a child of the given type may be added; replacing names a child that will be removed. */
    boolean canAppend(NodeImpl child, LeafNodeImpl replacing) {
        switch (child.getNodeType()) {
            case ELEMENT_NODE:
            case TEXT_NODE:
            case CDATA_SECTION_NODE:
            case COMMENT_NODE:
            case PROCESSING_INSTRUCTION_NODE:
            case ENTITY_REFERENCE_NODE:
                return true;
            default:
                return false;
        }
    }

    /** Inserts newChild at index (handles fragments and removal from the old parent). */
    Node insertChildAt(Node newChild, int index, LeafNodeImpl replacing) throws DOMException {
        if (newChild == null) {
            throw new NullPointerException("newChild == null");
        }
        if (!(newChild instanceof NodeImpl)) {
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "WRONG_DOCUMENT_ERR: A node is used in a "
                    + "different document than the one that created it.");
        }
        NodeImpl child = (NodeImpl) newChild;
        DocumentImpl myDoc = this instanceof DocumentImpl ? (DocumentImpl) this : document;
        if (child instanceof DocumentTypeImpl && child.document == null) {
            child.document = myDoc;
        }
        if (child.document != myDoc || child instanceof DocumentImpl) {
            if (child instanceof DocumentImpl) {
                throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, "HIERARCHY_REQUEST_ERR: An attempt was "
                        + "made to insert a node where it is not permitted.");
            }
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "WRONG_DOCUMENT_ERR: A node is used in a "
                    + "different document than the one that created it.");
        }
        if (child.getNodeType() == DOCUMENT_FRAGMENT_NODE) {
            InnerNodeImpl fragment = (InnerNodeImpl) child;
            for (LeafNodeImpl c : fragment.children) {
                if (!canAppend(c, replacing)) {
                    throw hierarchyError();
                }
            }
            List<LeafNodeImpl> moved = new ArrayList<LeafNodeImpl>(fragment.children);
            for (LeafNodeImpl c : moved) {
                fragment.removeChild(c);
                insertChildAt(c, index++, replacing);
            }
            return newChild;
        }
        for (Node n = this; n != null; n = n.getParentNode()) {
            if (n == child) {
                throw hierarchyError();
            }
        }
        if (!(child instanceof LeafNodeImpl) || !canAppend(child, replacing)) {
            throw hierarchyError();
        }
        LeafNodeImpl leaf = (LeafNodeImpl) child;
        if (leaf.parent != null) {
            InnerNodeImpl oldParent = leaf.parent;
            if (oldParent == this && leaf.index < index) {
                index--;
            }
            oldParent.removeChild(leaf);
        }
        children.add(index, leaf);
        leaf.parent = this;
        reindex(index);
        return newChild;
    }

    static DOMException hierarchyError() {
        return new DOMException(DOMException.HIERARCHY_REQUEST_ERR, "HIERARCHY_REQUEST_ERR: An attempt was made to "
                + "insert a node where it is not permitted.");
    }

    // ---- text ----------------------------------------------------------------------------

    public String getTextContent() throws DOMException {
        StringBuilder sb = new StringBuilder();
        appendTextContent(sb);
        return sb.toString();
    }

    void appendTextContent(StringBuilder sb) {
        for (LeafNodeImpl child : children) {
            child.appendTextContent(sb);
        }
    }

    public void setTextContent(String textContent) throws DOMException {
        while (!children.isEmpty()) {
            removeChild(children.get(children.size() - 1));
        }
        if (textContent != null && !textContent.isEmpty()) {
            appendChild(document.createTextNode(textContent));
        }
    }

    public void normalize() {
        TextImpl previous = null;
        for (int i = 0; i < children.size(); i++) {
            LeafNodeImpl child = children.get(i);
            if (child.getNodeType() == TEXT_NODE) {
                TextImpl text = (TextImpl) child;
                if (previous != null) {
                    previous.appendData(text.getData());
                    removeChild(text);
                    i--;
                } else if (text.getLength() == 0) {
                    removeChild(text);
                    i--;
                } else {
                    previous = text;
                }
            } else {
                previous = null;
                child.normalize();
            }
        }
    }

    // ---- searches -------------------------------------------------------------------------

    void collectElementsByTagName(String name, NodeListImpl out) {
        for (LeafNodeImpl child : children) {
            if (child.getNodeType() == ELEMENT_NODE) {
                ElementImpl e = (ElementImpl) child;
                if ("*".equals(name) || name.equals(e.getNodeName())) {
                    out.add(e);
                }
                e.collectElementsByTagName(name, out);
            } else if (child instanceof InnerNodeImpl) {
                ((InnerNodeImpl) child).collectElementsByTagName(name, out);
            }
        }
    }

    void collectElementsByTagNameNS(String namespaceURI, String localName, NodeListImpl out) {
        for (LeafNodeImpl child : children) {
            if (child.getNodeType() == ELEMENT_NODE) {
                ElementImpl e = (ElementImpl) child;
                if (e.getLocalName() != null
                        && ("*".equals(namespaceURI) || eq(emptyToNull(namespaceURI), e.getNamespaceURI()))
                        && ("*".equals(localName) || localName.equals(e.getLocalName()))) {
                    out.add(e);
                }
                e.collectElementsByTagNameNS(namespaceURI, localName, out);
            } else if (child instanceof InnerNodeImpl) {
                ((InnerNodeImpl) child).collectElementsByTagNameNS(namespaceURI, localName, out);
            }
        }
    }

    ElementImpl findElementById(String id) {
        for (LeafNodeImpl child : children) {
            if (child.getNodeType() == ELEMENT_NODE) {
                ElementImpl e = (ElementImpl) child;
                for (AttrImpl a : e.attributes) {
                    if (a.isId && id.equals(a.getValue())) {
                        return e;
                    }
                }
            }
            if (child instanceof InnerNodeImpl) {
                ElementImpl found = ((InnerNodeImpl) child).findElementById(id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Copies the children of this node into the target copy. */
    void cloneChildrenInto(InnerNodeImpl copy, DocumentImpl target) {
        for (LeafNodeImpl child : children) {
            copy.appendChild(child.cloneInto(target, true));
        }
    }
}
