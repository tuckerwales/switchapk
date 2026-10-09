package java.security.spec;

public class NamedParameterSpec implements AlgorithmParameterSpec {
    public static final NamedParameterSpec X25519 = new NamedParameterSpec("X25519");
    public static final NamedParameterSpec X448 = new NamedParameterSpec("X448");
    public static final NamedParameterSpec ED25519 = new NamedParameterSpec("Ed25519");
    public static final NamedParameterSpec ED448 = new NamedParameterSpec("Ed448");

    private final String name;

    public NamedParameterSpec(String stdName) {
        if (stdName == null) throw new NullPointerException("stdName must not be null");
        this.name = stdName;
    }

    public String getName() {
        return name;
    }
}
