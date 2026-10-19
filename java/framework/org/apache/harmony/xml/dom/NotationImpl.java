package org.apache.harmony.xml.dom;

import org.w3c.dom.Node;
import org.w3c.dom.Notation;

public final class NotationImpl extends NodeImpl implements Notation {
    private final String name;
    private final String publicId;
    private final String systemId;

    NotationImpl(DocumentImpl document, String name, String publicId, String systemId) {
        super(document);
        this.name = name;
        this.publicId = publicId;
        this.systemId = systemId;
    }

    public String getNodeName() {
        return name;
    }

    public short getNodeType() {
        return Node.NOTATION_NODE;
    }

    public String getTextContent() {
        return null;
    }

    public void setTextContent(String textContent) {
    }

    public String getPublicId() {
        return publicId;
    }

    public String getSystemId() {
        return systemId;
    }

    NodeImpl cloneInto(DocumentImpl target, boolean deep) {
        return new NotationImpl(target, name, publicId, systemId);
    }
}
