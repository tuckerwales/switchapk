package javax.xml.transform;

public interface Templates {
    javax.xml.transform.Transformer newTransformer() throws javax.xml.transform.TransformerConfigurationException;
    java.util.Properties getOutputProperties();
}
