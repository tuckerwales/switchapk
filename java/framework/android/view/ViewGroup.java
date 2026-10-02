package android.view;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

/**
 * A view that contains other views, ported from AOSP ViewGroup: child
 * management, touch dispatch with touch targets, interception and split
 * motion events, key and focus routing, focus traversal, drawing order (Z
 * then custom order), clipping, invalidation propagation and layout params.
 */
public abstract class ViewGroup extends View implements ViewParent, ViewManager {
    private static final String TAG = "ViewGroup";

    protected static final int CLIP_TO_PADDING_MASK = 34;
    public static final int FOCUS_AFTER_DESCENDANTS = 262144;
    public static final int FOCUS_BEFORE_DESCENDANTS = 131072;
    public static final int FOCUS_BLOCK_DESCENDANTS = 393216;
    public static final int LAYOUT_MODE_CLIP_BOUNDS = 0;
    public static final int LAYOUT_MODE_OPTICAL_BOUNDS = 1;
    public static final int PERSISTENT_ALL_CACHES = 3;
    public static final int PERSISTENT_ANIMATION_CACHE = 1;
    public static final int PERSISTENT_NO_CACHE = 0;
    public static final int PERSISTENT_SCROLLING_CACHE = 2;

    static final int FLAG_CLIP_CHILDREN = 0x1;
    static final int FLAG_CLIP_TO_PADDING = 0x2;
    static final int FLAG_PADDING_NOT_NULL = 0x20;
    static final int FLAG_USE_CHILD_DRAWING_ORDER = 0x400;
    static final int FLAG_SUPPORT_STATIC_TRANSFORMATIONS = 0x800;
    static final int FLAG_ADD_STATES_FROM_CHILDREN = 0x2000;
    static final int FLAG_ALWAYS_DRAWN_WITH_CACHE = 0x4000;
    static final int FLAG_CHILDREN_DRAWN_WITH_CACHE = 0x8000;
    static final int FLAG_NOTIFY_CHILDREN_ON_DRAWABLE_STATE_CHANGE = 0x10000;
    static final int FLAG_MASK_FOCUSABILITY = 0x60000;
    static final int FLAG_DISALLOW_INTERCEPT = 0x80000;
    static final int FLAG_SPLIT_MOTION_EVENTS = 0x200000;
    static final int FLAG_PREVENT_DISPATCH_ATTACHED_TO_WINDOW = 0x400000;
    static final int FLAG_IS_TRANSITION_GROUP = 0x1000000;
    static final int FLAG_IS_TRANSITION_GROUP_SET = 0x2000000;
    static final int FLAG_TOUCHSCREEN_BLOCKS_FOCUS = 0x4000000;
    static final int FLAG_ANIMATION_CACHE = 0x40;

    private static final int[] DESCENDANT_FOCUSABILITY_FLAGS = {FOCUS_BEFORE_DESCENDANTS, FOCUS_AFTER_DESCENDANTS,
            FOCUS_BLOCK_DESCENDANTS};

    /** AOSP name. */
    protected int mGroupFlags;
    private int mPersistentDrawingCache = PERSISTENT_SCROLLING_CACHE;
    private int mLayoutMode = -1;

    private View[] mChildren = new View[12];
    private int mChildrenCount;

    /** AOSP name: the focused child (or the child containing the focused view). */
    View mFocused;
    private View mDefaultFocus;
    private View mFocusedInCluster;

    private TouchTarget mFirstTouchTarget;
    private boolean mSuppressLayout;
    private boolean mLayoutCalledWhileSuppressed;
    private int mNestedScrollAxes;
    private int mChildCountWithTransientState;
    private ArrayList<View> mTransitioningViews;
    private ArrayList<View> mPreSortedChildren;

    protected OnHierarchyChangeListener mOnHierarchyChangeListener;

    public interface OnHierarchyChangeListener {
        void onChildViewAdded(View parent, View child);
        void onChildViewRemoved(View parent, View child);
    }

    private static final int[] VIEWGROUP_ATTRS = {
        android.R.attr.clipChildren, android.R.attr.clipToPadding, android.R.attr.animationCache,
        android.R.attr.persistentDrawingCache, android.R.attr.addStatesFromChildren,
        android.R.attr.alwaysDrawnWithCache, android.R.attr.descendantFocusability,
        android.R.attr.splitMotionEvents, android.R.attr.layoutMode, android.R.attr.transitionGroup,
        android.R.attr.touchscreenBlocksFocus,
    };

    public ViewGroup(Context context) { this(context, null); }

    public ViewGroup(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public ViewGroup(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public ViewGroup(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initViewGroup();
        if (attrs != null || defStyleAttr != 0 || defStyleRes != 0) {
            initFromAttributes(context, attrs, defStyleAttr, defStyleRes);
        }
    }

    private void initViewGroup() {
        setFlags(WILL_NOT_DRAW, DRAW_MASK);
        mGroupFlags |= FLAG_CLIP_CHILDREN | FLAG_CLIP_TO_PADDING | FLAG_ANIMATION_CACHE | FLAG_ALWAYS_DRAWN_WITH_CACHE
                | FLAG_SPLIT_MOTION_EVENTS;
        setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);
        updatePaddingFlag();
    }

    private void initFromAttributes(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        final TypedArray a = context.obtainStyledAttributes(attrs, VIEWGROUP_ATTRS, defStyleAttr, defStyleRes);
        final int n = a.getIndexCount();
        for (int i = 0; i < n; i++) {
            int index = a.getIndex(i);
            switch (VIEWGROUP_ATTRS[index]) {
                case android.R.attr.clipChildren:
                    setClipChildren(a.getBoolean(index, true));
                    break;
                case android.R.attr.clipToPadding:
                    setClipToPadding(a.getBoolean(index, true));
                    break;
                case android.R.attr.animationCache:
                    setAnimationCacheEnabled(a.getBoolean(index, true));
                    break;
                case android.R.attr.persistentDrawingCache:
                    setPersistentDrawingCache(a.getInt(index, PERSISTENT_SCROLLING_CACHE));
                    break;
                case android.R.attr.addStatesFromChildren:
                    setAddStatesFromChildren(a.getBoolean(index, false));
                    break;
                case android.R.attr.alwaysDrawnWithCache:
                    setAlwaysDrawnWithCacheEnabled(a.getBoolean(index, true));
                    break;
                case android.R.attr.descendantFocusability:
                    setDescendantFocusability(DESCENDANT_FOCUSABILITY_FLAGS[a.getInt(index, 0)]);
                    break;
                case android.R.attr.splitMotionEvents:
                    setMotionEventSplittingEnabled(a.getBoolean(index, false));
                    break;
                case android.R.attr.layoutMode: {
                    int layoutMode = a.getInt(index, -1);
                    if (layoutMode != -1) setLayoutMode(layoutMode);
                    break;
                }
                case android.R.attr.transitionGroup:
                    setTransitionGroup(a.getBoolean(index, false));
                    break;
                case android.R.attr.touchscreenBlocksFocus:
                    setTouchscreenBlocksFocus(a.getBoolean(index, false));
                    break;
            }
        }
        a.recycle();
    }

    // ---------------------------------------------------------------- flags and settings

    public int getDescendantFocusability() { return mGroupFlags & FLAG_MASK_FOCUSABILITY; }

    public void setDescendantFocusability(int focusability) {
        switch (focusability) {
            case FOCUS_BEFORE_DESCENDANTS:
            case FOCUS_AFTER_DESCENDANTS:
            case FOCUS_BLOCK_DESCENDANTS:
                break;
            default:
                throw new IllegalArgumentException("must be one of FOCUS_BEFORE_DESCENDANTS, "
                        + "FOCUS_AFTER_DESCENDANTS, FOCUS_BLOCK_DESCENDANTS");
        }
        mGroupFlags &= ~FLAG_MASK_FOCUSABILITY;
        mGroupFlags |= (focusability & FLAG_MASK_FOCUSABILITY);
    }

    public boolean getClipChildren() { return (mGroupFlags & FLAG_CLIP_CHILDREN) != 0; }

    public void setClipChildren(boolean clipChildren) {
        boolean previousValue = (mGroupFlags & FLAG_CLIP_CHILDREN) == FLAG_CLIP_CHILDREN;
        if (clipChildren != previousValue) {
            setBooleanFlag(FLAG_CLIP_CHILDREN, clipChildren);
            invalidate(true);
        }
    }

    public boolean getClipToPadding() { return hasBooleanFlag(FLAG_CLIP_TO_PADDING); }

    public void setClipToPadding(boolean clipToPadding) {
        if (hasBooleanFlag(FLAG_CLIP_TO_PADDING) != clipToPadding) {
            setBooleanFlag(FLAG_CLIP_TO_PADDING, clipToPadding);
            invalidate(true);
        }
    }

    private boolean hasBooleanFlag(int flag) { return (mGroupFlags & flag) == flag; }

    private void setBooleanFlag(int flag, boolean value) {
        if (value) mGroupFlags |= flag;
        else mGroupFlags &= ~flag;
    }

    public void setAddStatesFromChildren(boolean addsStates) {
        if (addsStates) mGroupFlags |= FLAG_ADD_STATES_FROM_CHILDREN;
        else mGroupFlags &= ~FLAG_ADD_STATES_FROM_CHILDREN;
        refreshDrawableState();
    }

    public boolean addStatesFromChildren() { return (mGroupFlags & FLAG_ADD_STATES_FROM_CHILDREN) != 0; }

    public void setMotionEventSplittingEnabled(boolean split) {
        if (split) mGroupFlags |= FLAG_SPLIT_MOTION_EVENTS;
        else mGroupFlags &= ~FLAG_SPLIT_MOTION_EVENTS;
    }

    public boolean isMotionEventSplittingEnabled() { return (mGroupFlags & FLAG_SPLIT_MOTION_EVENTS) == FLAG_SPLIT_MOTION_EVENTS; }

    public boolean isTransitionGroup() {
        if ((mGroupFlags & FLAG_IS_TRANSITION_GROUP_SET) != 0) return (mGroupFlags & FLAG_IS_TRANSITION_GROUP) != 0;
        return getBackground() != null || getTransitionName() != null;
    }

    public void setTransitionGroup(boolean isTransitionGroup) {
        mGroupFlags |= FLAG_IS_TRANSITION_GROUP_SET;
        if (isTransitionGroup) mGroupFlags |= FLAG_IS_TRANSITION_GROUP;
        else mGroupFlags &= ~FLAG_IS_TRANSITION_GROUP;
    }

    public void setTouchscreenBlocksFocus(boolean touchscreenBlocksFocus) {
        if (touchscreenBlocksFocus) mGroupFlags |= FLAG_TOUCHSCREEN_BLOCKS_FOCUS;
        else mGroupFlags &= ~FLAG_TOUCHSCREEN_BLOCKS_FOCUS;
    }

    public boolean getTouchscreenBlocksFocus() { return (mGroupFlags & FLAG_TOUCHSCREEN_BLOCKS_FOCUS) != 0; }

    boolean shouldBlockFocusForTouchscreen() { return getTouchscreenBlocksFocus() && false; }

    public boolean isAlwaysDrawnWithCacheEnabled() { return (mGroupFlags & FLAG_ALWAYS_DRAWN_WITH_CACHE) != 0; }

    public void setAlwaysDrawnWithCacheEnabled(boolean always) { setBooleanFlag(FLAG_ALWAYS_DRAWN_WITH_CACHE, always); }

    protected boolean isChildrenDrawnWithCacheEnabled() { return (mGroupFlags & FLAG_CHILDREN_DRAWN_WITH_CACHE) != 0; }

    protected void setChildrenDrawnWithCacheEnabled(boolean enabled) { setBooleanFlag(FLAG_CHILDREN_DRAWN_WITH_CACHE, enabled); }

    protected void setChildrenDrawingCacheEnabled(boolean enabled) {}

    public boolean isAnimationCacheEnabled() { return (mGroupFlags & FLAG_ANIMATION_CACHE) != 0; }

    public void setAnimationCacheEnabled(boolean enabled) { setBooleanFlag(FLAG_ANIMATION_CACHE, enabled); }

    public int getPersistentDrawingCache() { return mPersistentDrawingCache; }

    public void setPersistentDrawingCache(int drawingCacheToKeep) { mPersistentDrawingCache = drawingCacheToKeep & PERSISTENT_ALL_CACHES; }

    public int getLayoutMode() { return mLayoutMode == -1 ? LAYOUT_MODE_CLIP_BOUNDS : mLayoutMode; }

    public void setLayoutMode(int layoutMode) {
        if (mLayoutMode != layoutMode) {
            mLayoutMode = layoutMode;
            requestLayout();
        }
    }

    protected boolean isChildrenDrawingOrderEnabled() { return (mGroupFlags & FLAG_USE_CHILD_DRAWING_ORDER) == FLAG_USE_CHILD_DRAWING_ORDER; }

    protected void setChildrenDrawingOrderEnabled(boolean enabled) { setBooleanFlag(FLAG_USE_CHILD_DRAWING_ORDER, enabled); }

    protected int getChildDrawingOrder(int childCount, int drawingPosition) { return drawingPosition; }

    public final int getChildDrawingOrder(int drawingPosition) { return getChildDrawingOrder(getChildCount(), drawingPosition); }

    protected void setStaticTransformationsEnabled(boolean enabled) { setBooleanFlag(FLAG_SUPPORT_STATIC_TRANSFORMATIONS, enabled); }

    public boolean shouldDelayChildPressedState() { return true; }

    protected boolean canAnimate() { return false; }

    public void startLayoutAnimation() {}

    public void scheduleLayoutAnimation() {}

    public void clearDisappearingChildren() {}

    public void startViewTransition(View view) {
        if (view.mParent == this) {
            if (mTransitioningViews == null) mTransitioningViews = new ArrayList<View>();
            mTransitioningViews.add(view);
        }
    }

    public void endViewTransition(View view) {
        if (mTransitioningViews != null) mTransitioningViews.remove(view);
    }

    /** framework-internal (hidden in AOSP). */
    boolean isViewTransitioning(View view) { return mTransitioningViews != null && mTransitioningViews.contains(view); }

    public void suppressLayout(boolean suppress) {
        mSuppressLayout = suppress;
        if (!suppress && mLayoutCalledWhileSuppressed) {
            requestLayout();
            mLayoutCalledWhileSuppressed = false;
        }
    }

    public boolean isLayoutSuppressed() { return mSuppressLayout; }

    @Override
    protected void internalSetPadding(int left, int top, int right, int bottom) {
        super.internalSetPadding(left, top, right, bottom);
        updatePaddingFlag();
    }

    private void updatePaddingFlag() {
        if ((mPaddingLeft | mPaddingTop | mPaddingRight | mPaddingBottom) != 0) mGroupFlags |= FLAG_PADDING_NOT_NULL;
        else mGroupFlags &= ~FLAG_PADDING_NOT_NULL;
    }

    // ---------------------------------------------------------------- children

    public int getChildCount() { return mChildrenCount; }

    public View getChildAt(int index) {
        if (index < 0 || index >= mChildrenCount) return null;
        return mChildren[index];
    }

    public int indexOfChild(View child) {
        final int count = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) {
            if (children[i] == child) return i;
        }
        return -1;
    }

    public View getFocusedChild() { return mFocused; }

    public void setOnHierarchyChangeListener(OnHierarchyChangeListener listener) { mOnHierarchyChangeListener = listener; }

    public void onViewAdded(View child) {}

    public void onViewRemoved(View child) {}

    void dispatchViewAdded(View child) {
        onViewAdded(child);
        if (mOnHierarchyChangeListener != null) mOnHierarchyChangeListener.onChildViewAdded(this, child);
    }

    void dispatchViewRemoved(View child) {
        onViewRemoved(child);
        if (mOnHierarchyChangeListener != null) mOnHierarchyChangeListener.onChildViewRemoved(this, child);
    }

    public void addView(View child) { addView(child, -1); }

    public void addView(View child, int index) {
        if (child == null) throw new IllegalArgumentException("Cannot add a null child view to a ViewGroup");
        LayoutParams params = child.getLayoutParams();
        if (params == null) {
            params = generateDefaultLayoutParams();
            if (params == null) {
                throw new IllegalArgumentException("generateDefaultLayoutParams() cannot return null");
            }
        }
        addView(child, index, params);
    }

    public void addView(View child, int width, int height) {
        final LayoutParams params = generateDefaultLayoutParams();
        params.width = width;
        params.height = height;
        addView(child, -1, params);
    }

    public void addView(View child, LayoutParams params) { addView(child, -1, params); }

    public void addView(View child, int index, LayoutParams params) {
        if (child == null) throw new IllegalArgumentException("Cannot add a null child view to a ViewGroup");
        requestLayout();
        invalidate(true);
        addViewInner(child, index, params, false);
    }

    public void updateViewLayout(View view, LayoutParams params) {
        if (!checkLayoutParams(params)) throw new IllegalArgumentException("Invalid LayoutParams supplied to " + this);
        if (view.mParent != this) throw new IllegalArgumentException("Given view not a child of " + this);
        view.setLayoutParams(params);
    }

    protected boolean checkLayoutParams(LayoutParams p) { return p != null; }

    protected boolean addViewInLayout(View child, int index, LayoutParams params) {
        return addViewInLayout(child, index, params, false);
    }

    protected boolean addViewInLayout(View child, int index, LayoutParams params, boolean preventRequestLayout) {
        if (child == null) throw new IllegalArgumentException("Cannot add a null child view to a ViewGroup");
        child.mParent = null;
        addViewInner(child, index, params, preventRequestLayout);
        child.mPrivateFlags = (child.mPrivateFlags & ~PFLAG_DIRTY) | PFLAG_DRAWN;
        return true;
    }

    protected void cleanupLayoutState(View child) { child.mPrivateFlags &= ~View.PFLAG_FORCE_LAYOUT; }

    private void addViewInner(View child, int index, LayoutParams params, boolean preventRequestLayout) {
        if (child.getParent() != null) {
            throw new IllegalStateException("The specified child already has a parent. "
                    + "You must call removeView() on the child's parent first.");
        }
        if (!checkLayoutParams(params)) params = generateLayoutParams(params);
        if (preventRequestLayout) child.mLayoutParams = params;
        else child.setLayoutParams(params);
        if (index < 0) index = mChildrenCount;
        addInArray(child, index);
        child.mParent = this;
        if (child.mLayoutParams != null) child.mLayoutParams.resolveLayoutDirection(child.getLayoutDirection());
        final boolean childHasFocus = child.hasFocus();
        if (childHasFocus) requestChildFocus(child, child.findFocus());
        AttachInfo ai = mAttachInfo;
        if (ai != null && (mGroupFlags & FLAG_PREVENT_DISPATCH_ATTACHED_TO_WINDOW) == 0) {
            child.dispatchAttachedToWindow(mAttachInfo, (mViewFlags & VISIBILITY_MASK));
        }
        dispatchViewAdded(child);
        if ((child.mViewFlags & DUPLICATE_PARENT_STATE) == DUPLICATE_PARENT_STATE) {
            mGroupFlags |= FLAG_NOTIFY_CHILDREN_ON_DRAWABLE_STATE_CHANGE;
        }
        if (child.hasTransientState()) childHasTransientStateChanged(child, true);
        if (child.isFocusedByDefault()) setDefaultFocus(child);
    }

    private void addInArray(View child, int index) {
        View[] children = mChildren;
        final int count = mChildrenCount;
        final int size = children.length;
        if (index == count) {
            if (size == count) {
                mChildren = new View[size + 12];
                System.arraycopy(children, 0, mChildren, 0, size);
                children = mChildren;
            }
            children[mChildrenCount++] = child;
        } else if (index < count) {
            if (size == count) {
                mChildren = new View[size + 12];
                System.arraycopy(children, 0, mChildren, 0, index);
                System.arraycopy(children, index, mChildren, index + 1, count - index);
                children = mChildren;
            } else {
                System.arraycopy(children, index, children, index + 1, count - index);
            }
            children[index] = child;
            mChildrenCount++;
        } else {
            throw new IndexOutOfBoundsException("index=" + index + " count=" + count);
        }
    }

    private void removeFromArray(int index) {
        final View[] children = mChildren;
        final int count = mChildrenCount;
        if (index == count - 1) {
            children[--mChildrenCount] = null;
        } else if (index >= 0 && index < count) {
            System.arraycopy(children, index + 1, children, index, count - index - 1);
            children[--mChildrenCount] = null;
        } else {
            throw new IndexOutOfBoundsException();
        }
    }

    private void removeFromArray(int start, int count) {
        final View[] children = mChildren;
        final int childrenCount = mChildrenCount;
        start = Math.max(0, start);
        final int end = Math.min(childrenCount, start + count);
        if (start == end) return;
        if (end == childrenCount) {
            for (int i = start; i < end; i++) children[i] = null;
        } else {
            System.arraycopy(children, end, children, start, childrenCount - end);
            for (int i = childrenCount - (end - start); i < childrenCount; i++) children[i] = null;
        }
        mChildrenCount -= (end - start);
    }

    public void removeView(View view) {
        if (removeViewInternal(view)) {
            requestLayout();
            invalidate(true);
        }
    }

    public void removeViewInLayout(View view) { removeViewInternal(view); }

    public void removeViewsInLayout(int start, int count) { removeViewsInternal(start, count); }

    public void removeViewAt(int index) {
        removeViewInternal(index, getChildAt(index));
        requestLayout();
        invalidate(true);
    }

    public void removeViews(int start, int count) {
        removeViewsInternal(start, count);
        requestLayout();
        invalidate(true);
    }

    private boolean removeViewInternal(View view) {
        final int index = indexOfChild(view);
        if (index >= 0) {
            removeViewInternal(index, view);
            return true;
        }
        return false;
    }

    private void removeViewInternal(int index, View view) {
        if (view == null) return;
        boolean clearChildFocus = false;
        if (view == mFocused) {
            view.unFocus(null);
            clearChildFocus = true;
        }
        cancelTouchTarget(view);
        if (view.mAttachInfo != null) view.dispatchDetachedFromWindow();
        if (view.hasTransientState()) childHasTransientStateChanged(view, false);
        removeFromArray(index);
        view.mParent = null;
        if (view == mDefaultFocus) clearDefaultFocus(view);
        if (clearChildFocus) {
            clearChildFocus(view);
            if (!rootViewRequestFocus()) notifyGlobalFocusCleared(this);
        }
        dispatchViewRemoved(view);
    }

    private void removeViewsInternal(int start, int count) {
        final int end = Math.min(start + count, mChildrenCount);
        boolean clearChildFocus = false;
        View focused = mFocused;
        final boolean detach = mAttachInfo != null;
        final View[] children = mChildren;
        for (int i = start; i < end; i++) {
            final View view = children[i];
            if (view == focused) {
                view.unFocus(null);
                clearChildFocus = true;
            }
            if (view == mDefaultFocus) clearDefaultFocus(view);
            cancelTouchTarget(view);
            if (detach) view.dispatchDetachedFromWindow();
            if (view.hasTransientState()) childHasTransientStateChanged(view, false);
            view.mParent = null;
            dispatchViewRemoved(view);
        }
        removeFromArray(start, end - start);
        if (clearChildFocus) {
            clearChildFocus(focused);
            if (!rootViewRequestFocus()) notifyGlobalFocusCleared(focused);
        }
    }

    public void removeAllViews() {
        removeAllViewsInLayout();
        requestLayout();
        invalidate(true);
    }

    public void removeAllViewsInLayout() {
        final int count = mChildrenCount;
        if (count <= 0) return;
        removeViewsInternal(0, count);
    }

    protected void removeDetachedView(View child, boolean animate) {
        if (child == mFocused) child.clearFocus();
        if (child == mDefaultFocus) clearDefaultFocus(child);
        cancelTouchTarget(child);
        if (child.mAttachInfo != null) child.dispatchDetachedFromWindow();
        if (child.hasTransientState()) childHasTransientStateChanged(child, false);
        dispatchViewRemoved(child);
    }

    protected void attachViewToParent(View child, int index, LayoutParams params) {
        child.mLayoutParams = params;
        if (index < 0) index = mChildrenCount;
        addInArray(child, index);
        child.mParent = this;
        child.mPrivateFlags = (child.mPrivateFlags & ~PFLAG_DIRTY & ~PFLAG_DRAWING_CACHE_VALID) | PFLAG_DRAWN
                | PFLAG_INVALIDATED;
        this.mPrivateFlags |= PFLAG_INVALIDATED;
        if (child.hasFocus()) requestChildFocus(child, child.findFocus());
        if (child.mAttachInfo == null && mAttachInfo != null) {
            child.dispatchAttachedToWindow(mAttachInfo, mViewFlags & VISIBILITY_MASK);
        }
    }

    protected void detachViewFromParent(View child) { removeFromArray(indexOfChild(child)); child.mParent = null; }

    protected void detachViewFromParent(int index) {
        View child = mChildren[index];
        removeFromArray(index);
        if (child != null) child.mParent = null;
    }

    protected void detachViewsFromParent(int start, int count) {
        for (int i = start; i < Math.min(start + count, mChildrenCount); i++) mChildren[i].mParent = null;
        removeFromArray(start, count);
    }

    protected void detachAllViewsFromParent() {
        final int count = mChildrenCount;
        if (count <= 0) return;
        final View[] children = mChildren;
        mChildrenCount = 0;
        for (int i = count - 1; i >= 0; i--) {
            children[i].mParent = null;
            children[i] = null;
        }
    }

    public void bringChildToFront(View child) {
        final int index = indexOfChild(child);
        if (index >= 0) {
            removeFromArray(index);
            addInArray(child, mChildrenCount);
            child.mParent = this;
            requestLayout();
            invalidate();
        }
    }

    /** framework-internal (hidden in AOSP). Called by View.setLayoutParams. */
    protected void onSetLayoutParams(View child, LayoutParams layoutParams) { requestLayout(); }

    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    public LayoutParams generateLayoutParams(AttributeSet attrs) { return new LayoutParams(getContext(), attrs); }

    protected LayoutParams generateLayoutParams(LayoutParams p) { return p; }

    // ---------------------------------------------------------------- attach / detach and dispatch to children

    @Override
    void dispatchAttachedToWindow(AttachInfo info, int visibility) {
        super.dispatchAttachedToWindow(info, visibility);
        mGroupFlags &= ~FLAG_PREVENT_DISPATCH_ATTACHED_TO_WINDOW;
        final int count = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) {
            final View child = children[i];
            child.dispatchAttachedToWindow(info, combineVisibility(visibility, child.getVisibility()));
        }
    }

    @Override
    void dispatchDetachedFromWindow() {
        cancelAndClearTouchTargets(null);
        mLayoutCalledWhileSuppressed = false;
        final int count = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) children[i].dispatchDetachedFromWindow();
        super.dispatchDetachedFromWindow();
    }

    static int combineVisibility(int vis1, int vis2) { return Math.max(vis1, vis2); }

    @Override
    protected void onAttachedToWindow() { super.onAttachedToWindow(); }

    @Override
    protected void onDetachedFromWindow() { super.onDetachedFromWindow(); }

    @Override
    public void dispatchWindowFocusChanged(boolean hasFocus) {
        super.dispatchWindowFocusChanged(hasFocus);
        final int count = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) children[i].dispatchWindowFocusChanged(hasFocus);
    }

    @Override
    public void dispatchWindowVisibilityChanged(int visibility) {
        super.dispatchWindowVisibilityChanged(visibility);
        final int count = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) children[i].dispatchWindowVisibilityChanged(visibility);
    }

    @Override
    protected void dispatchVisibilityChanged(View changedView, int visibility) {
        super.dispatchVisibilityChanged(changedView, visibility);
        final int count = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) children[i].dispatchVisibilityChanged(changedView, visibility);
    }

    @Override
    boolean dispatchVisibilityAggregated(boolean isVisible) {
        isVisible = super.dispatchVisibilityAggregated(isVisible);
        final int count = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) {
            if (children[i].getVisibility() == VISIBLE) children[i].dispatchVisibilityAggregated(isVisible);
        }
        return isVisible;
    }

    /** framework-internal (hidden in AOSP, protected). */
    protected void onChildVisibilityChanged(View child, int oldVisibility, int newVisibility) {}

    @Override
    public void dispatchConfigurationChanged(Configuration newConfig) {
        super.dispatchConfigurationChanged(newConfig);
        final int count = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) children[i].dispatchConfigurationChanged(newConfig);
    }

    @Override
    public void dispatchDisplayHint(int hint) {
        super.dispatchDisplayHint(hint);
        final int count = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) children[i].dispatchDisplayHint(hint);
    }

    @Override
    public void dispatchSystemUiVisibilityChanged(int visible) {
        super.dispatchSystemUiVisibilityChanged(visible);
        final int count = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) children[i].dispatchSystemUiVisibilityChanged(visible);
    }

    @Override
    public void dispatchWindowSystemUiVisiblityChanged(int visible) {
        super.dispatchWindowSystemUiVisiblityChanged(visible);
        final int count = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) children[i].dispatchWindowSystemUiVisiblityChanged(visible);
    }

    @Override
    public void dispatchStartTemporaryDetach() {
        super.dispatchStartTemporaryDetach();
        final int count = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) children[i].dispatchStartTemporaryDetach();
    }

    @Override
    public void dispatchFinishTemporaryDetach() {
        super.dispatchFinishTemporaryDetach();
        final int count = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) children[i].dispatchFinishTemporaryDetach();
    }

    @Override
    public void dispatchPointerCaptureChanged(boolean hasCapture) {
        super.dispatchPointerCaptureChanged(hasCapture);
        final int count = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) children[i].dispatchPointerCaptureChanged(hasCapture);
    }

    @Override
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        insets = super.dispatchApplyWindowInsets(insets);
        if (!insets.isConsumed()) {
            final int count = getChildCount();
            for (int i = 0; i < count; i++) {
                insets = getChildAt(i).dispatchApplyWindowInsets(insets);
                if (insets.isConsumed()) break;
            }
        }
        return insets;
    }

    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        super.dispatchSaveInstanceState(container);
        final int count = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) {
            View c = children[i];
            if ((c.mViewFlags & PARENT_SAVE_DISABLED) != PARENT_SAVE_DISABLED) c.dispatchSaveInstanceState(container);
        }
    }

    protected void dispatchFreezeSelfOnly(SparseArray<Parcelable> container) { super.dispatchSaveInstanceState(container); }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        super.dispatchRestoreInstanceState(container);
        final int count = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) {
            View c = children[i];
            if ((c.mViewFlags & PARENT_SAVE_DISABLED) != PARENT_SAVE_DISABLED) c.dispatchRestoreInstanceState(container);
        }
    }

    protected void dispatchThawSelfOnly(SparseArray<Parcelable> container) { super.dispatchRestoreInstanceState(container); }

    // ---------------------------------------------------------------- drawable state

    @Override
    protected void dispatchSetPressed(boolean pressed) {
        final View[] children = mChildren;
        final int count = mChildrenCount;
        for (int i = 0; i < count; i++) {
            final View child = children[i];
            if (!pressed || (!child.isClickable() && !child.isLongClickable())) child.setPressed(pressed);
        }
    }

    @Override
    public void dispatchSetSelected(boolean selected) {
        final View[] children = mChildren;
        final int count = mChildrenCount;
        for (int i = 0; i < count; i++) children[i].setSelected(selected);
    }

    @Override
    public void dispatchSetActivated(boolean activated) {
        final View[] children = mChildren;
        final int count = mChildrenCount;
        for (int i = 0; i < count; i++) children[i].setActivated(activated);
    }

    @Override
    public void dispatchDrawableHotspotChanged(float x, float y) {
        final int count = mChildrenCount;
        if (count == 0) return;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) {
            final View child = children[i];
            final boolean nonActionable = !child.isClickable() && !child.isLongClickable();
            final boolean duplicatesState = (child.mViewFlags & DUPLICATE_PARENT_STATE) != 0;
            if (nonActionable || duplicatesState) {
                final float[] point = {x, y};
                transformPointToViewLocal(point, child);
                child.drawableHotspotChanged(point[0], point[1]);
            }
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if ((mGroupFlags & FLAG_NOTIFY_CHILDREN_ON_DRAWABLE_STATE_CHANGE) != 0) {
            if ((mGroupFlags & FLAG_ADD_STATES_FROM_CHILDREN) != 0) {
                throw new IllegalStateException("addStateFromChildren cannot be enabled if a"
                        + " child has duplicateParentState set to true");
            }
            final View[] children = mChildren;
            final int count = mChildrenCount;
            for (int i = 0; i < count; i++) {
                final View child = children[i];
                if ((child.mViewFlags & DUPLICATE_PARENT_STATE) != 0) child.refreshDrawableState();
            }
        }
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        if ((mGroupFlags & FLAG_ADD_STATES_FROM_CHILDREN) == 0) return super.onCreateDrawableState(extraSpace);
        int need = 0;
        int n = getChildCount();
        for (int i = 0; i < n; i++) {
            int[] childState = getChildAt(i).getDrawableState();
            if (childState != null) need += childState.length;
        }
        int[] state = super.onCreateDrawableState(extraSpace + need);
        for (int i = 0; i < n; i++) {
            int[] childState = getChildAt(i).getDrawableState();
            if (childState != null) state = mergeDrawableStates(state, childState);
        }
        return state;
    }

    public void childDrawableStateChanged(View child) {
        if ((mGroupFlags & FLAG_ADD_STATES_FROM_CHILDREN) != 0) refreshDrawableState();
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        final View[] children = mChildren;
        final int count = mChildrenCount;
        for (int i = 0; i < count; i++) children[i].jumpDrawablesToCurrentState();
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final int childrenCount = mChildrenCount;
        final View[] children = mChildren;
        final int flags = mGroupFlags;
        int clipSaveCount = 0;
        final boolean clipToPadding = (flags & CLIP_TO_PADDING_MASK) == CLIP_TO_PADDING_MASK;
        if (clipToPadding) {
            clipSaveCount = canvas.save();
            canvas.clipRect(mScrollX + mPaddingLeft, mScrollY + mPaddingTop,
                    mScrollX + mRight - mLeft - mPaddingRight, mScrollY + mBottom - mTop - mPaddingBottom);
        }
        final long drawingTime = getDrawingTime();
        final ArrayList<View> preorderedList = buildOrderedChildList();
        final boolean customOrder = preorderedList == null && isChildrenDrawingOrderEnabled();
        for (int i = 0; i < childrenCount; i++) {
            final int childIndex = getAndVerifyPreorderedIndex(childrenCount, i, customOrder);
            final View child = getAndVerifyPreorderedView(preorderedList, children, childIndex);
            if ((child.mViewFlags & VISIBILITY_MASK) == VISIBLE) drawChild(canvas, child, drawingTime);
        }
        if (preorderedList != null) preorderedList.clear();
        if (clipToPadding) canvas.restoreToCount(clipSaveCount);
    }

    protected boolean drawChild(Canvas canvas, View child, long drawingTime) { return child.draw(canvas, this, drawingTime); }

    private int getAndVerifyPreorderedIndex(int childrenCount, int i, boolean customOrder) {
        final int childIndex;
        if (customOrder) {
            final int childIndex1 = getChildDrawingOrder(childrenCount, i);
            if (childIndex1 >= childrenCount) {
                throw new IndexOutOfBoundsException("getChildDrawingOrder() returned invalid index " + childIndex1
                        + " (child count is " + childrenCount + ")");
            }
            childIndex = childIndex1;
        } else {
            childIndex = i;
        }
        return childIndex;
    }

    private static View getAndVerifyPreorderedView(ArrayList<View> preorderedList, View[] children, int childIndex) {
        if (preorderedList != null) {
            View child = preorderedList.get(childIndex);
            if (child == null) throw new RuntimeException("Invalid preorderedList contained null child at index " + childIndex);
            return child;
        }
        return children[childIndex];
    }

    /** Children ordered for drawing: by Z, then custom drawing order (AOSP buildOrderedChildList). */
    ArrayList<View> buildOrderedChildList() {
        final int childrenCount = mChildrenCount;
        if (childrenCount <= 1 || !hasChildWithZ()) return null;
        if (mPreSortedChildren == null) mPreSortedChildren = new ArrayList<View>(childrenCount);
        else mPreSortedChildren.clear();
        final boolean customOrder = isChildrenDrawingOrderEnabled();
        for (int i = 0; i < childrenCount; i++) {
            final int childIndex = getAndVerifyPreorderedIndex(childrenCount, i, customOrder);
            final View nextChild = mChildren[childIndex];
            final float currentZ = nextChild.getZ();
            int insertIndex = i;
            while (insertIndex > 0 && mPreSortedChildren.get(insertIndex - 1).getZ() > currentZ) insertIndex--;
            mPreSortedChildren.add(insertIndex, nextChild);
        }
        return mPreSortedChildren;
    }

    private boolean hasChildWithZ() {
        for (int i = 0; i < mChildrenCount; i++) {
            if (mChildren[i].getZ() != 0) return true;
        }
        return false;
    }

    public ArrayList<View> buildTouchDispatchChildList() { return buildOrderedChildList(); }

    @Override
    public ViewGroupOverlay getOverlay() {
        if (mOverlay == null) mOverlay = new ViewGroupOverlay(mContext, this);
        return (ViewGroupOverlay) mOverlay;
    }

    // ---------------------------------------------------------------- invalidation

    public final void invalidateChild(View child, final Rect dirty) {
        if (dirty == null) {
            child.invalidate(true);
            return;
        }
        ViewParent parent = this;
        final int[] location = new int[] {child.mLeft, child.mTop};
        if (!child.hasIdentityMatrix()) transformRect(child.getMatrix(), dirty);
        do {
            View view = null;
            if (parent instanceof View) view = (View) parent;
            parent = parent.invalidateChildInParent(location, dirty);
            if (dirty.isEmpty()) return;
            if (view != null && parent != null && !view.hasIdentityMatrix()) transformRect(view.getMatrix(), dirty);
        } while (parent != null);
    }

    private static void transformRect(android.graphics.Matrix m, Rect r) {
        RectF boundingRect = new RectF(r);
        m.mapRect(boundingRect);
        r.set((int) Math.floor(boundingRect.left), (int) Math.floor(boundingRect.top),
                (int) Math.ceil(boundingRect.right), (int) Math.ceil(boundingRect.bottom));
    }

    public ViewParent invalidateChildInParent(final int[] location, final Rect dirty) {
        mPrivateFlags |= PFLAG_DIRTY;
        dirty.offset(location[0] - mScrollX, location[1] - mScrollY);
        if ((mGroupFlags & FLAG_CLIP_CHILDREN) != 0) {
            if (!dirty.intersect(0, 0, mRight - mLeft, mBottom - mTop)) dirty.setEmpty();
        }
        location[0] = mLeft;
        location[1] = mTop;
        if ((mViewFlags & VISIBILITY_MASK) != VISIBLE) {
            dirty.setEmpty();
            return null;
        }
        return mParent;
    }

    @Override
    public void onDescendantInvalidated(View child, View target) {
        if (mParent != null) mParent.onDescendantInvalidated(this, target);
    }

    public void requestTransparentRegion(View child) {}

    @Override
    public boolean gatherTransparentRegion(Region region) { return true; }

    public void recomputeViewAttributes(View child) {
        if (mParent != null) mParent.recomputeViewAttributes(this);
    }

    // ---------------------------------------------------------------- layout and measure

    @Override
    public final void layout(int l, int t, int r, int b) {
        if (!mSuppressLayout) super.layout(l, t, r, b);
        else mLayoutCalledWhileSuppressed = true;
    }

    @Override
    protected abstract void onLayout(boolean changed, int l, int t, int r, int b);

    protected void measureChildren(int widthMeasureSpec, int heightMeasureSpec) {
        final int size = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < size; ++i) {
            final View child = children[i];
            if ((child.mViewFlags & VISIBILITY_MASK) != GONE) measureChild(child, widthMeasureSpec, heightMeasureSpec);
        }
    }

    protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
        final LayoutParams lp = child.getLayoutParams();
        final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec, mPaddingLeft + mPaddingRight,
                lp.width);
        final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec, mPaddingTop + mPaddingBottom,
                lp.height);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
            int parentHeightMeasureSpec, int heightUsed) {
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
                mPaddingLeft + mPaddingRight + lp.leftMargin + lp.rightMargin + widthUsed, lp.width);
        final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
                mPaddingTop + mPaddingBottom + lp.topMargin + lp.bottomMargin + heightUsed, lp.height);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    public static int getChildMeasureSpec(int spec, int padding, int childDimension) {
        int specMode = MeasureSpec.getMode(spec);
        int specSize = MeasureSpec.getSize(spec);
        int size = Math.max(0, specSize - padding);
        int resultSize = 0;
        int resultMode = 0;
        switch (specMode) {
            case MeasureSpec.EXACTLY:
                if (childDimension >= 0) {
                    resultSize = childDimension;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childDimension == LayoutParams.MATCH_PARENT) {
                    resultSize = size;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childDimension == LayoutParams.WRAP_CONTENT) {
                    resultSize = size;
                    resultMode = MeasureSpec.AT_MOST;
                }
                break;
            case MeasureSpec.AT_MOST:
                if (childDimension >= 0) {
                    resultSize = childDimension;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childDimension == LayoutParams.MATCH_PARENT) {
                    resultSize = size;
                    resultMode = MeasureSpec.AT_MOST;
                } else if (childDimension == LayoutParams.WRAP_CONTENT) {
                    resultSize = size;
                    resultMode = MeasureSpec.AT_MOST;
                }
                break;
            case MeasureSpec.UNSPECIFIED:
                if (childDimension >= 0) {
                    resultSize = childDimension;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childDimension == LayoutParams.MATCH_PARENT) {
                    resultSize = size;
                    resultMode = MeasureSpec.UNSPECIFIED;
                } else if (childDimension == LayoutParams.WRAP_CONTENT) {
                    resultSize = size;
                    resultMode = MeasureSpec.UNSPECIFIED;
                }
                break;
        }
        return MeasureSpec.makeMeasureSpec(resultSize, resultMode);
    }

    // ---------------------------------------------------------------- touch

    private static final class TouchTarget {
        static final int ALL_POINTER_IDS = -1;
        View child;
        int pointerIdBits;
        TouchTarget next;

        static TouchTarget obtain(View child, int pointerIdBits) {
            TouchTarget t = new TouchTarget();
            t.child = child;
            t.pointerIdBits = pointerIdBits;
            return t;
        }

        void recycle() { child = null; }
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.isFromSource(InputDevice.SOURCE_MOUSE) && ev.getAction() == MotionEvent.ACTION_DOWN
                && ev.isButtonPressed(MotionEvent.BUTTON_PRIMARY) && isOnScrollbarThumb(ev.getX(), ev.getY())) {
            return true;
        }
        return false;
    }

    private boolean isOnScrollbarThumb(float x, float y) { return false; }

    public boolean onInterceptHoverEvent(MotionEvent event) { return false; }

    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (disallowIntercept == ((mGroupFlags & FLAG_DISALLOW_INTERCEPT) != 0)) return;
        if (disallowIntercept) mGroupFlags |= FLAG_DISALLOW_INTERCEPT;
        else mGroupFlags &= ~FLAG_DISALLOW_INTERCEPT;
        if (mParent != null) mParent.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean handled = false;
        if (onFilterTouchEventForSecurity(ev)) {
            final int action = ev.getAction();
            final int actionMasked = action & MotionEvent.ACTION_MASK;
            if (actionMasked == MotionEvent.ACTION_DOWN) {
                cancelAndClearTouchTargets(ev);
                resetTouchState();
            }
            final boolean intercepted;
            if (actionMasked == MotionEvent.ACTION_DOWN || mFirstTouchTarget != null) {
                final boolean disallowIntercept = (mGroupFlags & FLAG_DISALLOW_INTERCEPT) != 0;
                if (!disallowIntercept) {
                    intercepted = onInterceptTouchEvent(ev);
                    ev.setAction(action);
                } else {
                    intercepted = false;
                }
            } else {
                intercepted = true;
            }
            final boolean canceled = resetCancelNextUpFlag(this) || actionMasked == MotionEvent.ACTION_CANCEL;
            final boolean split = (mGroupFlags & FLAG_SPLIT_MOTION_EVENTS) != 0;
            TouchTarget newTouchTarget = null;
            boolean alreadyDispatchedToNewTouchTarget = false;
            if (!canceled && !intercepted) {
                if (actionMasked == MotionEvent.ACTION_DOWN
                        || (split && actionMasked == MotionEvent.ACTION_POINTER_DOWN)
                        || actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
                    final int actionIndex = ev.getActionIndex();
                    final int idBitsToAssign = split ? 1 << ev.getPointerId(actionIndex) : TouchTarget.ALL_POINTER_IDS;
                    removePointersFromTouchTargets(idBitsToAssign);
                    final int childrenCount = mChildrenCount;
                    if (childrenCount != 0) {
                        final float x = ev.getX(actionIndex);
                        final float y = ev.getY(actionIndex);
                        final ArrayList<View> preorderedList = buildTouchDispatchChildList();
                        final boolean customOrder = preorderedList == null && isChildrenDrawingOrderEnabled();
                        final View[] children = mChildren;
                        for (int i = childrenCount - 1; i >= 0; i--) {
                            final int childIndex = getAndVerifyPreorderedIndex(childrenCount, i, customOrder);
                            final View child = getAndVerifyPreorderedView(preorderedList, children, childIndex);
                            if (!canViewReceivePointerEvents(child) || !isTransformedTouchPointInView(x, y, child, null)) {
                                continue;
                            }
                            newTouchTarget = getTouchTarget(child);
                            if (newTouchTarget != null) {
                                newTouchTarget.pointerIdBits |= idBitsToAssign;
                                break;
                            }
                            resetCancelNextUpFlag(child);
                            if (dispatchTransformedTouchEvent(ev, false, child, idBitsToAssign)) {
                                newTouchTarget = addTouchTarget(child, idBitsToAssign);
                                alreadyDispatchedToNewTouchTarget = true;
                                break;
                            }
                        }
                        if (preorderedList != null) preorderedList.clear();
                    }
                    if (newTouchTarget == null && mFirstTouchTarget != null) {
                        newTouchTarget = mFirstTouchTarget;
                        while (newTouchTarget.next != null) newTouchTarget = newTouchTarget.next;
                        newTouchTarget.pointerIdBits |= idBitsToAssign;
                    }
                }
            }
            if (mFirstTouchTarget == null) {
                handled = dispatchTransformedTouchEvent(ev, canceled, null, TouchTarget.ALL_POINTER_IDS);
            } else {
                TouchTarget predecessor = null;
                TouchTarget target = mFirstTouchTarget;
                while (target != null) {
                    final TouchTarget next = target.next;
                    if (alreadyDispatchedToNewTouchTarget && target == newTouchTarget) {
                        handled = true;
                    } else {
                        final boolean cancelChild = resetCancelNextUpFlag(target.child) || intercepted;
                        if (dispatchTransformedTouchEvent(ev, cancelChild, target.child, target.pointerIdBits)) {
                            handled = true;
                        }
                        if (cancelChild) {
                            if (predecessor == null) mFirstTouchTarget = next;
                            else predecessor.next = next;
                            target.recycle();
                            target = next;
                            continue;
                        }
                    }
                    predecessor = target;
                    target = next;
                }
            }
            if (canceled || actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
                resetTouchState();
            } else if (split && actionMasked == MotionEvent.ACTION_POINTER_UP) {
                final int actionIndex = ev.getActionIndex();
                final int idBitsToRemove = 1 << ev.getPointerId(actionIndex);
                removePointersFromTouchTargets(idBitsToRemove);
            }
        }
        return handled;
    }

    private static boolean canViewReceivePointerEvents(View child) {
        return (child.mViewFlags & VISIBILITY_MASK) == VISIBLE;
    }

    private void resetTouchState() {
        clearTouchTargets();
        resetCancelNextUpFlag(this);
        mGroupFlags &= ~FLAG_DISALLOW_INTERCEPT;
        mNestedScrollAxes = SCROLL_AXIS_NONE;
    }

    private static boolean resetCancelNextUpFlag(View view) {
        if ((view.mPrivateFlags & PFLAG_CANCEL_NEXT_UP_EVENT) != 0) {
            view.mPrivateFlags &= ~PFLAG_CANCEL_NEXT_UP_EVENT;
            return true;
        }
        return false;
    }

    private void clearTouchTargets() {
        TouchTarget target = mFirstTouchTarget;
        if (target != null) {
            do {
                TouchTarget next = target.next;
                target.recycle();
                target = next;
            } while (target != null);
            mFirstTouchTarget = null;
        }
    }

    private void cancelAndClearTouchTargets(MotionEvent event) {
        if (mFirstTouchTarget != null) {
            boolean syntheticEvent = false;
            if (event == null) {
                final long now = android.os.SystemClock.uptimeMillis();
                event = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);
                event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                syntheticEvent = true;
            }
            for (TouchTarget target = mFirstTouchTarget; target != null; target = target.next) {
                resetCancelNextUpFlag(target.child);
                dispatchTransformedTouchEvent(event, true, target.child, target.pointerIdBits);
            }
            clearTouchTargets();
            if (syntheticEvent) event.recycle();
        }
    }

    private TouchTarget getTouchTarget(View child) {
        for (TouchTarget target = mFirstTouchTarget; target != null; target = target.next) {
            if (target.child == child) return target;
        }
        return null;
    }

    private TouchTarget addTouchTarget(View child, int pointerIdBits) {
        final TouchTarget target = TouchTarget.obtain(child, pointerIdBits);
        target.next = mFirstTouchTarget;
        mFirstTouchTarget = target;
        return target;
    }

    private void removePointersFromTouchTargets(int pointerIdBits) {
        TouchTarget predecessor = null;
        TouchTarget target = mFirstTouchTarget;
        while (target != null) {
            final TouchTarget next = target.next;
            if ((target.pointerIdBits & pointerIdBits) != 0) {
                target.pointerIdBits &= ~pointerIdBits;
                if (target.pointerIdBits == 0) {
                    if (predecessor == null) mFirstTouchTarget = next;
                    else predecessor.next = next;
                    target.recycle();
                    target = next;
                    continue;
                }
            }
            predecessor = target;
            target = next;
        }
    }

    private void cancelTouchTarget(View view) {
        TouchTarget predecessor = null;
        TouchTarget target = mFirstTouchTarget;
        while (target != null) {
            final TouchTarget next = target.next;
            if (target.child == view) {
                if (predecessor == null) mFirstTouchTarget = next;
                else predecessor.next = next;
                target.recycle();
                final long now = android.os.SystemClock.uptimeMillis();
                MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);
                event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                view.dispatchTouchEvent(event);
                event.recycle();
                return;
            }
            predecessor = target;
            target = next;
        }
    }

    /** framework-internal (hidden in AOSP, protected). */
    protected boolean isTransformedTouchPointInView(float x, float y, View child, PointF outLocalPoint) {
        final float[] point = new float[] {x, y};
        transformPointToViewLocal(point, child);
        final boolean isInView = child.pointInView(point[0], point[1]);
        if (isInView && outLocalPoint != null) outLocalPoint.set(point[0], point[1]);
        return isInView;
    }

    /** framework-internal (hidden in AOSP). */
    public void transformPointToViewLocal(float[] point, View child) {
        point[0] += mScrollX - child.mLeft;
        point[1] += mScrollY - child.mTop;
        if (!child.hasIdentityMatrix()) child.getInverseMatrix().mapPoints(point);
    }

    private boolean dispatchTransformedTouchEvent(MotionEvent event, boolean cancel, View child,
            int desiredPointerIdBits) {
        final boolean handled;
        final int oldAction = event.getAction();
        if (cancel || oldAction == MotionEvent.ACTION_CANCEL) {
            event.setAction(MotionEvent.ACTION_CANCEL);
            if (child == null) handled = super.dispatchTouchEvent(event);
            else handled = child.dispatchTouchEvent(event);
            event.setAction(oldAction);
            return handled;
        }
        final int oldPointerIdBits = event.getPointerIdBits();
        final int newPointerIdBits = oldPointerIdBits & desiredPointerIdBits;
        if (newPointerIdBits == 0) return false;
        final MotionEvent transformedEvent;
        if (newPointerIdBits == oldPointerIdBits) {
            if (child == null || child.hasIdentityMatrix()) {
                if (child == null) {
                    handled = super.dispatchTouchEvent(event);
                } else {
                    final float offsetX = mScrollX - child.mLeft;
                    final float offsetY = mScrollY - child.mTop;
                    event.offsetLocation(offsetX, offsetY);
                    handled = child.dispatchTouchEvent(event);
                    event.offsetLocation(-offsetX, -offsetY);
                }
                return handled;
            }
            transformedEvent = MotionEvent.obtain(event);
        } else {
            transformedEvent = event.split(newPointerIdBits);
        }
        if (child == null) {
            handled = super.dispatchTouchEvent(transformedEvent);
        } else {
            final float offsetX = mScrollX - child.mLeft;
            final float offsetY = mScrollY - child.mTop;
            transformedEvent.offsetLocation(offsetX, offsetY);
            if (!child.hasIdentityMatrix()) transformedEvent.transform(child.getInverseMatrix());
            handled = child.dispatchTouchEvent(transformedEvent);
        }
        transformedEvent.recycle();
        return handled;
    }

    @Override
    protected boolean dispatchHoverEvent(MotionEvent event) { return super.dispatchHoverEvent(event); }

    @Override
    protected boolean dispatchGenericPointerEvent(MotionEvent event) {
        final int childrenCount = mChildrenCount;
        if (childrenCount != 0) {
            final float x = event.getX();
            final float y = event.getY();
            final ArrayList<View> preorderedList = buildOrderedChildList();
            final boolean customOrder = preorderedList == null && isChildrenDrawingOrderEnabled();
            final View[] children = mChildren;
            for (int i = childrenCount - 1; i >= 0; i--) {
                final int childIndex = getAndVerifyPreorderedIndex(childrenCount, i, customOrder);
                final View child = getAndVerifyPreorderedView(preorderedList, children, childIndex);
                if (!canViewReceivePointerEvents(child) || !isTransformedTouchPointInView(x, y, child, null)) continue;
                final float offsetX = mScrollX - child.mLeft;
                final float offsetY = mScrollY - child.mTop;
                MotionEvent transformed = MotionEvent.obtain(event);
                transformed.offsetLocation(offsetX, offsetY);
                if (!child.hasIdentityMatrix()) transformed.transform(child.getInverseMatrix());
                if (child.dispatchGenericMotionEvent(transformed)) {
                    if (preorderedList != null) preorderedList.clear();
                    return true;
                }
            }
            if (preorderedList != null) preorderedList.clear();
        }
        return super.dispatchGenericPointerEvent(event);
    }

    @Override
    protected boolean dispatchGenericFocusedEvent(MotionEvent event) {
        if ((mPrivateFlags & (PFLAG_FOCUSED | PFLAG_HAS_BOUNDS)) == (PFLAG_FOCUSED | PFLAG_HAS_BOUNDS)) {
            return super.dispatchGenericFocusedEvent(event);
        } else if (mFocused != null && (mFocused.mPrivateFlags & PFLAG_HAS_BOUNDS) == PFLAG_HAS_BOUNDS) {
            return mFocused.dispatchGenericMotionEvent(event);
        }
        return false;
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent event) {
        if ((mPrivateFlags & (PFLAG_FOCUSED | PFLAG_HAS_BOUNDS)) == (PFLAG_FOCUSED | PFLAG_HAS_BOUNDS)) {
            return super.dispatchTrackballEvent(event);
        } else if (mFocused != null && (mFocused.mPrivateFlags & PFLAG_HAS_BOUNDS) == PFLAG_HAS_BOUNDS) {
            return mFocused.dispatchTrackballEvent(event);
        }
        return false;
    }

    @Override
    public boolean dispatchCapturedPointerEvent(MotionEvent event) {
        if ((mPrivateFlags & (PFLAG_FOCUSED | PFLAG_HAS_BOUNDS)) == (PFLAG_FOCUSED | PFLAG_HAS_BOUNDS)) {
            return super.dispatchCapturedPointerEvent(event);
        } else if (mFocused != null && (mFocused.mPrivateFlags & PFLAG_HAS_BOUNDS) == PFLAG_HAS_BOUNDS) {
            return mFocused.dispatchCapturedPointerEvent(event);
        }
        return false;
    }

    @Override
    public boolean dispatchDragEvent(DragEvent event) { return super.dispatchDragEvent(event); }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) { return null; }

    // ---------------------------------------------------------------- keys

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if ((mPrivateFlags & (PFLAG_FOCUSED | PFLAG_HAS_BOUNDS)) == (PFLAG_FOCUSED | PFLAG_HAS_BOUNDS)) {
            return super.dispatchKeyEventPreIme(event);
        } else if (mFocused != null && (mFocused.mPrivateFlags & PFLAG_HAS_BOUNDS) == PFLAG_HAS_BOUNDS) {
            return mFocused.dispatchKeyEventPreIme(event);
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if ((mPrivateFlags & (PFLAG_FOCUSED | PFLAG_HAS_BOUNDS)) == (PFLAG_FOCUSED | PFLAG_HAS_BOUNDS)) {
            if (super.dispatchKeyEvent(event)) return true;
        } else if (mFocused != null && (mFocused.mPrivateFlags & PFLAG_HAS_BOUNDS) == PFLAG_HAS_BOUNDS) {
            if (mFocused.dispatchKeyEvent(event)) return true;
        }
        return false;
    }

    @Override
    public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        if ((mPrivateFlags & (PFLAG_FOCUSED | PFLAG_HAS_BOUNDS)) == (PFLAG_FOCUSED | PFLAG_HAS_BOUNDS)) {
            return super.dispatchKeyShortcutEvent(event);
        } else if (mFocused != null && (mFocused.mPrivateFlags & PFLAG_HAS_BOUNDS) == PFLAG_HAS_BOUNDS) {
            return mFocused.dispatchKeyShortcutEvent(event);
        }
        return false;
    }

    @Override
    boolean dispatchUnhandledKeyEvent(KeyEvent evt) {
        View focused = mFocused;
        if (focused != null && focused.dispatchUnhandledKeyEvent(evt)) return true;
        return super.dispatchUnhandledKeyEvent(evt);
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        return mFocused != null && mFocused.dispatchUnhandledMove(focused, direction);
    }

    // ---------------------------------------------------------------- focus

    @Override
    public boolean hasFocus() { return (mPrivateFlags & PFLAG_FOCUSED) != 0 || mFocused != null; }

    @Override
    public View findFocus() {
        if (isFocused()) return this;
        if (mFocused != null) return mFocused.findFocus();
        return null;
    }

    @Override
    boolean hasFocusable(boolean allowAutoFocus, boolean dispatchExplicit) {
        if ((mViewFlags & VISIBILITY_MASK) != VISIBLE) return false;
        if ((allowAutoFocus || getFocusable() != FOCUSABLE_AUTO) && isFocusable()) return true;
        final int descendantFocusability = getDescendantFocusability();
        if (descendantFocusability != FOCUS_BLOCK_DESCENDANTS) {
            final int count = mChildrenCount;
            final View[] children = mChildren;
            for (int i = 0; i < count; i++) {
                final View child = children[i];
                if (child.hasFocusable(allowAutoFocus, dispatchExplicit)) return true;
            }
        }
        return false;
    }

    public void requestChildFocus(View child, View focused) {
        if (getDescendantFocusability() == FOCUS_BLOCK_DESCENDANTS) return;
        super.unFocus(focused);
        if (mFocused != child) {
            if (mFocused != null) mFocused.unFocus(focused);
            mFocused = child;
        }
        if (mParent != null) mParent.requestChildFocus(this, focused);
    }

    void setDefaultFocus(View child) {
        if (mDefaultFocus != null && mDefaultFocus.isFocusedByDefault()) return;
        mDefaultFocus = child;
        if (mParent instanceof ViewGroup) ((ViewGroup) mParent).setDefaultFocus(this);
    }

    void clearDefaultFocus(View child) {
        if (mDefaultFocus != child && mDefaultFocus != null && mDefaultFocus.isFocusedByDefault()) return;
        mDefaultFocus = null;
        for (int i = 0; i < mChildrenCount; i++) {
            View sibling = mChildren[i];
            if (sibling.isFocusedByDefault()) {
                mDefaultFocus = sibling;
                return;
            } else if (mDefaultFocus == null && sibling.hasDefaultFocus()) {
                mDefaultFocus = sibling;
            }
        }
        if (mParent instanceof ViewGroup) ((ViewGroup) mParent).clearDefaultFocus(this);
    }

    @Override
    boolean hasDefaultFocus() { return mDefaultFocus != null || super.hasDefaultFocus(); }

    void clearFocusedInCluster() { mFocusedInCluster = null; }

    @Override
    public void focusableViewAvailable(View v) {
        if (mParent != null && (getDescendantFocusability() != FOCUS_BLOCK_DESCENDANTS)
                && ((mViewFlags & VISIBILITY_MASK) == VISIBLE)
                && (isFocusableInTouchMode() || !shouldBlockFocusForTouchscreen())
                && !(isFocused() && getDescendantFocusability() != FOCUS_AFTER_DESCENDANTS)) {
            mParent.focusableViewAvailable(v);
        }
    }

    @Override
    public boolean showContextMenuForChild(View originalView) {
        return mParent != null && mParent.showContextMenuForChild(originalView);
    }

    @Override
    public boolean showContextMenuForChild(View originalView, float x, float y) {
        return mParent != null && mParent.showContextMenuForChild(originalView, x, y);
    }

    @Override
    public void createContextMenu(ContextMenu menu) { super.createContextMenu(menu); }

    @Override
    public ActionMode startActionModeForChild(View originalView, ActionMode.Callback callback) {
        return startActionModeForChild(originalView, callback, ActionMode.TYPE_PRIMARY);
    }

    @Override
    public ActionMode startActionModeForChild(View originalView, ActionMode.Callback callback, int type) {
        if (mParent != null) {
            try {
                return mParent.startActionModeForChild(originalView, callback, type);
            } catch (AbstractMethodError ame) {
                return mParent.startActionModeForChild(originalView, callback);
            }
        }
        return null;
    }

    @Override
    public View focusSearch(View focused, int direction) {
        if (isRootNamespace() || !(mParent instanceof View)) {
            return FocusFinder.getInstance().findNextFocus(this, focused, direction);
        } else if (mParent != null) {
            return mParent.focusSearch(focused, direction);
        }
        return null;
    }

    /** framework-internal (hidden in AOSP). */
    public boolean isRootNamespace() { return (mPrivateFlags & PFLAG_IS_ROOT_NAMESPACE) != 0; }

    /** framework-internal (hidden in AOSP). */
    public void setIsRootNamespace(boolean isRoot) {
        if (isRoot) mPrivateFlags |= PFLAG_IS_ROOT_NAMESPACE;
        else mPrivateFlags &= ~PFLAG_IS_ROOT_NAMESPACE;
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) { return false; }

    @Override
    public boolean requestSendAccessibilityEvent(View child, AccessibilityEvent event) {
        ViewParent parent = mParent;
        if (parent == null) return false;
        final boolean propagate = onRequestSendAccessibilityEvent(child, event);
        if (!propagate) return false;
        return parent.requestSendAccessibilityEvent(this, event);
    }

    public boolean onRequestSendAccessibilityEvent(View child, AccessibilityEvent event) {
        AccessibilityDelegate delegate = getAccessibilityDelegate();
        if (delegate != null) return delegate.onRequestSendAccessibilityEvent(this, child, event);
        return onRequestSendAccessibilityEventInternal(child, event);
    }

    boolean onRequestSendAccessibilityEventInternal(View child, AccessibilityEvent event) { return true; }

    @Override
    public void childHasTransientStateChanged(View child, boolean childHasTransientState) {
        final boolean oldHasTransientState = hasTransientState();
        if (childHasTransientState) mChildCountWithTransientState++;
        else mChildCountWithTransientState--;
        final boolean newHasTransientState = hasTransientState();
        if (mParent != null && oldHasTransientState != newHasTransientState) {
            mParent.childHasTransientStateChanged(this, newHasTransientState);
        }
    }

    @Override
    public boolean hasTransientState() { return mChildCountWithTransientState > 0 || super.hasTransientState(); }

    @Override
    public void clearChildFocus(View child) {
        mFocused = null;
        if (mParent != null) mParent.clearChildFocus(this);
    }

    @Override
    public void clearFocus() {
        if (mFocused == null) {
            super.clearFocus();
        } else {
            View focused = mFocused;
            mFocused = null;
            focused.clearFocus();
        }
    }

    @Override
    void unFocus(View focused) {
        if (mFocused == null) {
            super.unFocus(focused);
        } else {
            mFocused.unFocus(focused);
            mFocused = null;
        }
    }

    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        int descendantFocusability = getDescendantFocusability();
        boolean result;
        switch (descendantFocusability) {
            case FOCUS_BLOCK_DESCENDANTS:
                result = super.requestFocus(direction, previouslyFocusedRect);
                break;
            case FOCUS_BEFORE_DESCENDANTS: {
                final boolean took = super.requestFocus(direction, previouslyFocusedRect);
                result = took ? took : onRequestFocusInDescendants(direction, previouslyFocusedRect);
                break;
            }
            case FOCUS_AFTER_DESCENDANTS: {
                final boolean took = onRequestFocusInDescendants(direction, previouslyFocusedRect);
                result = took ? took : super.requestFocus(direction, previouslyFocusedRect);
                break;
            }
            default:
                throw new IllegalStateException("descendant focusability must be one of FOCUS_BEFORE_DESCENDANTS,"
                        + " FOCUS_AFTER_DESCENDANTS, FOCUS_BLOCK_DESCENDANTS but is " + descendantFocusability);
        }
        return result;
    }

    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        int index;
        int increment;
        int end;
        int count = mChildrenCount;
        if ((direction & FOCUS_FORWARD) != 0) {
            index = 0;
            increment = 1;
            end = count;
        } else {
            index = count - 1;
            increment = -1;
            end = -1;
        }
        final View[] children = mChildren;
        for (int i = index; i != end; i += increment) {
            View child = children[i];
            if ((child.mViewFlags & VISIBILITY_MASK) == VISIBLE) {
                if (child.requestFocus(direction, previouslyFocusedRect)) return true;
            }
        }
        return false;
    }

    @Override
    public boolean restoreDefaultFocus() {
        if (mDefaultFocus != null && getDescendantFocusability() != FOCUS_BLOCK_DESCENDANTS
                && (mDefaultFocus.mViewFlags & VISIBILITY_MASK) == VISIBLE && mDefaultFocus.restoreDefaultFocus()) {
            return true;
        }
        return super.restoreDefaultFocus();
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        final int focusableCount = views.size();
        final int descendantFocusability = getDescendantFocusability();
        final boolean blockFocusForTouchscreen = shouldBlockFocusForTouchscreen();
        final boolean focusSelf = (isFocusableInTouchMode() || !blockFocusForTouchscreen);
        if (descendantFocusability == FOCUS_BLOCK_DESCENDANTS) {
            if (focusSelf) super.addFocusables(views, direction, focusableMode);
            return;
        }
        if (blockFocusForTouchscreen) focusableMode |= FOCUSABLES_TOUCH_MODE;
        if ((descendantFocusability == FOCUS_BEFORE_DESCENDANTS) && focusSelf) {
            super.addFocusables(views, direction, focusableMode);
        }
        final View[] children = mChildren;
        for (int i = 0; i < mChildrenCount; ++i) {
            View child = children[i];
            if ((child.mViewFlags & VISIBILITY_MASK) == VISIBLE) child.addFocusables(views, direction, focusableMode);
        }
        if ((descendantFocusability == FOCUS_AFTER_DESCENDANTS) && focusSelf && focusableCount == views.size()) {
            super.addFocusables(views, direction, focusableMode);
        }
    }

    @Override
    public void addKeyboardNavigationClusters(Collection<View> views, int direction) {
        final int focusableCount = views.size();
        if (isKeyboardNavigationCluster()) {
            super.addKeyboardNavigationClusters(views, direction);
            return;
        }
        if (getDescendantFocusability() == FOCUS_BLOCK_DESCENDANTS) return;
        for (int i = 0; i < mChildrenCount; ++i) {
            View child = mChildren[i];
            if ((child.mViewFlags & VISIBILITY_MASK) == VISIBLE) child.addKeyboardNavigationClusters(views, direction);
        }
    }

    @Override
    public void addTouchables(ArrayList<View> views) {
        super.addTouchables(views);
        final int count = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < count; i++) {
            final View child = children[i];
            if ((child.mViewFlags & VISIBILITY_MASK) == VISIBLE) child.addTouchables(views);
        }
    }

    @Override
    public void findViewsWithText(ArrayList<View> outViews, CharSequence text, int flags) {
        super.findViewsWithText(outViews, text, flags);
        final int childrenCount = mChildrenCount;
        final View[] children = mChildren;
        for (int i = 0; i < childrenCount; i++) {
            View child = children[i];
            if ((child.mViewFlags & VISIBILITY_MASK) == VISIBLE) child.findViewsWithText(outViews, text, flags);
        }
    }

    @Override
    View findViewTraversal(int id) {
        if (id == mID) return this;
        final View[] where = mChildren;
        final int len = mChildrenCount;
        for (int i = 0; i < len; i++) {
            View v = where[i];
            v = v.findViewById(id);
            if (v != null) return v;
        }
        return null;
    }

    @Override
    View findViewWithTagTraversal(Object tag) {
        if (tag != null && tag.equals(mTag)) return this;
        final View[] where = mChildren;
        final int len = mChildrenCount;
        for (int i = 0; i < len; i++) {
            View v = where[i];
            v = v.findViewWithTag(tag);
            if (v != null) return v;
        }
        return null;
    }

    @Override
    View findViewByPredicateTraversal(java.util.function.Predicate<View> predicate, View childToSkip) {
        if (predicate.test(this)) return this;
        final View[] where = mChildren;
        final int len = mChildrenCount;
        for (int i = 0; i < len; i++) {
            View v = where[i];
            if (v != childToSkip) {
                v = v.findViewByPredicateTraversal(predicate, null);
                if (v != null) return v;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- coordinates

    public final void offsetDescendantRectToMyCoords(View descendant, Rect rect) {
        offsetRectBetweenParentAndChild(descendant, rect, true, false);
    }

    public final void offsetRectIntoDescendantCoords(View descendant, Rect rect) {
        offsetRectBetweenParentAndChild(descendant, rect, false, false);
    }

    void offsetRectBetweenParentAndChild(View descendant, Rect rect, boolean offsetFromChildToParent,
            boolean clipToBounds) {
        if (descendant == this) return;
        ViewParent theParent = descendant.mParent;
        while ((theParent != null) && (theParent instanceof View) && (theParent != this)) {
            if (offsetFromChildToParent) {
                rect.offset(descendant.mLeft - descendant.mScrollX, descendant.mTop - descendant.mScrollY);
                if (clipToBounds) {
                    View p = (View) theParent;
                    boolean intersected = rect.intersect(0, 0, p.mRight - p.mLeft, p.mBottom - p.mTop);
                    if (!intersected) rect.setEmpty();
                }
            } else {
                if (clipToBounds) {
                    View p = (View) theParent;
                    boolean intersected = rect.intersect(0, 0, p.mRight - p.mLeft, p.mBottom - p.mTop);
                    if (!intersected) rect.setEmpty();
                }
                rect.offset(descendant.mScrollX - descendant.mLeft, descendant.mScrollY - descendant.mTop);
            }
            descendant = (View) theParent;
            theParent = descendant.mParent;
        }
        if (theParent == this) {
            if (offsetFromChildToParent) {
                rect.offset(descendant.mLeft - descendant.mScrollX, descendant.mTop - descendant.mScrollY);
            } else {
                rect.offset(descendant.mScrollX - descendant.mLeft, descendant.mScrollY - descendant.mTop);
            }
        } else {
            throw new IllegalArgumentException("parameter must be a descendant of this view");
        }
    }

    public boolean getChildVisibleRect(View child, Rect r, Point offset) {
        final RectF rect = new RectF(r);
        if (!child.hasIdentityMatrix()) child.getMatrix().mapRect(rect);
        final int dx = child.mLeft - mScrollX;
        final int dy = child.mTop - mScrollY;
        rect.offset(dx, dy);
        if (offset != null) {
            if (!child.hasIdentityMatrix()) {
                float[] position = new float[] {offset.x, offset.y};
                child.getMatrix().mapPoints(position);
                offset.x = Math.round(position[0]);
                offset.y = Math.round(position[1]);
            }
            offset.x += dx;
            offset.y += dy;
        }
        final int width = mRight - mLeft;
        final int height = mBottom - mTop;
        boolean rectIsVisible = true;
        if (mParent == null || (mParent instanceof ViewGroup && ((ViewGroup) mParent).getClipChildren())) {
            rectIsVisible = rect.intersect(0, 0, width, height);
        }
        if (rectIsVisible && (mGroupFlags & CLIP_TO_PADDING_MASK) == CLIP_TO_PADDING_MASK) {
            rectIsVisible = rect.intersect(mPaddingLeft, mPaddingTop, width - mPaddingRight, height - mPaddingBottom);
        }
        r.set((int) Math.floor(rect.left), (int) Math.floor(rect.top), (int) Math.ceil(rect.right),
                (int) Math.ceil(rect.bottom));
        if (rectIsVisible && mParent != null) {
            if (mParent instanceof ViewGroup) {
                rectIsVisible = ((ViewGroup) mParent).getChildVisibleRect(this, r, offset);
            } else {
                rectIsVisible = mParent.getChildVisibleRect(this, r, offset);
            }
        }
        return rectIsVisible;
    }

    // ---------------------------------------------------------------- nested scrolling parent

    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) { return false; }

    public void onNestedScrollAccepted(View child, View target, int axes) { mNestedScrollAxes = axes; }

    public void onStopNestedScroll(View child) {
        stopNestedScroll();
        mNestedScrollAxes = 0;
    }

    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, null);
    }

    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        dispatchNestedPreScroll(dx, dy, consumed, null);
    }

    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
        return dispatchNestedFling(velocityX, velocityY, consumed);
    }

    public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
        return dispatchNestedPreFling(velocityX, velocityY);
    }

    public int getNestedScrollAxes() { return mNestedScrollAxes; }

    public boolean onNestedPrePerformAccessibilityAction(View target, int action, Bundle args) { return false; }

    // ---------------------------------------------------------------- ViewParent odds and ends

    @Override
    public void requestFitSystemWindows() {
        if (mParent != null) mParent.requestFitSystemWindows();
    }

    @Override
    public void notifySubtreeAccessibilityStateChanged(View child, View source, int changeType) {}

    @Override
    public CharSequence getAccessibilityClassName() { return ViewGroup.class.getName(); }

    @Override
    public void addChildrenForAccessibility(ArrayList<View> outChildren) {
        for (int i = 0; i < mChildrenCount; i++) {
            View child = mChildren[i];
            if ((child.mViewFlags & VISIBILITY_MASK) == VISIBLE) outChildren.add(child);
        }
    }

    // ---------------------------------------------------------------- layout params

    public static class LayoutParams {
        @Deprecated
        public static final int FILL_PARENT = -1;
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;

        public int width;
        public int height;

        private static final int[] LAYOUT_ATTRS = {android.R.attr.layout_width, android.R.attr.layout_height};

        public LayoutParams(Context c, AttributeSet attrs) {
            TypedArray a = c.obtainStyledAttributes(attrs, LAYOUT_ATTRS);
            setBaseAttributes(a, 0, 1);
            a.recycle();
        }

        public LayoutParams(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public LayoutParams(LayoutParams source) {
            this.width = source.width;
            this.height = source.height;
        }

        LayoutParams() {}

        protected void setBaseAttributes(TypedArray a, int widthAttr, int heightAttr) {
            width = a.getLayoutDimension(widthAttr, "layout_width");
            height = a.getLayoutDimension(heightAttr, "layout_height");
        }

        public void resolveLayoutDirection(int layoutDirection) {}

        /** framework-internal (hidden in AOSP). */
        public String debug(String output) {
            return output + "ViewGroup.LayoutParams={ width=" + sizeToString(width) + ", height="
                    + sizeToString(height) + " }";
        }

        /** framework-internal (hidden in AOSP). */
        protected static String sizeToString(int size) {
            if (size == WRAP_CONTENT) return "wrap-content";
            if (size == MATCH_PARENT) return "match-parent";
            return String.valueOf(size);
        }
    }

    public static class MarginLayoutParams extends ViewGroup.LayoutParams {
        public int leftMargin;
        public int topMargin;
        public int rightMargin;
        public int bottomMargin;
        private int startMargin = DEFAULT_MARGIN_RELATIVE;
        private int endMargin = DEFAULT_MARGIN_RELATIVE;

        /** framework-internal (hidden in AOSP). */
        public static final int DEFAULT_MARGIN_RELATIVE = Integer.MIN_VALUE;
        private static final int DEFAULT_MARGIN_RESOLVED = 0;
        private static final int UNDEFINED_MARGIN = DEFAULT_MARGIN_RELATIVE;
        private static final int LAYOUT_DIRECTION_MASK = 0x03;
        private static final int LEFT_MARGIN_UNDEFINED_MASK = 0x04;
        private static final int RIGHT_MARGIN_UNDEFINED_MASK = 0x08;
        private static final int RTL_COMPATIBILITY_MODE_MASK = 0x10;
        private static final int NEED_RESOLUTION_MASK = 0x20;

        byte mMarginFlags;

        private static final int[] MARGIN_ATTRS = {
            android.R.attr.layout_width, android.R.attr.layout_height, android.R.attr.layout_margin,
            android.R.attr.layout_marginLeft, android.R.attr.layout_marginTop, android.R.attr.layout_marginRight,
            android.R.attr.layout_marginBottom, android.R.attr.layout_marginStart, android.R.attr.layout_marginEnd,
            android.R.attr.layout_marginHorizontal, android.R.attr.layout_marginVertical,
        };

        public MarginLayoutParams(Context c, AttributeSet attrs) {
            super();
            TypedArray a = c.obtainStyledAttributes(attrs, MARGIN_ATTRS);
            setBaseAttributes(a, 0, 1);
            int margin = a.getDimensionPixelSize(2, -1);
            if (margin >= 0) {
                leftMargin = margin;
                topMargin = margin;
                rightMargin = margin;
                bottomMargin = margin;
            } else {
                int horizontalMargin = a.getDimensionPixelSize(9, -1);
                int verticalMargin = a.getDimensionPixelSize(10, -1);
                if (horizontalMargin >= 0) {
                    leftMargin = horizontalMargin;
                    rightMargin = horizontalMargin;
                } else {
                    leftMargin = a.getDimensionPixelSize(3, UNDEFINED_MARGIN);
                    if (leftMargin == UNDEFINED_MARGIN) {
                        mMarginFlags |= LEFT_MARGIN_UNDEFINED_MASK;
                        leftMargin = DEFAULT_MARGIN_RESOLVED;
                    }
                    rightMargin = a.getDimensionPixelSize(5, UNDEFINED_MARGIN);
                    if (rightMargin == UNDEFINED_MARGIN) {
                        mMarginFlags |= RIGHT_MARGIN_UNDEFINED_MASK;
                        rightMargin = DEFAULT_MARGIN_RESOLVED;
                    }
                }
                startMargin = a.getDimensionPixelSize(7, DEFAULT_MARGIN_RELATIVE);
                endMargin = a.getDimensionPixelSize(8, DEFAULT_MARGIN_RELATIVE);
                if (verticalMargin >= 0) {
                    topMargin = verticalMargin;
                    bottomMargin = verticalMargin;
                } else {
                    topMargin = a.getDimensionPixelSize(4, DEFAULT_MARGIN_RESOLVED);
                    bottomMargin = a.getDimensionPixelSize(6, DEFAULT_MARGIN_RESOLVED);
                }
                if (isMarginRelative()) mMarginFlags |= NEED_RESOLUTION_MASK;
            }
            mMarginFlags |= View.LAYOUT_DIRECTION_LTR;
            a.recycle();
        }

        public MarginLayoutParams(int width, int height) {
            super(width, height);
            mMarginFlags |= LEFT_MARGIN_UNDEFINED_MASK;
            mMarginFlags |= RIGHT_MARGIN_UNDEFINED_MASK;
            mMarginFlags &= ~NEED_RESOLUTION_MASK;
            mMarginFlags &= ~RTL_COMPATIBILITY_MODE_MASK;
        }

        public MarginLayoutParams(MarginLayoutParams source) {
            this.width = source.width;
            this.height = source.height;
            this.leftMargin = source.leftMargin;
            this.topMargin = source.topMargin;
            this.rightMargin = source.rightMargin;
            this.bottomMargin = source.bottomMargin;
            this.startMargin = source.startMargin;
            this.endMargin = source.endMargin;
            this.mMarginFlags = source.mMarginFlags;
        }

        public MarginLayoutParams(LayoutParams source) {
            super(source);
            mMarginFlags |= LEFT_MARGIN_UNDEFINED_MASK;
            mMarginFlags |= RIGHT_MARGIN_UNDEFINED_MASK;
            mMarginFlags &= ~NEED_RESOLUTION_MASK;
            mMarginFlags &= ~RTL_COMPATIBILITY_MODE_MASK;
        }

        /** framework-internal (hidden in AOSP). */
        public final void copyMarginsFrom(MarginLayoutParams source) {
            this.leftMargin = source.leftMargin;
            this.topMargin = source.topMargin;
            this.rightMargin = source.rightMargin;
            this.bottomMargin = source.bottomMargin;
            this.startMargin = source.startMargin;
            this.endMargin = source.endMargin;
            this.mMarginFlags = source.mMarginFlags;
        }

        public void setMargins(int left, int top, int right, int bottom) {
            leftMargin = left;
            topMargin = top;
            rightMargin = right;
            bottomMargin = bottom;
            mMarginFlags &= ~LEFT_MARGIN_UNDEFINED_MASK;
            mMarginFlags &= ~RIGHT_MARGIN_UNDEFINED_MASK;
            if (isMarginRelative()) mMarginFlags |= NEED_RESOLUTION_MASK;
            else mMarginFlags &= ~NEED_RESOLUTION_MASK;
        }

        /** framework-internal (hidden in AOSP). */
        public void setMarginsRelative(int start, int top, int end, int bottom) {
            startMargin = start;
            topMargin = top;
            endMargin = end;
            bottomMargin = bottom;
            mMarginFlags |= NEED_RESOLUTION_MASK;
        }

        public void setMarginStart(int start) {
            startMargin = start;
            mMarginFlags |= NEED_RESOLUTION_MASK;
        }

        public int getMarginStart() {
            if (startMargin != DEFAULT_MARGIN_RELATIVE) return startMargin;
            if ((mMarginFlags & NEED_RESOLUTION_MASK) == NEED_RESOLUTION_MASK) doResolveMargins();
            switch (mMarginFlags & LAYOUT_DIRECTION_MASK) {
                case View.LAYOUT_DIRECTION_RTL: return rightMargin;
                case View.LAYOUT_DIRECTION_LTR:
                default: return leftMargin;
            }
        }

        public void setMarginEnd(int end) {
            endMargin = end;
            mMarginFlags |= NEED_RESOLUTION_MASK;
        }

        public int getMarginEnd() {
            if (endMargin != DEFAULT_MARGIN_RELATIVE) return endMargin;
            if ((mMarginFlags & NEED_RESOLUTION_MASK) == NEED_RESOLUTION_MASK) doResolveMargins();
            switch (mMarginFlags & LAYOUT_DIRECTION_MASK) {
                case View.LAYOUT_DIRECTION_RTL: return leftMargin;
                case View.LAYOUT_DIRECTION_LTR:
                default: return rightMargin;
            }
        }

        public boolean isMarginRelative() {
            return (startMargin != DEFAULT_MARGIN_RELATIVE || endMargin != DEFAULT_MARGIN_RELATIVE);
        }

        public void setLayoutDirection(int layoutDirection) {
            if (layoutDirection != View.LAYOUT_DIRECTION_LTR && layoutDirection != View.LAYOUT_DIRECTION_RTL) return;
            if (layoutDirection != (mMarginFlags & LAYOUT_DIRECTION_MASK)) {
                mMarginFlags &= ~LAYOUT_DIRECTION_MASK;
                mMarginFlags |= (layoutDirection & LAYOUT_DIRECTION_MASK);
                if (isMarginRelative()) mMarginFlags |= NEED_RESOLUTION_MASK;
                else mMarginFlags &= ~NEED_RESOLUTION_MASK;
            }
        }

        public int getLayoutDirection() { return (mMarginFlags & LAYOUT_DIRECTION_MASK); }

        @Override
        public void resolveLayoutDirection(int layoutDirection) {
            setLayoutDirection(layoutDirection);
            if (!isMarginRelative() || (mMarginFlags & NEED_RESOLUTION_MASK) != NEED_RESOLUTION_MASK) return;
            doResolveMargins();
        }

        private void doResolveMargins() {
            switch (mMarginFlags & LAYOUT_DIRECTION_MASK) {
                case View.LAYOUT_DIRECTION_RTL:
                    leftMargin = (endMargin > DEFAULT_MARGIN_RELATIVE) ? endMargin : DEFAULT_MARGIN_RESOLVED;
                    rightMargin = (startMargin > DEFAULT_MARGIN_RELATIVE) ? startMargin : DEFAULT_MARGIN_RESOLVED;
                    break;
                case View.LAYOUT_DIRECTION_LTR:
                default:
                    if (startMargin > DEFAULT_MARGIN_RELATIVE) leftMargin = startMargin;
                    else if ((mMarginFlags & LEFT_MARGIN_UNDEFINED_MASK) != 0) leftMargin = DEFAULT_MARGIN_RESOLVED;
                    if (endMargin > DEFAULT_MARGIN_RELATIVE) rightMargin = endMargin;
                    else if ((mMarginFlags & RIGHT_MARGIN_UNDEFINED_MASK) != 0) rightMargin = DEFAULT_MARGIN_RESOLVED;
                    break;
            }
            mMarginFlags &= ~NEED_RESOLUTION_MASK;
        }

        /** framework-internal (hidden in AOSP). */
        public boolean isLayoutRtl() { return ((mMarginFlags & LAYOUT_DIRECTION_MASK) == View.LAYOUT_DIRECTION_RTL); }
    }
}
