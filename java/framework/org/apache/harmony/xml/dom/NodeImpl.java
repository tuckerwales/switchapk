package org.apache.harmony.xml.dom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.CharacterData;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.TypeInfo;
import org.w3c.dom.UserDataHandler;

/**
 * Base of the DOM implementation (same package and class layout as Android's Apache Harmony DOM,
 * written fresh). Leaf behaviour lives here; {@link InnerNodeImpl} adds children.
 */
public abstract class NodeImpl implements Node {
    static final String XMLNS_NS = "http://www.w3.org/2000/xmlns/";
    static final String XML_NS = "http://www.w3.org/XML/1998/namespace";

    private static final NodeList EMPTY_LIST = new NodeListImpl();

    static final TypeInfo NULL_TYPE_INFO = new TypeInfo() {
        public String getTypeName() {
            return null;
        }

        public String getTypeNamespace() {
            return null;
        }

        public boolean isDerivedFrom(String typeNamespaceArg, String typeNameArg, int derivationMethod) {
            return false;
        }
    };

    /** The owning document; for a DocumentImpl this is the document itself. */
    DocumentImpl document;

    private Map<String, UserData> userData;

    NodeImpl(DocumentImpl document) {
        this.document = document;
    }

    public String getNodeValue() throws DOMException {
        return null;
    }

    public void setNodeValue(String nodeValue) throws DOMException {
    }

    public Node getParentNode() {
        return null;
    }

    public NodeList getChildNodes() {
        return EMPTY_LIST;
    }

    public Node getFirstChild() {
        return null;
    }

    public Node getLastChild() {
        return null;
    }

    public Node getPreviousSibling() {
        return null;
    }

    public Node getNextSibling() {
        return null;
    }

    public NamedNodeMap getAttributes() {
        return null;
    }

    public Document getOwnerDocument() {
        return document == this ? null : document;
    }

    public Node insertBefore(Node newChild, Node refChild) throws DOMException {
        throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, "HIERARCHY_REQUEST_ERR: This node cannot have "
                + "children.");
    }

    public Node replaceChild(Node newChild, Node oldChild) throws DOMException {
        throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, "HIERARCHY_REQUEST_ERR: This node cannot have "
                + "children.");
    }

    public Node removeChild(Node oldChild) throws DOMException {
        throw new DOMException(DOMException.NOT_FOUND_ERR, "NOT_FOUND_ERR: The node is not a child of this node.");
    }

    public Node appendChild(Node newChild) throws DOMException {
        throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, "HIERARCHY_REQUEST_ERR: This node cannot have "
                + "children.");
    }

    public boolean hasChildNodes() {
        return false;
    }

    public final Node cloneNode(boolean deep) {
        NodeImpl copy = cloneInto(document, deep);
        notifyUserDataHandlers(UserDataHandler.NODE_CLONED, this, copy);
        return copy;
    }

    /**
     * Copies this node into the given document. Subclasses copy their own state; the children are
     * copied when deep is true.
     */
    abstract NodeImpl cloneInto(DocumentImpl target, boolean deep);

    public void normalize() {
    }

    public boolean isSupported(String feature, String version) {
        return DOMImplementationImpl.getInstance().hasFeature(feature, version);
    }

    public String getNamespaceURI() {
        return null;
    }

    public String getPrefix() {
        return null;
    }

    public void setPrefix(String prefix) throws DOMException {
    }

    public String getLocalName() {
        return null;
    }

    public boolean hasAttributes() {
        return false;
    }

    public String getBaseURI() {
        switch (getNodeType()) {
            case DOCUMENT_NODE:
                return ((Document) this).getDocumentURI();
            case ELEMENT_NODE:
            case PROCESSING_INSTRUCTION_NODE:
            case DOCUMENT_FRAGMENT_NODE:
            case ENTITY_REFERENCE_NODE:
                Node parent = getParentNode();
                return parent != null ? parent.getBaseURI() : (document != null ? document.getDocumentURI() : null);
            case ATTRIBUTE_NODE:
                Element owner = ((Attr) this).getOwnerElement();
                return owner != null ? owner.getBaseURI() : null;
            default:
                return null;
        }
    }

    // ---- document order -------------------------------------------------------------------

    public short compareDocumentPosition(Node other) throws DOMException {
        if (other == this) {
            return 0;
        }
        if (!(other instanceof NodeImpl)) {
            throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "NOT_SUPPORTED_ERR: foreign node");
        }
        NodeImpl a = this;
        NodeImpl b = (NodeImpl) other;
        // Attributes are positioned right after their owner element.
        NodeImpl aAttrOwner = a instanceof AttrImpl ? (NodeImpl) ((AttrImpl) a).getOwnerElement() : null;
        NodeImpl bAttrOwner = b instanceof AttrImpl ? (NodeImpl) ((AttrImpl) b).getOwnerElement() : null;
        if (aAttrOwner != null && aAttrOwner == bAttrOwner) {
            List<AttrImpl> attrs = ((ElementImpl) aAttrOwner).attributes;
            int ai = attrs.indexOf(a);
            int bi = attrs.indexOf(b);
            return (short) (DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC
                    | (bi < ai ? DOCUMENT_POSITION_PRECEDING : DOCUMENT_POSITION_FOLLOWING));
        }
        List<Node> pa = ancestry(a);
        List<Node> pb = ancestry(b);
        if (pa.get(0) != pb.get(0)) {
            int ha = System.identityHashCode(pa.get(0));
            int hb = System.identityHashCode(pb.get(0));
            return (short) (DOCUMENT_POSITION_DISCONNECTED | DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC
                    | (hb < ha ? DOCUMENT_POSITION_PRECEDING : DOCUMENT_POSITION_FOLLOWING));
        }
        int i = 0;
        int n = Math.min(pa.size(), pb.size());
        while (i < n && pa.get(i) == pb.get(i)) {
            i++;
        }
        if (i == pb.size()) {
            // other is an ancestor of this
            return DOCUMENT_POSITION_CONTAINS | DOCUMENT_POSITION_PRECEDING;
        }
        if (i == pa.size()) {
            return DOCUMENT_POSITION_CONTAINED_BY | DOCUMENT_POSITION_FOLLOWING;
        }
        Node ca = pa.get(i);
        Node cb = pb.get(i);
        // Attribute nodes come before children of their owner element.
        if (ca instanceof AttrImpl && !(cb instanceof AttrImpl)) {
            return DOCUMENT_POSITION_FOLLOWING;
        }
        if (cb instanceof AttrImpl && !(ca instanceof AttrImpl)) {
            return DOCUMENT_POSITION_PRECEDING;
        }
        if (ca instanceof AttrImpl) {
            List<AttrImpl> attrs = ((ElementImpl) pa.get(i - 1)).attributes;
            return attrs.indexOf(cb) < attrs.indexOf(ca) ? DOCUMENT_POSITION_PRECEDING : DOCUMENT_POSITION_FOLLOWING;
        }
        for (Node s = ca.getNextSibling(); s != null; s = s.getNextSibling()) {
            if (s == cb) {
                return DOCUMENT_POSITION_FOLLOWING;
            }
        }
        return DOCUMENT_POSITION_PRECEDING;
    }

    /** Returns the chain from the root down to node (attributes hang below their owner element). */
    static List<Node> ancestry(Node node) {
        ArrayList<Node> chain = new ArrayList<Node>();
        Node n = node;
        while (n != null) {
            chain.add(n);
            n = n instanceof Attr ? ((Attr) n).getOwnerElement() : n.getParentNode();
        }
        java.util.Collections.reverse(chain);
        return chain;
    }

    // ---- text content ---------------------------------------------------------------------

    public String getTextContent() throws DOMException {
        return getNodeValue();
    }

    void appendTextContent(StringBuilder sb) {
        short type = getNodeType();
        if (type != COMMENT_NODE && type != PROCESSING_INSTRUCTION_NODE) {
            String value = getTextContent();
            if (value != null) {
                sb.append(value);
            }
        }
    }

    public void setTextContent(String textContent) throws DOMException {
        setNodeValue(textContent);
    }

    public boolean isSameNode(Node other) {
        return this == other;
    }

    // ---- namespace lookup (DOM Level 3 Appendix B) ----------------------------------------

    /** The element used for namespace lookup on behalf of this node. */
    Element namespaceLookupElement() {
        switch (getNodeType()) {
            case ELEMENT_NODE:
                return (Element) this;
            case DOCUMENT_NODE:
                return ((Document) this).getDocumentElement();
            case ENTITY_NODE:
            case NOTATION_NODE:
            case DOCUMENT_FRAGMENT_NODE:
            case DOCUMENT_TYPE_NODE:
                return null;
            case ATTRIBUTE_NODE:
                return ((Attr) this).getOwnerElement();
            default:
                return parentElement(this);
        }
    }

    static Element parentElement(Node node) {
        Node p = node.getParentNode();
        while (p != null && p.getNodeType() != ELEMENT_NODE) {
            if (p.getNodeType() != ENTITY_REFERENCE_NODE) {
                return null;
            }
            p = p.getParentNode();
        }
        return (Element) p;
    }

    public String lookupPrefix(String namespaceURI) {
        if (namespaceURI == null || namespaceURI.isEmpty()) {
            return null;
        }
        Element start = namespaceLookupElement();
        for (Element e = start; e != null; e = parentElement(e)) {
            if (namespaceURI.equals(e.getNamespaceURI()) && e.getPrefix() != null
                    && namespaceURI.equals(start.lookupNamespaceURI(e.getPrefix()))) {
                return e.getPrefix();
            }
            NamedNodeMap attrs = e.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Attr a = (Attr) attrs.item(i);
                if ("xmlns".equals(a.getPrefix()) && namespaceURI.equals(a.getValue())
                        && namespaceURI.equals(start.lookupNamespaceURI(a.getLocalName()))) {
                    return a.getLocalName();
                }
            }
        }
        return null;
    }

    public boolean isDefaultNamespace(String namespaceURI) {
        for (Element e = namespaceLookupElement(); e != null; e = parentElement(e)) {
            if (e.getPrefix() == null && e.getNamespaceURI() != null) {
                return e.getNamespaceURI().equals(namespaceURI);
            }
            Attr xmlns = e.getAttributeNode("xmlns");
            if (xmlns != null) {
                String value = xmlns.getValue();
                return value.isEmpty() ? namespaceURI == null : value.equals(namespaceURI);
            }
        }
        return namespaceURI == null;
    }

    public String lookupNamespaceURI(String prefix) {
        if (prefix != null && prefix.isEmpty()) {
            prefix = null;
        }
        for (Element e = namespaceLookupElement(); e != null; e = parentElement(e)) {
            String ns = e.getNamespaceURI();
            String p = e.getPrefix();
            if (ns != null && (prefix == null ? p == null : prefix.equals(p))) {
                return ns;
            }
            if (e.hasAttributes()) {
                NamedNodeMap attrs = e.getAttributes();
                for (int i = 0; i < attrs.getLength(); i++) {
                    Attr a = (Attr) attrs.item(i);
                    String value = a.getValue();
                    if (prefix != null && "xmlns".equals(a.getPrefix()) && prefix.equals(a.getLocalName())) {
                        return value.isEmpty() ? null : value;
                    }
                    if (prefix == null && "xmlns".equals(a.getNodeName())) {
                        return value.isEmpty() ? null : value;
                    }
                }
            }
        }
        if ("xml".equals(prefix)) {
            return XML_NS;
        }
        return null;
    }

    // ---- equality -------------------------------------------------------------------------

    public boolean isEqualNode(Node arg) {
        if (arg == this) {
            return true;
        }
        if (arg == null || arg.getNodeType() != getNodeType()) {
            return false;
        }
        if (!eq(getNodeName(), arg.getNodeName()) || !eq(getLocalName(), arg.getLocalName())
                || !eq(getNamespaceURI(), arg.getNamespaceURI()) || !eq(getPrefix(), arg.getPrefix())
                || !eq(getNodeValue(), arg.getNodeValue())) {
            return false;
        }
        if (getNodeType() == DOCUMENT_TYPE_NODE) {
            DocumentType a = (DocumentType) this;
            DocumentType b = (DocumentType) arg;
            if (!eq(a.getPublicId(), b.getPublicId()) || !eq(a.getSystemId(), b.getSystemId())
                    || !eq(a.getInternalSubset(), b.getInternalSubset())) {
                return false;
            }
        }
        NamedNodeMap am = getAttributes();
        NamedNodeMap bm = arg.getAttributes();
        if ((am == null) != (bm == null)) {
            return false;
        }
        if (am != null) {
            if (am.getLength() != bm.getLength()) {
                return false;
            }
            for (int i = 0; i < am.getLength(); i++) {
                Node a = am.item(i);
                Node b = a.getLocalName() == null ? bm.getNamedItem(a.getNodeName())
                        : bm.getNamedItemNS(a.getNamespaceURI(), a.getLocalName());
                if (b == null || !a.isEqualNode(b)) {
                    return false;
                }
            }
        }
        NodeList ac = getChildNodes();
        NodeList bc = arg.getChildNodes();
        if (ac.getLength() != bc.getLength()) {
            return false;
        }
        for (int i = 0; i < ac.getLength(); i++) {
            if (!ac.item(i).isEqualNode(bc.item(i))) {
                return false;
            }
        }
        return true;
    }

    static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    public Object getFeature(String feature, String version) {
        return isSupported(feature, version) ? this : null;
    }

    // ---- user data ------------------------------------------------------------------------

    private static final class UserData {
        final Object value;
        final UserDataHandler handler;

        UserData(Object value, UserDataHandler handler) {
            this.value = value;
            this.handler = handler;
        }
    }

    public Object setUserData(String key, Object data, UserDataHandler handler) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }
        UserData previous;
        if (data == null) {
            previous = userData == null ? null : userData.remove(key);
        } else {
            if (userData == null) {
                userData = new LinkedHashMap<String, UserData>();
            }
            previous = userData.put(key, new UserData(data, handler));
        }
        return previous == null ? null : previous.value;
    }

    public Object getUserData(String key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }
        UserData d = userData == null ? null : userData.get(key);
        return d == null ? null : d.value;
    }

    static void notifyUserDataHandlers(short operation, NodeImpl source, NodeImpl destination) {
        if (source.userData == null) {
            return;
        }
        for (Map.Entry<String, UserData> e : new ArrayList<Map.Entry<String, UserData>>(source.userData.entrySet())) {
            UserData d = e.getValue();
            if (d.handler != null) {
                d.handler.handle(operation, e.getKey(), d.value, source, destination);
            }
        }
    }

    // ---- shared validation helpers --------------------------------------------------------

    static boolean isXmlName(String s, boolean allowColon) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok;
            if (c == ':') {
                ok = allowColon;
            } else if (i == 0) {
                ok = isNameStart(c);
            } else {
                ok = isNameStart(c) || (c >= '0' && c <= '9') || c == '-' || c == '.' || c == 0xB7
                        || (c >= 0x300 && c <= 0x36F) || (c >= 0x203F && c <= 0x2040);
            }
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNameStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_'
                || (c >= 0xC0 && c <= 0xD6) || (c >= 0xD8 && c <= 0xF6) || (c >= 0xF8 && c <= 0x2FF)
                || (c >= 0x370 && c <= 0x37D) || (c >= 0x37F && c <= 0x1FFF) || (c >= 0x200C && c <= 0x200D)
                || (c >= 0x2070 && c <= 0x218F) || (c >= 0x2C00 && c <= 0x2FEF) || (c >= 0x3001 && c <= 0xD7FF)
                || (c >= 0xF900 && c <= 0xFDCF) || (c >= 0xFDF0 && c <= 0xFFFD) || (c >= 0xD800 && c <= 0xDFFF);
    }

    static void checkName(String name) {
        if (!isXmlName(name, true)) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "INVALID_CHARACTER_ERR: An invalid or "
                    + "illegal XML character is specified.");
        }
    }

    /**
     * Validates a qualified name against its namespace as createElementNS/createAttributeNS do.
     * Returns {prefix, localName}.
     */
    static String[] splitQualifiedName(String namespaceURI, String qualifiedName, boolean attribute) {
        if (qualifiedName == null) {
            throw new DOMException(DOMException.NAMESPACE_ERR, "NAMESPACE_ERR: qualifiedName == null");
        }
        checkName(qualifiedName);
        int colon = qualifiedName.indexOf(':');
        String prefix = null;
        String local = qualifiedName;
        if (colon >= 0) {
            prefix = qualifiedName.substring(0, colon);
            local = qualifiedName.substring(colon + 1);
            if (prefix.isEmpty() || local.isEmpty() || local.indexOf(':') >= 0 || !isXmlName(local, false)) {
                throw new DOMException(DOMException.NAMESPACE_ERR, "NAMESPACE_ERR: An attempt is made to create or "
                        + "change an object in a way which is incorrect with regard to namespaces.");
            }
        }
        checkPrefix(namespaceURI, prefix, local, attribute);
        return new String[] {prefix, local};
    }

    static void checkPrefix(String namespaceURI, String prefix, String local, boolean attribute) {
        boolean bad = false;
        if (prefix != null) {
            if (namespaceURI == null) {
                bad = true;
            } else if ("xml".equals(prefix) && !XML_NS.equals(namespaceURI)) {
                bad = true;
            } else if ("xmlns".equals(prefix) && (!attribute || !XMLNS_NS.equals(namespaceURI))) {
                bad = true;
            }
        }
        if (prefix == null && "xmlns".equals(local) && (!attribute || !XMLNS_NS.equals(namespaceURI))) {
            bad = true;
        }
        if (XMLNS_NS.equals(namespaceURI) && !"xmlns".equals(prefix) && !(prefix == null && "xmlns".equals(local))) {
            bad = true;
        }
        if (bad) {
            throw new DOMException(DOMException.NAMESPACE_ERR, "NAMESPACE_ERR: An attempt is made to create or "
                    + "change an object in a way which is incorrect with regard to namespaces.");
        }
    }

    static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    static void collectText(Node node, StringBuilder sb) {
        if (node instanceof NodeImpl) {
            ((NodeImpl) node).appendTextContent(sb);
        } else if (node instanceof CharacterData || node instanceof ProcessingInstruction) {
            sb.append(node.getNodeValue());
        }
    }
}
