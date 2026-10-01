package android.view;

/** Contextual mode of the user interface. Concrete modes come from the window decor (TODO(WS4)). */
public abstract class ActionMode {
    public static final int DEFAULT_HIDE_DURATION = -1;
    public static final int TYPE_FLOATING = 1;
    public static final int TYPE_PRIMARY = 0;

    private Object mTag;
    private boolean mTitleOptionalHint;
    private int mType = TYPE_PRIMARY;

    public ActionMode() {}

    public void setTag(Object tag) { mTag = tag; }
    public Object getTag() { return mTag; }
    public abstract void setTitle(CharSequence title);
    public abstract void setTitle(int resId);
    public abstract void setSubtitle(CharSequence subtitle);
    public abstract void setSubtitle(int resId);
    public void setTitleOptionalHint(boolean titleOptional) { mTitleOptionalHint = titleOptional; }
    public boolean getTitleOptionalHint() { return mTitleOptionalHint; }
    public boolean isTitleOptional() { return false; }
    public abstract void setCustomView(View view);
    public void setType(int type) { mType = type; }
    public int getType() { return mType; }
    public abstract void invalidate();
    public void invalidateContentRect() {}
    public void hide(long duration) {}
    public abstract void finish();
    public abstract Menu getMenu();
    public abstract CharSequence getTitle();
    public abstract CharSequence getSubtitle();
    public abstract View getCustomView();
    public abstract MenuInflater getMenuInflater();
    public void onWindowFocusChanged(boolean hasWindowFocus) {}

    public interface Callback {
        boolean onCreateActionMode(ActionMode mode, Menu menu);
        boolean onPrepareActionMode(ActionMode mode, Menu menu);
        boolean onActionItemClicked(ActionMode mode, MenuItem item);
        void onDestroyActionMode(ActionMode mode);
    }

    public static abstract class Callback2 implements ActionMode.Callback {
        public Callback2() {}

        public void onGetContentRect(ActionMode mode, View view, android.graphics.Rect outRect) {
            if (view != null) outRect.set(0, 0, view.getWidth(), view.getHeight());
            else outRect.set(0, 0, 0, 0);
        }
    }
}
