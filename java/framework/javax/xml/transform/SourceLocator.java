package javax.xml.transform;

public interface SourceLocator {
    java.lang.String getPublicId();
    java.lang.String getSystemId();
    int getLineNumber();
    int getColumnNumber();
}
