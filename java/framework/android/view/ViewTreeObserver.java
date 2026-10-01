package android.view;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Global view tree notifications, following AOSP ViewTreeObserver (listeners copied on dispatch). */
public final class ViewTreeObserver {
    private ArrayList<OnWindowAttachListener> mOnWindowAttachListeners;
    private ArrayList<OnWindowFocusChangeListener> mOnWindowFocusListeners;
    private ArrayList<OnWindowVisibilityChangeListener> mOnWindowVisibilityListeners;
    private ArrayList<OnGlobalFocusChangeListener> mOnGlobalFocusListeners;
    private ArrayList<OnTouchModeChangeListener> mOnTouchModeChangeListeners;
    private ArrayList<OnGlobalLayoutListener> mOnGlobalLayoutListeners;
    private ArrayList<OnScrollChangedListener> mOnScrollChangedListeners;
    private ArrayList<OnPreDrawListener> mOnPreDrawListeners;
    private ArrayList<OnDrawListener> mOnDrawListeners;
    private ArrayList<Runnable> mOnFrameCommitListeners;
    private ArrayList<Consumer<List<Rect>>> mGestureExclusionListeners;
    private boolean mAlive = true;
    private boolean mInDispatchOnDraw;

    public interface OnWindowAttachListener {
        void onWindowAttached();
        void onWindowDetached();
    }

    public interface OnWindowFocusChangeListener {
        void onWindowFocusChanged(boolean hasFocus);
    }

    public interface OnWindowVisibilityChangeListener {
        void onWindowVisibilityChanged(int visibility);
    }

    public interface OnGlobalFocusChangeListener {
        void onGlobalFocusChanged(View oldFocus, View newFocus);
    }

    public interface OnGlobalLayoutListener {
        void onGlobalLayout();
    }

    public interface OnPreDrawListener {
        boolean onPreDraw();
    }

    public interface OnDrawListener {
        void onDraw();
    }

    public interface OnTouchModeChangeListener {
        void onTouchModeChanged(boolean isInTouchMode);
    }

    public interface OnScrollChangedListener {
        void onScrollChanged();
    }

    /** framework-internal (hidden in AOSP). */
    public ViewTreeObserver(android.content.Context context) {}

    /** framework-internal. Moves the listeners of a floating observer into this one. */
    void merge(ViewTreeObserver observer) {
        mOnWindowAttachListeners = mergeList(mOnWindowAttachListeners, observer.mOnWindowAttachListeners);
        mOnWindowFocusListeners = mergeList(mOnWindowFocusListeners, observer.mOnWindowFocusListeners);
        mOnWindowVisibilityListeners = mergeList(mOnWindowVisibilityListeners, observer.mOnWindowVisibilityListeners);
        mOnGlobalFocusListeners = mergeList(mOnGlobalFocusListeners, observer.mOnGlobalFocusListeners);
        mOnTouchModeChangeListeners = mergeList(mOnTouchModeChangeListeners, observer.mOnTouchModeChangeListeners);
        mOnGlobalLayoutListeners = mergeList(mOnGlobalLayoutListeners, observer.mOnGlobalLayoutListeners);
        mOnScrollChangedListeners = mergeList(mOnScrollChangedListeners, observer.mOnScrollChangedListeners);
        mOnPreDrawListeners = mergeList(mOnPreDrawListeners, observer.mOnPreDrawListeners);
        mOnDrawListeners = mergeList(mOnDrawListeners, observer.mOnDrawListeners);
        mOnFrameCommitListeners = mergeList(mOnFrameCommitListeners, observer.mOnFrameCommitListeners);
        observer.kill();
    }

    private static <T> ArrayList<T> mergeList(ArrayList<T> into, ArrayList<T> from) {
        if (from == null || from.isEmpty()) return into;
        if (into == null) return new ArrayList<T>(from);
        into.addAll(from);
        return into;
    }

    private static <T> ArrayList<T> add(ArrayList<T> list, T l) {
        if (l == null) throw new NullPointerException("listener");
        if (list == null) list = new ArrayList<T>();
        list.add(l);
        return list;
    }

    private static <T> void remove(ArrayList<T> list, T l) {
        if (list != null) list.remove(l);
    }

    private static <T> ArrayList<T> snapshot(ArrayList<T> list) {
        return list == null || list.isEmpty() ? null : new ArrayList<T>(list);
    }

    public void addOnWindowAttachListener(OnWindowAttachListener l) {
        checkIsAlive();
        mOnWindowAttachListeners = add(mOnWindowAttachListeners, l);
    }

    public void removeOnWindowAttachListener(OnWindowAttachListener l) {
        checkIsAlive();
        remove(mOnWindowAttachListeners, l);
    }

    public void addOnWindowFocusChangeListener(OnWindowFocusChangeListener l) {
        checkIsAlive();
        mOnWindowFocusListeners = add(mOnWindowFocusListeners, l);
    }

    public void removeOnWindowFocusChangeListener(OnWindowFocusChangeListener l) {
        checkIsAlive();
        remove(mOnWindowFocusListeners, l);
    }

    public void addOnWindowVisibilityChangeListener(OnWindowVisibilityChangeListener l) {
        checkIsAlive();
        mOnWindowVisibilityListeners = add(mOnWindowVisibilityListeners, l);
    }

    public void removeOnWindowVisibilityChangeListener(OnWindowVisibilityChangeListener l) {
        checkIsAlive();
        remove(mOnWindowVisibilityListeners, l);
    }

    public void addOnGlobalFocusChangeListener(OnGlobalFocusChangeListener l) {
        checkIsAlive();
        mOnGlobalFocusListeners = add(mOnGlobalFocusListeners, l);
    }

    public void removeOnGlobalFocusChangeListener(OnGlobalFocusChangeListener l) {
        checkIsAlive();
        remove(mOnGlobalFocusListeners, l);
    }

    public void addOnGlobalLayoutListener(OnGlobalLayoutListener l) {
        checkIsAlive();
        mOnGlobalLayoutListeners = add(mOnGlobalLayoutListeners, l);
    }

    @Deprecated
    public void removeGlobalOnLayoutListener(OnGlobalLayoutListener l) { removeOnGlobalLayoutListener(l); }

    public void removeOnGlobalLayoutListener(OnGlobalLayoutListener l) {
        checkIsAlive();
        remove(mOnGlobalLayoutListeners, l);
    }

    public void addOnPreDrawListener(OnPreDrawListener l) {
        checkIsAlive();
        mOnPreDrawListeners = add(mOnPreDrawListeners, l);
    }

    public void removeOnPreDrawListener(OnPreDrawListener l) {
        checkIsAlive();
        remove(mOnPreDrawListeners, l);
    }

    public void addOnDrawListener(OnDrawListener l) {
        checkIsAlive();
        if (mInDispatchOnDraw) {
            throw new IllegalStateException("Cannot call addOnDrawListener inside of onDraw");
        }
        mOnDrawListeners = add(mOnDrawListeners, l);
    }

    public void removeOnDrawListener(OnDrawListener l) {
        checkIsAlive();
        if (mInDispatchOnDraw) {
            throw new IllegalStateException("Cannot call removeOnDrawListener inside of onDraw");
        }
        remove(mOnDrawListeners, l);
    }

    public void registerFrameCommitCallback(Runnable callback) {
        checkIsAlive();
        mOnFrameCommitListeners = add(mOnFrameCommitListeners, callback);
    }

    public boolean unregisterFrameCommitCallback(Runnable callback) {
        checkIsAlive();
        return mOnFrameCommitListeners != null && mOnFrameCommitListeners.remove(callback);
    }

    public void addOnScrollChangedListener(OnScrollChangedListener l) {
        checkIsAlive();
        mOnScrollChangedListeners = add(mOnScrollChangedListeners, l);
    }

    public void removeOnScrollChangedListener(OnScrollChangedListener l) {
        checkIsAlive();
        remove(mOnScrollChangedListeners, l);
    }

    public void addOnTouchModeChangeListener(OnTouchModeChangeListener l) {
        checkIsAlive();
        mOnTouchModeChangeListeners = add(mOnTouchModeChangeListeners, l);
    }

    public void removeOnTouchModeChangeListener(OnTouchModeChangeListener l) {
        checkIsAlive();
        remove(mOnTouchModeChangeListeners, l);
    }

    public void addOnSystemGestureExclusionRectsChangedListener(Consumer<List<Rect>> listener) {
        checkIsAlive();
        mGestureExclusionListeners = add(mGestureExclusionListeners, listener);
    }

    public void removeOnSystemGestureExclusionRectsChangedListener(Consumer<List<Rect>> listener) {
        checkIsAlive();
        remove(mGestureExclusionListeners, listener);
    }

    private void checkIsAlive() {
        if (!mAlive) {
            throw new IllegalStateException("This ViewTreeObserver is not alive, call getViewTreeObserver() again");
        }
    }

    public boolean isAlive() { return mAlive; }

    private void kill() { mAlive = false; }

    final void dispatchOnWindowAttachedChange(boolean attached) {
        ArrayList<OnWindowAttachListener> ls = snapshot(mOnWindowAttachListeners);
        if (ls == null) return;
        for (OnWindowAttachListener l : ls) {
            if (attached) l.onWindowAttached();
            else l.onWindowDetached();
        }
    }

    final void dispatchOnWindowFocusChange(boolean hasFocus) {
        ArrayList<OnWindowFocusChangeListener> ls = snapshot(mOnWindowFocusListeners);
        if (ls == null) return;
        for (OnWindowFocusChangeListener l : ls) l.onWindowFocusChanged(hasFocus);
    }

    /** framework-internal (hidden in AOSP). */
    public void dispatchOnWindowVisibilityChange(int visibility) {
        ArrayList<OnWindowVisibilityChangeListener> ls = snapshot(mOnWindowVisibilityListeners);
        if (ls == null) return;
        for (OnWindowVisibilityChangeListener l : ls) l.onWindowVisibilityChanged(visibility);
    }

    final void dispatchOnGlobalFocusChange(View oldFocus, View newFocus) {
        ArrayList<OnGlobalFocusChangeListener> ls = snapshot(mOnGlobalFocusListeners);
        if (ls == null) return;
        for (OnGlobalFocusChangeListener l : ls) l.onGlobalFocusChanged(oldFocus, newFocus);
    }

    public final void dispatchOnGlobalLayout() {
        ArrayList<OnGlobalLayoutListener> ls = snapshot(mOnGlobalLayoutListeners);
        if (ls == null) return;
        for (OnGlobalLayoutListener l : ls) l.onGlobalLayout();
    }

    final boolean hasOnPreDrawListeners() { return mOnPreDrawListeners != null && !mOnPreDrawListeners.isEmpty(); }

    @SuppressWarnings("unchecked")
    public final boolean dispatchOnPreDraw() {
        boolean cancelDraw = false;
        ArrayList<OnPreDrawListener> ls = snapshot(mOnPreDrawListeners);
        if (ls == null) return false;
        for (OnPreDrawListener l : ls) cancelDraw |= !l.onPreDraw();
        return cancelDraw;
    }

    public final void dispatchOnDraw() {
        ArrayList<OnDrawListener> ls = snapshot(mOnDrawListeners);
        if (ls == null) return;
        mInDispatchOnDraw = true;
        try {
            for (OnDrawListener l : ls) l.onDraw();
        } finally {
            mInDispatchOnDraw = false;
        }
    }

    final void dispatchOnFrameCommit() {
        ArrayList<Runnable> ls = mOnFrameCommitListeners;
        if (ls == null || ls.isEmpty()) return;
        mOnFrameCommitListeners = null;
        for (Runnable r : ls) r.run();
    }

    final void dispatchOnTouchModeChanged(boolean inTouchMode) {
        ArrayList<OnTouchModeChangeListener> ls = snapshot(mOnTouchModeChangeListeners);
        if (ls == null) return;
        for (OnTouchModeChangeListener l : ls) l.onTouchModeChanged(inTouchMode);
    }

    final void dispatchOnScrollChanged() {
        ArrayList<OnScrollChangedListener> ls = snapshot(mOnScrollChangedListeners);
        if (ls == null) return;
        for (OnScrollChangedListener l : ls) l.onScrollChanged();
    }
}
