package java.util;

/** Thrown when a service provider cannot be loaded (see ServiceLoader). */
public class ServiceConfigurationError extends Error {
    private static final long serialVersionUID = 74132770414881L;

    public ServiceConfigurationError(String msg) { super(msg); }

    public ServiceConfigurationError(String msg, Throwable cause) { super(msg, cause); }
}
