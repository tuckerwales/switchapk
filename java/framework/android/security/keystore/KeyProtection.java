package android.security.keystore;

import java.math.BigInteger;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import javax.security.auth.x500.X500Principal;

/** Authorizations for a key imported into the AndroidKeyStore with KeyStore.setEntry. */
public final class KeyProtection implements java.security.KeyStore.ProtectionParameter {
    private final int purposes;
    private final Date keyValidityStart;
    private final Date keyValidityForOriginationEnd;
    private final Date keyValidityForConsumptionEnd;
    private final String[] digests;
    private final String[] mgf1Digests;
    private final String[] encryptionPaddings;
    private final String[] signaturePaddings;
    private final String[] blockModes;
    private final boolean randomizedEncryptionRequired;
    private final boolean userAuthenticationRequired;
    private final boolean userConfirmationRequired;
    private final int userAuthenticationValidityDurationSeconds;
    private final int userAuthenticationType;
    private final boolean userPresenceRequired;
    private final boolean userAuthenticationValidWhileOnBody;
    private final boolean invalidatedByBiometricEnrollment;
    private final boolean unlockedDeviceRequired;
    private final boolean strongBoxBacked;
    private final int maxUsageCount;

    private KeyProtection(Builder b) {
        this.purposes = b.purposes;
        this.keyValidityStart = b.keyValidityStart;
        this.keyValidityForOriginationEnd = b.keyValidityForOriginationEnd;
        this.keyValidityForConsumptionEnd = b.keyValidityForConsumptionEnd;
        this.digests = b.digests;
        this.mgf1Digests = b.mgf1Digests;
        this.encryptionPaddings = b.encryptionPaddings;
        this.signaturePaddings = b.signaturePaddings;
        this.blockModes = b.blockModes;
        this.randomizedEncryptionRequired = b.randomizedEncryptionRequired;
        this.userAuthenticationRequired = b.userAuthenticationRequired;
        this.userConfirmationRequired = b.userConfirmationRequired;
        this.userAuthenticationValidityDurationSeconds = b.userAuthenticationValidityDurationSeconds;
        this.userAuthenticationType = b.userAuthenticationType;
        this.userPresenceRequired = b.userPresenceRequired;
        this.userAuthenticationValidWhileOnBody = b.userAuthenticationValidWhileOnBody;
        this.invalidatedByBiometricEnrollment = b.invalidatedByBiometricEnrollment;
        this.unlockedDeviceRequired = b.unlockedDeviceRequired;
        this.strongBoxBacked = b.strongBoxBacked;
        this.maxUsageCount = b.maxUsageCount;
    }

    public int getPurposes() {
        return purposes;
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

    public String[] getDigests() {
        if (digests == null) throw new IllegalStateException("Digests not specified");
        return digests.clone();
    }

    public boolean isDigestsSpecified() {
        return digests != null;
    }

    public Set<String> getMgf1Digests() {
        if (mgf1Digests == null) return Collections.emptySet();
        HashSet<String> s = new HashSet<String>();
        Collections.addAll(s, mgf1Digests);
        return Collections.unmodifiableSet(s);
    }

    public boolean isMgf1DigestsSpecified() {
        return mgf1Digests != null;
    }

    public String[] getEncryptionPaddings() {
        return encryptionPaddings == null ? new String[0] : encryptionPaddings.clone();
    }

    public String[] getSignaturePaddings() {
        return signaturePaddings == null ? new String[0] : signaturePaddings.clone();
    }

    public String[] getBlockModes() {
        return blockModes == null ? new String[0] : blockModes.clone();
    }

    public boolean isRandomizedEncryptionRequired() {
        return randomizedEncryptionRequired;
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

    public boolean isUserPresenceRequired() {
        return userPresenceRequired;
    }

    public boolean isUserAuthenticationValidWhileOnBody() {
        return userAuthenticationValidWhileOnBody;
    }

    public boolean isInvalidatedByBiometricEnrollment() {
        return invalidatedByBiometricEnrollment;
    }

    public boolean isUnlockedDeviceRequired() {
        return unlockedDeviceRequired;
    }

    public int getMaxUsageCount() {
        return maxUsageCount;
    }

    public static final class Builder {
        private int purposes;
        private Date keyValidityStart;
        private Date keyValidityForOriginationEnd;
        private Date keyValidityForConsumptionEnd;
        private String[] digests;
        private String[] mgf1Digests;
        private String[] encryptionPaddings;
        private String[] signaturePaddings;
        private String[] blockModes;
        private boolean randomizedEncryptionRequired = true;
        private boolean userAuthenticationRequired = false;
        private boolean userConfirmationRequired = false;
        private int userAuthenticationValidityDurationSeconds = 0;
        private int userAuthenticationType = KeyProperties.AUTH_BIOMETRIC_STRONG;
        private boolean userPresenceRequired = false;
        private boolean userAuthenticationValidWhileOnBody = false;
        private boolean invalidatedByBiometricEnrollment = true;
        private boolean unlockedDeviceRequired = false;
        private boolean strongBoxBacked = false;
        private int maxUsageCount = KeyProperties.UNRESTRICTED_USAGE_COUNT;

        public Builder(int purposes) {
            this.purposes = purposes;
        }

        public Builder setKeyValidityStart(Date value) {
            keyValidityStart = value == null ? null : new Date(value.getTime());
            return this;
        }

        public Builder setKeyValidityEnd(Date value) {
            setKeyValidityForOriginationEnd(value);
            setKeyValidityForConsumptionEnd(value);
            return this;
        }

        public Builder setKeyValidityForOriginationEnd(Date value) {
            keyValidityForOriginationEnd = value == null ? null : new Date(value.getTime());
            return this;
        }

        public Builder setKeyValidityForConsumptionEnd(Date value) {
            keyValidityForConsumptionEnd = value == null ? null : new Date(value.getTime());
            return this;
        }

        public Builder setDigests(String... value) {
            digests = value == null ? null : value.clone();
            return this;
        }

        public Builder setMgf1Digests(String... value) {
            mgf1Digests = value == null ? null : value.clone();
            return this;
        }

        public Builder setEncryptionPaddings(String... value) {
            encryptionPaddings = value == null ? null : value.clone();
            return this;
        }

        public Builder setSignaturePaddings(String... value) {
            signaturePaddings = value == null ? null : value.clone();
            return this;
        }

        public Builder setBlockModes(String... value) {
            blockModes = value == null ? null : value.clone();
            return this;
        }

        public Builder setRandomizedEncryptionRequired(boolean value) {
            randomizedEncryptionRequired = value;
            return this;
        }

        public Builder setUserAuthenticationRequired(boolean value) {
            userAuthenticationRequired = value;
            return this;
        }

        public Builder setUserConfirmationRequired(boolean value) {
            userConfirmationRequired = value;
            return this;
        }

        public Builder setUserAuthenticationValidityDurationSeconds(int value) {
            if (value < -1) throw new IllegalArgumentException("seconds must be -1 or larger");
            userAuthenticationValidityDurationSeconds = value;
            return this;
        }

        public Builder setUserAuthenticationParameters(int timeout, int type) {
            if (timeout < 0) throw new IllegalArgumentException("timeout must be 0 or larger");
            userAuthenticationValidityDurationSeconds = timeout;
            userAuthenticationType = type;
            return this;
        }

        public Builder setUserPresenceRequired(boolean value) {
            userPresenceRequired = value;
            return this;
        }

        public Builder setUserAuthenticationValidWhileOnBody(boolean value) {
            userAuthenticationValidWhileOnBody = value;
            return this;
        }

        public Builder setInvalidatedByBiometricEnrollment(boolean value) {
            invalidatedByBiometricEnrollment = value;
            return this;
        }

        public Builder setUnlockedDeviceRequired(boolean value) {
            unlockedDeviceRequired = value;
            return this;
        }

        public Builder setIsStrongBoxBacked(boolean value) {
            strongBoxBacked = value;
            return this;
        }

        public Builder setMaxUsageCount(int value) {
            if (value != KeyProperties.UNRESTRICTED_USAGE_COUNT && value <= 0) throw new IllegalArgumentException("maxUsageCount is not valid");
            maxUsageCount = value;
            return this;
        }

        public KeyProtection build() {
            return new KeyProtection(this);
        }
    }
}
