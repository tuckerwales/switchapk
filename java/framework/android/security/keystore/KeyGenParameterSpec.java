package android.security.keystore;

import java.math.BigInteger;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import javax.security.auth.x500.X500Principal;

/**
 * Describes a key the AndroidKeyStore KeyGenerator or KeyPairGenerator should make.
 * switchapk keeps every authorization; user authentication, attestation and
 * StrongBox cannot be provided and are recorded only.
 */
public final class KeyGenParameterSpec implements AlgorithmParameterSpec {
    private final int purposes;
    private final String keystoreAlias;
    private final int keySize;
    private final AlgorithmParameterSpec algorithmParameterSpec;
    private final X500Principal certificateSubject;
    private final BigInteger certificateSerialNumber;
    private final Date certificateNotBefore;
    private final Date certificateNotAfter;
    private final byte[] attestationChallenge;
    private final boolean devicePropertiesAttestationIncluded;
    private final String attestKeyAlias;
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

    private KeyGenParameterSpec(Builder b) {
        this.purposes = b.purposes;
        this.keystoreAlias = b.keystoreAlias;
        this.keySize = b.keySize;
        this.algorithmParameterSpec = b.algorithmParameterSpec;
        this.certificateSubject = b.certificateSubject;
        this.certificateSerialNumber = b.certificateSerialNumber;
        this.certificateNotBefore = b.certificateNotBefore;
        this.certificateNotAfter = b.certificateNotAfter;
        this.attestationChallenge = b.attestationChallenge;
        this.devicePropertiesAttestationIncluded = b.devicePropertiesAttestationIncluded;
        this.attestKeyAlias = b.attestKeyAlias;
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

    public String getKeystoreAlias() {
        return keystoreAlias;
    }

    public int getKeySize() {
        return keySize;
    }

    public AlgorithmParameterSpec getAlgorithmParameterSpec() {
        return algorithmParameterSpec;
    }

    public X500Principal getCertificateSubject() {
        return certificateSubject;
    }

    public BigInteger getCertificateSerialNumber() {
        return certificateSerialNumber;
    }

    public Date getCertificateNotBefore() {
        return certificateNotBefore == null ? null : new Date(certificateNotBefore.getTime());
    }

    public Date getCertificateNotAfter() {
        return certificateNotAfter == null ? null : new Date(certificateNotAfter.getTime());
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

    public byte[] getAttestationChallenge() {
        return attestationChallenge == null ? null : attestationChallenge.clone();
    }

    public boolean isDevicePropertiesAttestationIncluded() {
        return devicePropertiesAttestationIncluded;
    }

    public boolean isStrongBoxBacked() {
        return strongBoxBacked;
    }

    public String getAttestKeyAlias() {
        return attestKeyAlias;
    }

    public static final class Builder {
        private int purposes;
        private String keystoreAlias;
        private int keySize = -1;
        private AlgorithmParameterSpec algorithmParameterSpec;
        private X500Principal certificateSubject;
        private BigInteger certificateSerialNumber;
        private Date certificateNotBefore;
        private Date certificateNotAfter;
        private byte[] attestationChallenge;
        private boolean devicePropertiesAttestationIncluded = false;
        private String attestKeyAlias;
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

        public Builder(String keystoreAlias, int purposes) {
            if (keystoreAlias == null) throw new NullPointerException("keystoreAlias == null");
            if (keystoreAlias.isEmpty()) throw new IllegalArgumentException("keystoreAlias must not be empty");
            this.keystoreAlias = keystoreAlias;
            this.purposes = purposes;
        }

        public Builder setKeySize(int value) {
            if (value < 0) throw new IllegalArgumentException("negative size");
            keySize = value;
            return this;
        }

        public Builder setAlgorithmParameterSpec(AlgorithmParameterSpec value) {
            if (value == null) throw new NullPointerException("spec == null");
            algorithmParameterSpec = value;
            return this;
        }

        public Builder setCertificateSubject(X500Principal value) {
            if (value == null) throw new NullPointerException("subject == null");
            certificateSubject = value;
            return this;
        }

        public Builder setCertificateSerialNumber(BigInteger value) {
            if (value == null) throw new NullPointerException("serialNumber == null");
            certificateSerialNumber = value;
            return this;
        }

        public Builder setCertificateNotBefore(Date value) {
            if (value == null) throw new NullPointerException("date == null");
            certificateNotBefore = new Date(value.getTime());
            return this;
        }

        public Builder setCertificateNotAfter(Date value) {
            if (value == null) throw new NullPointerException("date == null");
            certificateNotAfter = new Date(value.getTime());
            return this;
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

        public Builder setAttestationChallenge(byte[] value) {
            attestationChallenge = value == null ? null : value.clone();
            return this;
        }

        public Builder setDevicePropertiesAttestationIncluded(boolean value) {
            devicePropertiesAttestationIncluded = value;
            return this;
        }

        public Builder setAttestKeyAlias(String value) {
            attestKeyAlias = value;
            return this;
        }

        public KeyGenParameterSpec build() {
            return new KeyGenParameterSpec(this);
        }
    }
}
