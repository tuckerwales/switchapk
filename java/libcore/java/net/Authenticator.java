package java.net;

public abstract class Authenticator {
    private static Authenticator theAuthenticator;

    public Authenticator() {
    }

    public static synchronized void setDefault(Authenticator a) {
        theAuthenticator = a;
    }

    protected PasswordAuthentication getPasswordAuthentication() {
        return null;
    }
}
