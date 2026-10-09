package android.security.keystore;

import java.security.spec.KeySpec;
import java.util.Date;

/**
 * What the AndroidKeyStore knows about a key, from SecretKeyFactory.getKeySpec
 * or KeyFactory.getKeySpec. switchapk's keystore is software only, so keys are
 * never inside secure hardware.
 */
public class KeyInfo implements KeySpec {
    private final String keystoreAlias;
    private final int keySize;
    private final int origin;
    private final Date keyValidityStart;
    private final Date keyValidityForOriginationEnd;
    private final Date keyValidityForConsumptionEnd;
    private final int purposes;
    private final String[] blockModes;
    private final String[] encryptionPaddings;
    private final String[] signaturePaddings;
    private final String[] digests;
    private final boolean userAuthenticationRequired;
    private final boolean userConfirmationRequired;
    private final int userAuthenticationValidityDurationSeconds;
    private final int userAuthenticationType;
    private final boolean userAuthenticationValidWhileOnBody;
    private final boolean invalidatedByBiometricEnrollment;
    private final boolean trustedUserPresenceRequired;
    private final int remainingUsageCount;

    KeyInfo(KeyStoreEntry e) {
        keystoreAlias = e.alias;
        keySize = e.keySize;
        origin = e.origin;
        keyValidityStart = e.date(e.validityStart);
        keyValidityForOriginationEnd = e.date(e.originationEnd);
        keyValidityForConsumptionEnd = e.date(e.consumptionEnd);
        purposes = e.purposes;
        blockModes = e.blockModes;
        encryptionPaddings = e.encryptionPaddings;
        signaturePaddings = e.signaturePaddings;
        digests = e.digests;
        userAuthenticationRequired = e.userAuthenticationRequired;
        userConfirmationRequired = false;
        userAuthenticationValidityDurationSeconds = e.userAuthenticationValidityDurationSeconds;
        userAuthenticationType = e.userAuthenticationType;
        userAuthenticationValidWhileOnBody = false;
        invalidatedByBiometricEnrollment = e.userAuthenticationRequired;
        trustedUserPresenceRequired = false;
        remainingUsageCount = e.maxUsageCount;
    }

    public String getKeystoreAlias() {
        return keystoreAlias;
    }

    @Deprecated
    public boolean isInsideSecureHardware() {
        return false;
    }

    public int getOrigin() {
        return origin;
    }

    public int getKeySize() {
        return keySize;
    }

    public Date getKeyValidityStart() {
        return keyValidityStart == null ? null : new Date(keyValidityStart.getTime());
    }

    public Date getKeyValidityForConsumptionEnd() {
        return keyValidityForConsumptionEnd == null ? null : new Date(keyValidityForConsumptionEnd.getTime());
    }

    public Date getKeyValidityForOriginationEnd() {
        return keyValidityForOriginationEnd == null ? null : new Date(keyValidityForOriginationEnd.getTime());
    }

    public int getPurposes() {
        return purposes;
    }

    public String[] getBlockModes() {
        return blockModes.clone();
    }

    public String[] getEncryptionPaddings() {
        return encryptionPaddings.clone();
    }

    public String[] getSignaturePaddings() {
        return signaturePaddings.clone();
    }

    public String[] getDigests() {
        return digests.clone();
    }

    public boolean isUserAuthenticationRequired() {
        return userAuthenticationRequired;
    }

    public boolean isUserConfirmationRequired() {
        return userConfirmationRequired;
    }

    public int getUserAuthenticationValidityDurationSeconds() {
        return userAuthenticationValidityDurationSeconds;
    }

    public int getUserAuthenticationType() {
        return userAuthenticationType;
    }

    public boolean isUserAuthenticationRequirementEnforcedBySecureHardware() {
        return false;
    }

    public boolean isUserAuthenticationValidWhileOnBody() {
        return userAuthenticationValidWhileOnBody;
    }

    public boolean isInvalidatedByBiometricEnrollment() {
        return invalidatedByBiometricEnrollment;
    }

    public boolean isTrustedUserPresenceRequired() {
        return trustedUserPresenceRequired;
    }

    public int getSecurityLevel() {
        return KeyProperties.SECURITY_LEVEL_SOFTWARE;
    }

    public int getRemainingUsageCount() {
        return remainingUsageCount;
    }
}
