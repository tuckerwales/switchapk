package android.widget;

/** A view that can be checked or unchecked (AOSP Checkable). */
public interface Checkable {
    void setChecked(boolean checked);

    boolean isChecked();

    void toggle();
}
