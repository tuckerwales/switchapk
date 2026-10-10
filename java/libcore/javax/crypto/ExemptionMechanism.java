package javax.crypto;

/** Export exemption mechanisms do not exist here; Cipher.getExemptionMechanism returns null as on Android. */
public class ExemptionMechanism {
    private final String mechanism;

    protected ExemptionMechanism(ExemptionMechanismSpi exmechSpi, java.security.Provider provider, String mechanism) {
        this.mechanism = mechanism;
    }

    public final String getName() {
        return mechanism;
    }
}
