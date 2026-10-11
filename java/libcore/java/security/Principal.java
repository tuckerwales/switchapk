package java.security;

/** implies(Subject) is left out until javax.security.auth.Subject exists. */
public interface Principal {
    boolean equals(Object another);

    String toString();

    int hashCode();

    String getName();
}
