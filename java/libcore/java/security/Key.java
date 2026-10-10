package java.security;

public interface Key extends java.io.Serializable {
    long serialVersionUID = 6603384152749567654L;

    String getAlgorithm();

    String getFormat();

    byte[] getEncoded();
}
