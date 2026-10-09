package javax.crypto.interfaces;

public interface PBEKey extends javax.crypto.SecretKey {
    long serialVersionUID = -1430015993304333921L;

    char[] getPassword();

    byte[] getSalt();

    int getIterationCount();
}
