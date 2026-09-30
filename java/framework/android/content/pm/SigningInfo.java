package android.content.pm;

public final class SigningInfo {
    private final Signature[] mSignatures;
    public SigningInfo() { mSignatures = new Signature[] {new Signature(new byte[] {0x53, 0x41, 0x50, 0x4b})}; }
    public boolean hasMultipleSigners() { return false; }
    public boolean hasPastSigningCertificates() { return false; }
    public Signature[] getSigningCertificateHistory() { return mSignatures; }
    public Signature[] getApkContentsSigners() { return mSignatures; }
}
