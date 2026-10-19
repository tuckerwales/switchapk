package javax.xml.namespace;

public interface NamespaceContext {
    java.lang.String getNamespaceURI(java.lang.String p0);
    java.lang.String getPrefix(java.lang.String p0);
    java.util.Iterator<java.lang.String> getPrefixes(java.lang.String p0);
}
