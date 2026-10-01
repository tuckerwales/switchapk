package android.text.method;

/** Hidden AOSP API: a transformation that can be told to handle length-changing results. */
public interface TransformationMethod2 extends TransformationMethod {
    void setLengthChangesAllowed(boolean allowLengthChanges);
}
