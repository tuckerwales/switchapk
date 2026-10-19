package javax.xml.namespace;

import java.io.Serializable;
import javax.xml.XMLConstants;

public class QName implements Serializable {
    private static final long serialVersionUID = -9120448754896609940L;

    private final String namespaceURI;
    private final String localPart;
    private final String prefix;

    public QName(String namespaceURI, String localPart) {
        this(namespaceURI, localPart, XMLConstants.DEFAULT_NS_PREFIX);
    }

    public QName(String namespaceURI, String localPart, String prefix) {
        this.namespaceURI = namespaceURI == null ? XMLConstants.NULL_NS_URI : namespaceURI;
        if (localPart == null) {
            throw new IllegalArgumentException("local part cannot be \"null\" when creating a QName");
        }
        this.localPart = localPart;
        if (prefix == null) {
            throw new IllegalArgumentException("prefix cannot be \"null\" when creating a QName");
        }
        this.prefix = prefix;
    }

    public QName(String localPart) {
        this(XMLConstants.NULL_NS_URI, localPart, XMLConstants.DEFAULT_NS_PREFIX);
    }

    public String getNamespaceURI() {
        return namespaceURI;
    }

    public String getLocalPart() {
        return localPart;
    }

    public String getPrefix() {
        return prefix;
    }

    public final boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof QName)) {
            return false;
        }
        QName q = (QName) o;
        return localPart.equals(q.localPart) && namespaceURI.equals(q.namespaceURI);
    }

    public final int hashCode() {
        return namespaceURI.hashCode() ^ localPart.hashCode();
    }

    public String toString() {
        return namespaceURI.isEmpty() ? localPart : "{" + namespaceURI + "}" + localPart;
    }

    public static QName valueOf(String s) {
        if (s == null) {
            throw new IllegalArgumentException("cannot create QName from \"null\" or \"\" String");
        }
        if (s.isEmpty() || s.charAt(0) != '{') {
            return new QName(XMLConstants.NULL_NS_URI, s, XMLConstants.DEFAULT_NS_PREFIX);
        }
        if (s.startsWith("{}")) {
            throw new IllegalArgumentException("Namespace URI .equals(XMLConstants.NULL_NS_URI), .equals(\"\"), "
                    + "only the local part, \"" + s.substring(2) + "\", should be provided.");
        }
        int end = s.indexOf('}');
        if (end == -1) {
            throw new IllegalArgumentException("cannot create QName from \"" + s + "\", missing closing \"}\"");
        }
        return new QName(s.substring(1, end), s.substring(end + 1), XMLConstants.DEFAULT_NS_PREFIX);
    }
}
