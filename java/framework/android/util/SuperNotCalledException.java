package android.util;

/** Hidden AOSP exception: a lifecycle method did not call through to super. */
public final class SuperNotCalledException extends AndroidRuntimeException {
    public SuperNotCalledException(String msg) { super(msg); }
}
