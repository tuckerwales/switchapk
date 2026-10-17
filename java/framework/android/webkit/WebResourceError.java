package android.webkit;

public abstract class WebResourceError {
    /** Hidden on Android (SystemApi); package-private here so WebView can subclass it. */
    WebResourceError() {
    }

    public abstract int getErrorCode();

    public abstract CharSequence getDescription();
}
