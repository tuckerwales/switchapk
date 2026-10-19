package org.apache.harmony.xml.dom;

import org.w3c.dom.Node;

/** A node that can be the child of an {@link InnerNodeImpl}. */
public abstract class LeafNodeImpl extends NodeImpl {
    InnerNodeImpl parent;
    /** Position in parent.children; only valid while parent != null. */
    int index;

    LeafNodeImpl(DocumentImpl document) {
        super(document);
    }

    public Node getParentNode() {
        return parent;
    }

    public Node getPreviousSibling() {
        if (parent == null || index == 0) {
            return null;
        }
        return parent.children.get(index - 1);
    }

    public Node getNextSibling() {
        if (parent == null || index + 1 >= parent.children.size()) {
            return null;
        }
        return parent.children.get(index + 1);
    }
}
