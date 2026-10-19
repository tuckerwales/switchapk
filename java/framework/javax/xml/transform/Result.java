package javax.xml.transform;

public interface Result {
    java.lang.String PI_DISABLE_OUTPUT_ESCAPING = "javax.xml.transform.disable-output-escaping";
    java.lang.String PI_ENABLE_OUTPUT_ESCAPING = "javax.xml.transform.enable-output-escaping";
    void setSystemId(java.lang.String p0);
    java.lang.String getSystemId();
}
