package org.apache.harmony.xml.dom;

import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Node;

public final class DocumentFragmentImpl extends InnerNodeImpl implements DocumentFragment {
    DocumentFragmentImpl(DocumentImpl document) {
        super(document);
    }

    public String getNodeName() {
        return "#document-fragment";
    }

    public short getNodeType() {
        return Node.DOCUMENT_FRAGMENT_NODE;
    }

    NodeImpl cloneInto(DocumentImpl target, boolean deep) {
        DocumentFragmentImpl copy = new DocumentFragmentImpl(target);
        if (deep) {
            cloneChildrenInto(copy, target);
        }
        return copy;
    }
}
