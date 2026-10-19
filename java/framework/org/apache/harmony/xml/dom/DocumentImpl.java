package org.apache.harmony.xml.dom;

import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Comment;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.EntityReference;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.w3c.dom.UserDataHandler;

public final class DocumentImpl extends InnerNodeImpl implements Document {
    private final DOMImplementation domImplementation;
    private DOMConfigurationImpl domConfiguration;

    private String documentUri;
    private String inputEncoding;
    private String xmlEncoding;
    private String xmlVersion = "1.0";
    private boolean xmlStandalone = false;
    private boolean strictErrorChecking = true;

    public DocumentImpl(DOMImplementationImpl impl, String namespaceURI, String qualifiedName, DocumentType doctype,
            String inputEncoding) {
        super(null);
        this.document = this;
        this.domImplementation = impl;
        this.inputEncoding = inputEncoding;
        if (doctype != null) {
            if (doctype.getOwnerDocument() != null || doctype.getParentNode() != null) {
                throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "WRONG_DOCUMENT_ERR: The doctype is already "
                        + "used by another document.");
            }
            appendChild(doctype);
        }
        if (qualifiedName != null) {
            appendChild(createElementNS(namespaceURI, qualifiedName));
        } else if (namespaceURI != null && !namespaceURI.isEmpty()) {
            throw new DOMException(DOMException.NAMESPACE_ERR, "NAMESPACE_ERR: namespace without a qualified name");
        }
    }

    public String getNodeName() {
        return "#document";
    }

    public short getNodeType() {
        return Node.DOCUMENT_NODE;
    }

    public String getTextContent() {
        return null;
    }

    public void setTextContent(String textContent) {
    }

    boolean canAppend(NodeImpl child, LeafNodeImpl replacing) {
        switch (child.getNodeType()) {
            case ELEMENT_NODE: {
                Element existing = getDocumentElement();
                return existing == null || existing == replacing || existing == child;
            }
            case DOCUMENT_TYPE_NODE: {
                DocumentType existing = getDoctype();
                return existing == null || existing == replacing || existing == child;
            }
            case COMMENT_NODE:
            case PROCESSING_INSTRUCTION_NODE:
                return true;
            default:
                return false;
        }
    }

    NodeImpl cloneInto(DocumentImpl target, boolean deep) {
        DocumentImpl copy = new DocumentImpl((DOMImplementationImpl) domImplementation, null, null, null,
                inputEncoding);
        copy.documentUri = documentUri;
        copy.xmlEncoding = xmlEncoding;
        copy.xmlVersion = xmlVersion;
        copy.xmlStandalone = xmlStandalone;
        copy.strictErrorChecking = strictErrorChecking;
        if (deep) {
            for (LeafNodeImpl child : children) {
                LeafNodeImpl childCopy = (LeafNodeImpl) child.cloneInto(copy, true);
                copy.appendChild(childCopy);
                // The reference implementation copies a document by importing its nodes.
                notifyImported(child, childCopy);
            }
        }
        return copy;
    }

    private static void notifyImported(NodeImpl original, NodeImpl copy) {
        notifyUserDataHandlers(UserDataHandler.NODE_IMPORTED, original, copy);
        if (original instanceof InnerNodeImpl && copy instanceof InnerNodeImpl) {
            List<LeafNodeImpl> a = ((InnerNodeImpl) original).children;
            List<LeafNodeImpl> b = ((InnerNodeImpl) copy).children;
            for (int i = 0; i < a.size() && i < b.size(); i++) {
                notifyImported(a.get(i), b.get(i));
            }
        }
    }

    public DocumentType getDoctype() {
        for (LeafNodeImpl child : children) {
            if (child instanceof DocumentType) {
                return (DocumentType) child;
            }
        }
        return null;
    }

    public DOMImplementation getImplementation() {
        return domImplementation;
    }

    public Element getDocumentElement() {
        for (LeafNodeImpl child : children) {
            if (child instanceof Element) {
                return (Element) child;
            }
        }
        return null;
    }

    public Element createElement(String tagName) throws DOMException {
        return new ElementImpl(this, tagName);
    }

    public DocumentFragment createDocumentFragment() {
        return new DocumentFragmentImpl(this);
    }

    public Text createTextNode(String data) {
        return new TextImpl(this, data);
    }

    public Comment createComment(String data) {
        return new CommentImpl(this, data);
    }

    public CDATASection createCDATASection(String data) throws DOMException {
        return new CDATASectionImpl(this, data);
    }

    public ProcessingInstruction createProcessingInstruction(String target, String data) throws DOMException {
        checkName(target);
        return new ProcessingInstructionImpl(this, target, data);
    }

    public Attr createAttribute(String name) throws DOMException {
        return new AttrImpl(this, name);
    }

    public EntityReference createEntityReference(String name) throws DOMException {
        checkName(name);
        EntityReferenceImpl ref = new EntityReferenceImpl(this, name);
        DocumentType doctype = getDoctype();
        if (doctype != null) {
            Node entity = doctype.getEntities().getNamedItem(name);
            String value = entity instanceof EntityImpl ? ((EntityImpl) entity).getReplacementText() : null;
            if (value != null && !value.isEmpty()) {
                ref.children.add(new TextImpl(this, value));
                ref.children.get(0).parent = ref;
            }
        }
        return ref;
    }

    public NodeList getElementsByTagName(String tagname) {
        NodeListImpl list = new NodeListImpl();
        collectElementsByTagName(tagname, list);
        return list;
    }

    public NodeList getElementsByTagNameNS(String namespaceURI, String localName) {
        NodeListImpl list = new NodeListImpl();
        collectElementsByTagNameNS(namespaceURI, localName, list);
        return list;
    }

    public Element getElementById(String elementId) {
        return findElementById(elementId);
    }

    public Element createElementNS(String namespaceURI, String qualifiedName) throws DOMException {
        return new ElementImpl(this, namespaceURI, qualifiedName);
    }

    public Attr createAttributeNS(String namespaceURI, String qualifiedName) throws DOMException {
        return new AttrImpl(this, namespaceURI, qualifiedName);
    }

    // ---- import / adopt / rename -----------------------------------------------------------

    public Node importNode(Node importedNode, boolean deep) throws DOMException {
        NodeImpl copy = importCopy(importedNode, deep);
        if (importedNode instanceof NodeImpl) {
            notifyUserDataHandlers(UserDataHandler.NODE_IMPORTED, (NodeImpl) importedNode, copy);
        }
        return copy;
    }

    private NodeImpl importCopy(Node node, boolean deep) {
        switch (node.getNodeType()) {
            case ATTRIBUTE_NODE: {
                Attr a = (Attr) node;
                AttrImpl copy = a.getLocalName() != null ? new AttrImpl(this, a.getNamespaceURI(), a.getName())
                        : new AttrImpl(this, a.getName());
                copy.setValue(a.getValue());
                return copy;
            }
            case ELEMENT_NODE: {
                Element e = (Element) node;
                ElementImpl copy = e.getLocalName() != null
                        ? new ElementImpl(this, e.getNamespaceURI(), e.getTagName())
                        : new ElementImpl(this, e.getTagName());
                NamedNodeMap attrs = e.getAttributes();
                for (int i = 0; i < attrs.getLength(); i++) {
                    AttrImpl ac = (AttrImpl) importCopy(attrs.item(i), true);
                    ac.ownerElement = copy;
                    copy.attributes.add(ac);
                }
                if (deep) {
                    importChildren(node, copy);
                }
                return copy;
            }
            case TEXT_NODE:
                return new TextImpl(this, node.getNodeValue());
            case CDATA_SECTION_NODE:
                return new CDATASectionImpl(this, node.getNodeValue());
            case COMMENT_NODE:
                return new CommentImpl(this, node.getNodeValue());
            case PROCESSING_INSTRUCTION_NODE:
                return new ProcessingInstructionImpl(this, node.getNodeName(), node.getNodeValue());
            case ENTITY_REFERENCE_NODE:
                return (NodeImpl) createEntityReference(node.getNodeName());
            case DOCUMENT_FRAGMENT_NODE: {
                DocumentFragmentImpl copy = new DocumentFragmentImpl(this);
                if (deep) {
                    importChildren(node, copy);
                }
                return copy;
            }
            case ENTITY_NODE: {
                org.w3c.dom.Entity e = (org.w3c.dom.Entity) node;
                return new EntityImpl(this, e.getNodeName(), e.getPublicId(), e.getSystemId(), e.getNotationName(),
                        e.getTextContent());
            }
            case NOTATION_NODE: {
                org.w3c.dom.Notation n = (org.w3c.dom.Notation) node;
                return new NotationImpl(this, n.getNodeName(), n.getPublicId(), n.getSystemId());
            }
            default:
                throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "NOT_SUPPORTED_ERR: The implementation does "
                        + "not support the requested type of object or operation.");
        }
    }

    private void importChildren(Node from, InnerNodeImpl to) {
        for (Node c = from.getFirstChild(); c != null; c = c.getNextSibling()) {
            to.appendChild(importCopy(c, true));
        }
    }

    public Node adoptNode(Node source) throws DOMException {
        if (!(source instanceof NodeImpl)) {
            return null;
        }
        NodeImpl node = (NodeImpl) source;
        switch (node.getNodeType()) {
            case DOCUMENT_NODE:
            case DOCUMENT_TYPE_NODE:
            case ENTITY_NODE:
            case NOTATION_NODE:
                throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "NOT_SUPPORTED_ERR: The implementation does "
                        + "not support the requested type of object or operation.");
            case ATTRIBUTE_NODE: {
                AttrImpl attr = (AttrImpl) node;
                if (attr.ownerElement != null) {
                    attr.ownerElement.removeAttributeNode(attr);
                }
                break;
            }
            default: {
                LeafNodeImpl leaf = (LeafNodeImpl) node;
                if (leaf.parent != null) {
                    leaf.parent.removeChild(leaf);
                }
                break;
            }
        }
        setDocumentRecursively(node, this);
        notifyUserDataHandlers(UserDataHandler.NODE_ADOPTED, node, null);
        return node;
    }

    private static void setDocumentRecursively(NodeImpl node, DocumentImpl doc) {
        node.document = doc;
        if (node instanceof ElementImpl) {
            for (AttrImpl a : ((ElementImpl) node).attributes) {
                a.document = doc;
            }
        }
        if (node instanceof InnerNodeImpl) {
            for (LeafNodeImpl child : ((InnerNodeImpl) node).children) {
                setDocumentRecursively(child, doc);
            }
        }
    }

    public Node renameNode(Node node, String namespaceURI, String qualifiedName) throws DOMException {
        if (!(node instanceof NodeImpl) || ((NodeImpl) node).document != this) {
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "WRONG_DOCUMENT_ERR: A node is used in a "
                    + "different document than the one that created it.");
        }
        if (node instanceof ElementImpl) {
            ((ElementImpl) node).setNameNS(namespaceURI, qualifiedName);
        } else if (node instanceof AttrImpl) {
            ((AttrImpl) node).setNameNS(namespaceURI, qualifiedName);
        } else {
            throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "NOT_SUPPORTED_ERR: The implementation does not "
                    + "support the requested type of object or operation.");
        }
        notifyUserDataHandlers(UserDataHandler.NODE_RENAMED, (NodeImpl) node, null);
        return node;
    }

    // ---- document properties --------------------------------------------------------------

    public String getInputEncoding() {
        return inputEncoding;
    }

    /** Framework-internal: set by the parser. */
    public void setInputEncoding(String inputEncoding) {
        this.inputEncoding = inputEncoding;
    }

    public String getXmlEncoding() {
        return xmlEncoding;
    }

    /** Framework-internal: set by the parser from the XML declaration. */
    public void setXmlEncoding(String xmlEncoding) {
        this.xmlEncoding = xmlEncoding;
    }

    public boolean getXmlStandalone() {
        return xmlStandalone;
    }

    public void setXmlStandalone(boolean xmlStandalone) throws DOMException {
        this.xmlStandalone = xmlStandalone;
    }

    public String getXmlVersion() {
        return xmlVersion;
    }

    public void setXmlVersion(String xmlVersion) throws DOMException {
        if (!"1.0".equals(xmlVersion) && !"1.1".equals(xmlVersion)) {
            throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "NOT_SUPPORTED_ERR: XML version " + xmlVersion);
        }
        this.xmlVersion = xmlVersion;
    }

    public boolean getStrictErrorChecking() {
        return strictErrorChecking;
    }

    public void setStrictErrorChecking(boolean strictErrorChecking) {
        this.strictErrorChecking = strictErrorChecking;
    }

    public String getDocumentURI() {
        return documentUri;
    }

    public void setDocumentURI(String documentUri) {
        this.documentUri = documentUri;
    }

    public DOMConfiguration getDomConfig() {
        if (domConfiguration == null) {
            domConfiguration = new DOMConfigurationImpl();
        }
        return domConfiguration;
    }

    public void normalizeDocument() {
        normalize();
    }
}
