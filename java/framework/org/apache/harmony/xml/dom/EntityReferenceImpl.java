package org.apache.harmony.xml.dom;

import org.w3c.dom.EntityReference;
import org.w3c.dom.Node;

public final class EntityReferenceImpl extends InnerNodeImpl implements EntityReference {
    private final String name;

    EntityReferenceImpl(DocumentImpl document, String name) {
        super(document);
        this.name = name;
    }

    public String getNodeName() {
        return name;
    }

    public short getNodeType() {
        return Node.ENTITY_REFERENCE_NODE;
    }

    NodeImpl cloneInto(DocumentImpl target, boolean deep) {
        EntityReferenceImpl copy = new EntityReferenceImpl(target, name);
        cloneChildrenInto(copy, target);
        return copy;
    }
}
