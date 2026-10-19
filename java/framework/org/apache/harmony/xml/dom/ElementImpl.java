package org.apache.harmony.xml.dom;

import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.TypeInfo;

public class ElementImpl extends InnerNodeImpl implements Element {
    boolean namespaceAware;
    String namespaceURI;
    String prefix;
    /** The local name, or for a DOM Level 1 element the whole tag name. */
    String localName;

    final List<AttrImpl> attributes = new ArrayList<AttrImpl>();

    ElementImpl(DocumentImpl document, String namespaceURI, String qualifiedName) {
        super(document);
        setNameNS(namespaceURI, qualifiedName);
    }

    ElementImpl(DocumentImpl document, String name) {
        super(document);
        setName(name);
    }

    void setNameNS(String namespaceURI, String qualifiedName) {
        namespaceURI = emptyToNull(namespaceURI);
        String[] parts = splitQualifiedName(namespaceURI, qualifiedName, false);
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

    public String getNodeName() {
        return getTagName();
    }

    public short getNodeType() {
        return Node.ELEMENT_NODE;
    }

    public String getTagName() {
        return prefix != null ? prefix + ":" + localName : localName;
    }

    public String getLocalName() {
        return namespaceAware ? localName : null;
    }

    public String getNamespaceURI() {
        return namespaceURI;
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
        checkPrefix(namespaceURI, prefix, localName, false);
        this.prefix = prefix;
    }

    boolean canAppend(NodeImpl child, LeafNodeImpl replacing) {
        return super.canAppend(child, replacing);
    }

    // ---- attributes -----------------------------------------------------------------------

    private int indexOfAttribute(String name) {
        for (int i = 0; i < attributes.size(); i++) {
            if (attributes.get(i).getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private int indexOfAttributeNS(String namespaceURI, String localName) {
        namespaceURI = emptyToNull(namespaceURI);
        for (int i = 0; i < attributes.size(); i++) {
            AttrImpl a = attributes.get(i);
            if (namespaceURI == null) {
                if (a.namespaceURI == null && localName.equals(a.localName)) {
                    return i;
                }
            } else if (a.namespaceAware && namespaceURI.equals(a.namespaceURI) && localName.equals(a.localName)) {
                return i;
            }
        }
        return -1;
    }

    public String getAttribute(String name) {
        int i = indexOfAttribute(name);
        return i < 0 ? "" : attributes.get(i).getValue();
    }

    public void setAttribute(String name, String value) throws DOMException {
        int i = indexOfAttribute(name);
        if (i >= 0) {
            attributes.get(i).setValue(value);
            return;
        }
        AttrImpl attr = new AttrImpl(document, name);
        attr.setValue(value);
        attr.ownerElement = this;
        attributes.add(attr);
    }

    public void removeAttribute(String name) throws DOMException {
        int i = indexOfAttribute(name);
        if (i >= 0) {
            attributes.remove(i).ownerElement = null;
        }
    }

    public Attr getAttributeNode(String name) {
        int i = indexOfAttribute(name);
        return i < 0 ? null : attributes.get(i);
    }

    private AttrImpl checkNewAttr(Attr newAttr) {
        if (!(newAttr instanceof AttrImpl) || ((AttrImpl) newAttr).document != document) {
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "WRONG_DOCUMENT_ERR: A node is used in a "
                    + "different document than the one that created it.");
        }
        AttrImpl attr = (AttrImpl) newAttr;
        if (attr.ownerElement != null && attr.ownerElement != this) {
            throw new DOMException(DOMException.INUSE_ATTRIBUTE_ERR, "INUSE_ATTRIBUTE_ERR: An attempt is made to "
                    + "add an attribute that is already in use elsewhere.");
        }
        return attr;
    }

    public Attr setAttributeNode(Attr newAttr) throws DOMException {
        AttrImpl attr = checkNewAttr(newAttr);
        if (attr.ownerElement == this) {
            return attr;
        }
        int i = indexOfAttribute(attr.getName());
        AttrImpl old = null;
        attr.ownerElement = this;
        if (i >= 0) {
            old = attributes.set(i, attr);
            old.ownerElement = null;
        } else {
            attributes.add(attr);
        }
        return old;
    }

    public Attr removeAttributeNode(Attr oldAttr) throws DOMException {
        int i = attributes.indexOf(oldAttr);
        if (i < 0) {
            throw new DOMException(DOMException.NOT_FOUND_ERR, "NOT_FOUND_ERR: An attempt is made to reference a "
                    + "node in a context where it does not exist.");
        }
        AttrImpl attr = attributes.remove(i);
        attr.ownerElement = null;
        return attr;
    }

    public NodeList getElementsByTagName(String name) {
        NodeListImpl list = new NodeListImpl();
        collectElementsByTagName(name, list);
        return list;
    }

    public String getAttributeNS(String namespaceURI, String localName) throws DOMException {
        int i = indexOfAttributeNS(namespaceURI, localName);
        return i < 0 ? "" : attributes.get(i).getValue();
    }

    public void setAttributeNS(String namespaceURI, String qualifiedName, String value) throws DOMException {
        namespaceURI = emptyToNull(namespaceURI);
        String[] parts = splitQualifiedName(namespaceURI, qualifiedName, true);
        int i = indexOfAttributeNS(namespaceURI, parts[1]);
        if (i >= 0) {
            AttrImpl a = attributes.get(i);
            if (a.namespaceAware) {
                a.prefix = parts[0];
            }
            a.setValue(value);
            return;
        }
        AttrImpl attr = new AttrImpl(document, namespaceURI, qualifiedName);
        attr.setValue(value);
        attr.ownerElement = this;
        attributes.add(attr);
    }

    public void removeAttributeNS(String namespaceURI, String localName) throws DOMException {
        int i = indexOfAttributeNS(namespaceURI, localName);
        if (i >= 0) {
            attributes.remove(i).ownerElement = null;
        }
    }

    public Attr getAttributeNodeNS(String namespaceURI, String localName) throws DOMException {
        int i = indexOfAttributeNS(namespaceURI, localName);
        return i < 0 ? null : attributes.get(i);
    }

    public Attr setAttributeNodeNS(Attr newAttr) throws DOMException {
        AttrImpl attr = checkNewAttr(newAttr);
        if (attr.ownerElement == this) {
            return attr;
        }
        int i = attr.namespaceAware ? indexOfAttributeNS(attr.namespaceURI, attr.localName)
                : indexOfAttribute(attr.getName());
        AttrImpl old = null;
        attr.ownerElement = this;
        if (i >= 0) {
            old = attributes.set(i, attr);
            old.ownerElement = null;
        } else {
            attributes.add(attr);
        }
        return old;
    }

    public NodeList getElementsByTagNameNS(String namespaceURI, String localName) throws DOMException {
        NodeListImpl list = new NodeListImpl();
        collectElementsByTagNameNS(namespaceURI, localName, list);
        return list;
    }

    public boolean hasAttribute(String name) {
        return indexOfAttribute(name) >= 0;
    }

    public boolean hasAttributeNS(String namespaceURI, String localName) throws DOMException {
        return indexOfAttributeNS(namespaceURI, localName) >= 0;
    }

    public boolean hasAttributes() {
        return !attributes.isEmpty();
    }

    public TypeInfo getSchemaTypeInfo() {
        return NULL_TYPE_INFO;
    }

    private static DOMException notFound() {
        return new DOMException(DOMException.NOT_FOUND_ERR, "NOT_FOUND_ERR: An attempt is made to reference a node "
                + "in a context where it does not exist.");
    }

    public void setIdAttribute(String name, boolean isId) throws DOMException {
        int i = indexOfAttribute(name);
        if (i < 0) {
            throw notFound();
        }
        attributes.get(i).isId = isId;
    }

    public void setIdAttributeNS(String namespaceURI, String localName, boolean isId) throws DOMException {
        int i = indexOfAttributeNS(namespaceURI, localName);
        if (i < 0) {
            throw notFound();
        }
        attributes.get(i).isId = isId;
    }

    public void setIdAttributeNode(Attr idAttr, boolean isId) throws DOMException {
        if (!attributes.contains(idAttr)) {
            throw notFound();
        }
        ((AttrImpl) idAttr).isId = isId;
    }

    public NamedNodeMap getAttributes() {
        return new AttributeMap();
    }

    NodeImpl cloneInto(DocumentImpl target, boolean deep) {
        ElementImpl copy = namespaceAware ? new ElementImpl(target, namespaceURI, getTagName())
                : new ElementImpl(target, localName);
        for (AttrImpl a : attributes) {
            AttrImpl ac = (AttrImpl) a.cloneInto(target, true);
            ac.ownerElement = copy;
            ac.isId = a.isId;
            copy.attributes.add(ac);
        }
        if (deep) {
            cloneChildrenInto(copy, target);
        }
        return copy;
    }

    /** Live view of the attribute list. */
    final class AttributeMap implements NamedNodeMap {
        public Node getNamedItem(String name) {
            return getAttributeNode(name);
        }

        public Node setNamedItem(Node arg) throws DOMException {
            if (!(arg instanceof Attr)) {
                throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, "HIERARCHY_REQUEST_ERR: An attempt was "
                        + "made to insert a node where it is not permitted.");
            }
            return setAttributeNode((Attr) arg);
        }

        public Node removeNamedItem(String name) throws DOMException {
            Attr a = getAttributeNode(name);
            if (a == null) {
                throw notFound();
            }
            return removeAttributeNode(a);
        }

        public Node item(int index) {
            return index < 0 || index >= attributes.size() ? null : attributes.get(index);
        }

        public int getLength() {
            return attributes.size();
        }

        public Node getNamedItemNS(String namespaceURI, String localName) throws DOMException {
            return getAttributeNodeNS(namespaceURI, localName);
        }

        public Node setNamedItemNS(Node arg) throws DOMException {
            if (!(arg instanceof Attr)) {
                throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, "HIERARCHY_REQUEST_ERR: An attempt was "
                        + "made to insert a node where it is not permitted.");
            }
            return setAttributeNodeNS((Attr) arg);
        }

        public Node removeNamedItemNS(String namespaceURI, String localName) throws DOMException {
            Attr a = getAttributeNodeNS(namespaceURI, localName);
            if (a == null) {
                throw notFound();
            }
            return removeAttributeNode(a);
        }
    }
}
