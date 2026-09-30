package java.lang;

public class ThreadDeath extends Error {
    public ThreadDeath() {
        super();
    }

    public ThreadDeath(String message) {
        super(message);
    }

    public ThreadDeath(String message, Throwable cause) {
        super(message, cause);
    }

    public ThreadDeath(Throwable cause) {
        super(cause);
    }
}
