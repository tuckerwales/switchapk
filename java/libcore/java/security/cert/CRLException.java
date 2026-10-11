package java.security.cert;

import java.security.GeneralSecurityException;

public class CRLException extends GeneralSecurityException {
    public CRLException() {
    }

    public CRLException(String message) {
        super(message);
    }

    public CRLException(String message, Throwable cause) {
        super(message, cause);
    }

    public CRLException(Throwable cause) {
        super(cause);
    }
}
