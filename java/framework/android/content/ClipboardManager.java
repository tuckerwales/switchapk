package android.content;

import java.util.ArrayList;

public class ClipboardManager extends android.text.ClipboardManager {
    private static ClipData sPrimary;
    private final ArrayList<OnPrimaryClipChangedListener> mListeners = new ArrayList<OnPrimaryClipChangedListener>();

    public interface OnPrimaryClipChangedListener {
        void onPrimaryClipChanged();
    }

    public ClipboardManager() {}

    public void setPrimaryClip(ClipData clip) {
        synchronized (ClipboardManager.class) { sPrimary = clip; }
        for (OnPrimaryClipChangedListener l : new ArrayList<OnPrimaryClipChangedListener>(mListeners)) l.onPrimaryClipChanged();
    }

    public void clearPrimaryClip() { setPrimaryClip(null); }
    public ClipData getPrimaryClip() { synchronized (ClipboardManager.class) { return sPrimary; } }
    public ClipDescription getPrimaryClipDescription() { ClipData c = getPrimaryClip(); return c != null ? c.getDescription() : null; }
    public boolean hasPrimaryClip() { return getPrimaryClip() != null; }
    public void addPrimaryClipChangedListener(OnPrimaryClipChangedListener what) { mListeners.add(what); }
    public void removePrimaryClipChangedListener(OnPrimaryClipChangedListener what) { mListeners.remove(what); }

    @Deprecated
    public CharSequence getText() {
        ClipData clip = getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) return clip.getItemAt(0).coerceToText(null);
        return null;
    }

    @Deprecated
    public void setText(CharSequence text) { setPrimaryClip(ClipData.newPlainText(null, text)); }

    @Deprecated
    public boolean hasText() {
        ClipData clip = getPrimaryClip();
        return clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null;
    }
}
