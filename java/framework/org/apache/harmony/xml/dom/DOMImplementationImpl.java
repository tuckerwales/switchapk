package org.apache.harmony.xml.dom;

import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;

public final class DOMImplementationImpl implements DOMImplementation {
    private static DOMImplementationImpl instance;

    DOMImplementationImpl() {
    }

    public static synchronized DOMImplementationImpl getInstance() {
        if (instance == null) {
            instance = new DOMImplementationImpl();
        }
        return instance;
    }

    public boolean hasFeature(String feature, String version) {
        if (feature == null) {
            return false;
        }
        boolean anyVersion = version == null || version.isEmpty();
        if (feature.startsWith("+")) {
            feature = feature.substring(1);
        }
        if (feature.equalsIgnoreCase("Core")) {
            return anyVersion || version.equals("1.0") || version.equals("2.0") || version.equals("3.0");
        } else if (feature.equalsIgnoreCase("XML")) {
            return anyVersion || version.equals("1.0") || version.equals("2.0") || version.equals("3.0");
        } else if (feature.equalsIgnoreCase("XMLVersion")) {
            return anyVersion || version.equals("1.0") || version.equals("1.1");
        }
        return false;
    }

    public DocumentType createDocumentType(String qualifiedName, String publicId, String systemId)
            throws DOMException {
        NodeImpl.checkName(qualifiedName);
        int colon = qualifiedName.indexOf(':');
        if (colon == 0 || colon == qualifiedName.length() - 1 || qualifiedName.indexOf(':', colon + 1) >= 0) {
            throw new DOMException(DOMException.NAMESPACE_ERR, "NAMESPACE_ERR: malformed qualified name");
        }
        return new DocumentTypeImpl(null, qualifiedName, publicId, systemId, null);
    }

    public Document createDocument(String namespaceURI, String qualifiedName, DocumentType doctype)
            throws DOMException {
        return new DocumentImpl(this, namespaceURI, qualifiedName, doctype, null);
    }

    public Object getFeature(String feature, String version) {
        return hasFeature(feature, version) ? this : null;
    }
}
