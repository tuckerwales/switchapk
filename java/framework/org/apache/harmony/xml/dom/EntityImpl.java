package org.apache.harmony.xml.dom;

import org.w3c.dom.Entity;
import org.w3c.dom.Node;

/** An entity declared in the internal DTD subset; the replacement text is its text content. */
public final class EntityImpl extends NodeImpl implements Entity {
    private final String name;
    private final String publicId;
    private final String systemId;
    private final String notationName;
    private final String value;

    /** Framework-internal: used by the document builder. */
    public EntityImpl(DocumentImpl document, String name, String publicId, String systemId, String notationName,
            String value) {
        super(document);
        this.name = name;
        this.publicId = publicId;
        this.systemId = systemId;
        this.notationName = notationName;
        this.value = value;
    }

    public String getNodeName() {
        return name;
    }

    public short getNodeType() {
        return Node.ENTITY_NODE;
    }

    public String getTextContent() {
        // The replacement text is not exposed as children, so (as on the JDK) the content is empty.
        return "";
    }

    /** Framework-internal: the replacement text from the declaration. */
    public String getReplacementText() {
        return value;
    }

    public void setTextContent(String textContent) {
    }

    public String getPublicId() {
        return publicId;
    }

    public String getSystemId() {
        return systemId;
    }

    public String getNotationName() {
        return notationName;
    }

    public String getInputEncoding() {
        return null;
    }

    public String getXmlEncoding() {
        return null;
    }

    public String getXmlVersion() {
        return null;
    }

    NodeImpl cloneInto(DocumentImpl target, boolean deep) {
        return new EntityImpl(target, name, publicId, systemId, notationName, value);
    }
}
