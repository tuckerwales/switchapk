package android.view.inputmethod;

/** Atomic snapshot of the editor text around the selection. */
public final class TextSnapshot {
    private final SurroundingText mSurroundingText;
    private final int mCompositionStart;
    private final int mCompositionEnd;
    private final int mCursorCapsMode;

    public TextSnapshot(SurroundingText surroundingText, int compositionStart, int compositionEnd, int cursorCapsMode) {
        mSurroundingText = surroundingText;
        mCompositionStart = compositionStart;
        mCompositionEnd = compositionEnd;
        mCursorCapsMode = cursorCapsMode;
    }

    public SurroundingText getSurroundingText() { return mSurroundingText; }

    public int getSelectionStart() { return mSurroundingText.getSelectionStart() < 0 ? -1 : mSurroundingText.getOffset() + mSurroundingText.getSelectionStart(); }

    public int getSelectionEnd() { return mSurroundingText.getSelectionEnd() < 0 ? -1 : mSurroundingText.getOffset() + mSurroundingText.getSelectionEnd(); }

    public int getCompositionStart() { return mCompositionStart; }

    public int getCompositionEnd() { return mCompositionEnd; }

    public int getCursorCapsMode() { return mCursorCapsMode; }
}
