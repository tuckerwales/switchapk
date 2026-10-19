package javax.xml.transform;

public interface Source {
    void setSystemId(String systemId);

    String getSystemId();

    default boolean isEmpty() {
        return getSystemId() == null;
    }
}
