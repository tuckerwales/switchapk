package org.apache.harmony.xml.dom;

import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.TypeInfo;

public final class AttrImpl extends NodeImpl implements Attr {
    ElementImpl ownerElement;
    boolean isId;

    boolean namespaceAware;
    String namespaceURI;
    String prefix;
    String localName;

    private String value = "";

    /** A namespace-aware attribute. */
    AttrImpl(DocumentImpl document, String namespaceURI, String qualifiedName) {
        super(document);
        setNameNS(namespaceURI, qualifiedName);
    }

    /** A DOM Level 1 attribute. */
    AttrImpl(DocumentImpl document, String name) {
        super(document);
        setName(name);
    }

    void setNameNS(String namespaceURI, String qualifiedName) {
        namespaceURI = emptyToNull(namespaceURI);
        String[] parts = splitQualifiedName(namespaceURI, qualifiedName, true);
        this.namespaceAware = true;
        this.namespaceURI = namespaceURI;
        this.prefix = parts[0];
        this.localName = parts[1];
    }

    void setName(String name) {
        checkName(name);
        this.namespaceAware = false;
        this.namespaceURI = null;
        this.prefix = null;
        this.localName = name;
    }

    public String getLocalName() {
        return namespaceAware ? localName : null;
    }

    public String getName() {
        return prefix != null ? prefix + ":" + localName : localName;
    }

    public String getNamespaceURI() {
        return namespaceURI;
    }

    public String getNodeName() {
        return getName();
    }

    public short getNodeType() {
        return Node.ATTRIBUTE_NODE;
    }

    public String getNodeValue() {
        return getValue();
    }

    public void setNodeValue(String value) throws DOMException {
        setValue(value);
    }

    public String getTextContent() {
        return value;
    }

    public void setTextContent(String textContent) {
        setValue(textContent);
    }

    public Element getOwnerElement() {
        return ownerElement;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        if (!namespaceAware) {
            throw new DOMException(DOMException.NAMESPACE_ERR, "NAMESPACE_ERR: An attempt is made to create or "
                    + "change an object in a way which is incorrect with regard to namespaces.");
        }
        prefix = emptyToNull(prefix);
        if (prefix != null && !isXmlName(prefix, false)) {
            checkName(prefix);
            throw new DOMException(DOMException.NAMESPACE_ERR, "NAMESPACE_ERR: invalid prefix");
        }
        checkPrefix(namespaceURI, prefix, localName, true);
        this.prefix = prefix;
    }

    public boolean getSpecified() {
        return true;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) throws DOMException {
        this.value = value == null ? "" : value;
    }

    public TypeInfo getSchemaTypeInfo() {
        return NULL_TYPE_INFO;
    }

    public boolean isId() {
        return isId;
    }

    NodeImpl cloneInto(DocumentImpl target, boolean deep) {
        AttrImpl copy = namespaceAware ? new AttrImpl(target, namespaceURI, getName()) : new AttrImpl(target, localName);
        copy.value = value;
        return copy;
    }
}
