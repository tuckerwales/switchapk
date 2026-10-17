package android.webkit;

public class JsPromptResult extends JsResult {
    private String mStringResult;

    /** Hidden on Android; package-private here. */
    JsPromptResult(ResultReceiver receiver) {
        super(receiver);
    }

    public void confirm(String result) {
        mStringResult = result;
        confirm();
    }

    /** Hidden on Android: the text entered, or null. */
    String getStringResult() {
        return mStringResult;
    }
}
