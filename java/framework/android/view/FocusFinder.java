package android.view;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

/** Finds the next focusable view in a direction (port of AOSP FocusFinder). */
public class FocusFinder {
    private static final ThreadLocal<FocusFinder> tlFocusFinder = new ThreadLocal<FocusFinder>() {
        @Override
        protected FocusFinder initialValue() { return new FocusFinder(); }
    };

    final Rect mFocusedRect = new Rect();
    final Rect mOtherRect = new Rect();
    final Rect mBestCandidateRect = new Rect();

    private FocusFinder() {}

    public static FocusFinder getInstance() { return tlFocusFinder.get(); }

    public final View findNextFocus(ViewGroup root, View focused, int direction) {
        return findNextFocus(root, focused, null, direction);
    }

    public View findNextFocusFromRect(ViewGroup root, Rect focusedRect, int direction) {
        mFocusedRect.set(focusedRect);
        return findNextFocus(root, null, mFocusedRect, direction);
    }

    public View findNextKeyboardNavigationCluster(View root, View currentCluster, int direction) { return null; }

    private View findNextFocus(ViewGroup root, View focused, Rect focusedRect, int direction) {
        View next = null;
        if (focused != null) next = findNextUserSpecifiedFocus(root, focused, direction);
        if (next != null) return next;
        ArrayList<View> focusables = new ArrayList<View>();
        root.addFocusables(focusables, direction);
        if (!focusables.isEmpty()) next = findNextFocus(root, focused, focusedRect, direction, focusables);
        return next;
    }

    private View findNextUserSpecifiedFocus(ViewGroup root, View focused, int direction) {
        View userSetNextFocus = focused.findUserSetNextFocus(root, direction);
        View cycleCheck = userSetNextFocus;
        boolean cycleStep = true;
        while (userSetNextFocus != null) {
            if (userSetNextFocus.isFocusable() && userSetNextFocus.getVisibility() == View.VISIBLE
                    && (!userSetNextFocus.isInTouchMode() || userSetNextFocus.isFocusableInTouchMode())) {
                return userSetNextFocus;
            }
            userSetNextFocus = userSetNextFocus.findUserSetNextFocus(root, direction);
            if (cycleStep = !cycleStep) {
                cycleCheck = cycleCheck.findUserSetNextFocus(root, direction);
                if (cycleCheck == userSetNextFocus) break;
            }
        }
        return null;
    }

    private View findNextFocus(ViewGroup root, View focused, Rect focusedRect, int direction,
            ArrayList<View> focusables) {
        if (focused != null) {
            if (focusedRect == null) focusedRect = mFocusedRect;
            focused.getFocusedRect(focusedRect);
            root.offsetDescendantRectToMyCoords(focused, focusedRect);
        } else if (focusedRect == null) {
            focusedRect = mFocusedRect;
            switch (direction) {
                case View.FOCUS_RIGHT:
                case View.FOCUS_DOWN:
                    setFocusTopLeft(root, focusedRect);
                    break;
                case View.FOCUS_FORWARD:
                    if (root.isLayoutRtl()) setFocusBottomRight(root, focusedRect);
                    else setFocusTopLeft(root, focusedRect);
                    break;
                case View.FOCUS_LEFT:
                case View.FOCUS_UP:
                    setFocusBottomRight(root, focusedRect);
                    break;
                case View.FOCUS_BACKWARD:
                    if (root.isLayoutRtl()) setFocusTopLeft(root, focusedRect);
                    else setFocusBottomRight(root, focusedRect);
                    break;
            }
        }
        switch (direction) {
            case View.FOCUS_FORWARD:
            case View.FOCUS_BACKWARD:
                return findNextFocusInRelativeDirection(focusables, root, focused, direction);
            case View.FOCUS_UP:
            case View.FOCUS_DOWN:
            case View.FOCUS_LEFT:
            case View.FOCUS_RIGHT:
                return findNextFocusInAbsoluteDirection(focusables, root, focused, focusedRect, direction);
            default:
                throw new IllegalArgumentException("Unknown direction: " + direction);
        }
    }

    private View findNextFocusInRelativeDirection(ArrayList<View> focusables, ViewGroup root, View focused,
            int direction) {
        sortRelative(focusables, root);
        final int count = focusables.size();
        if (count < 1) return null;
        switch (direction) {
            case View.FOCUS_FORWARD: {
                if (focused != null) {
                    int position = focusables.lastIndexOf(focused);
                    if (position >= 0 && position + 1 < count) return focusables.get(position + 1);
                }
                return focusables.get(0);
            }
            case View.FOCUS_BACKWARD: {
                if (focused != null) {
                    int position = focusables.indexOf(focused);
                    if (position > 0) return focusables.get(position - 1);
                }
                return focusables.get(count - 1);
            }
        }
        return null;
    }

    /** Sorts views into rows (by top), then by left within a row, like AOSP FocusSorter. */
    private void sortRelative(ArrayList<View> views, ViewGroup root) {
        final HashMap<View, Rect> rects = new HashMap<View, Rect>();
        for (View v : views) {
            Rect r = new Rect();
            v.getDrawingRect(r);
            root.offsetDescendantRectToMyCoords(v, r);
            rects.put(v, r);
        }
        Collections.sort(views, new Comparator<View>() {
            public int compare(View a, View b) {
                if (a == b) return 0;
                Rect ra = rects.get(a);
                Rect rb = rects.get(b);
                int r = ra.top - rb.top;
                if (r == 0) return ra.bottom - rb.bottom;
                return r;
            }
        });
        int rowTop = Integer.MIN_VALUE;
        int rowBottom = Integer.MIN_VALUE;
        int rowStart = 0;
        final boolean rtl = root.isLayoutRtl();
        Comparator<View> sideComparator = new Comparator<View>() {
            public int compare(View a, View b) {
                if (a == b) return 0;
                Rect ra = rects.get(a);
                Rect rb = rects.get(b);
                int r = ra.left - rb.left;
                if (r == 0) return ra.right - rb.right;
                return rtl ? -r : r;
            }
        };
        int count = views.size();
        for (int i = 0; i < count; i++) {
            Rect cur = rects.get(views.get(i));
            if (cur.top >= rowBottom) {
                if (i - rowStart > 1) Collections.sort(views.subList(rowStart, i), sideComparator);
                rowTop = cur.top;
                rowBottom = cur.bottom;
                rowStart = i;
            } else {
                rowTop = Math.min(rowTop, cur.top);
                rowBottom = Math.max(rowBottom, cur.bottom);
            }
        }
        if (count - rowStart > 1) Collections.sort(views.subList(rowStart, count), sideComparator);
    }

    private void setFocusBottomRight(ViewGroup root, Rect focusedRect) {
        final int rootBottom = root.getScrollY() + root.getHeight();
        final int rootRight = root.getScrollX() + root.getWidth();
        focusedRect.set(rootRight, rootBottom, rootRight, rootBottom);
    }

    private void setFocusTopLeft(ViewGroup root, Rect focusedRect) {
        final int rootTop = root.getScrollY();
        final int rootLeft = root.getScrollX();
        focusedRect.set(rootLeft, rootTop, rootLeft, rootTop);
    }

    View findNextFocusInAbsoluteDirection(ArrayList<View> focusables, ViewGroup root, View focused, Rect focusedRect,
            int direction) {
        mBestCandidateRect.set(focusedRect);
        switch (direction) {
            case View.FOCUS_LEFT:
                mBestCandidateRect.offset(focusedRect.width() + 1, 0);
                break;
            case View.FOCUS_RIGHT:
                mBestCandidateRect.offset(-(focusedRect.width() + 1), 0);
                break;
            case View.FOCUS_UP:
                mBestCandidateRect.offset(0, focusedRect.height() + 1);
                break;
            case View.FOCUS_DOWN:
                mBestCandidateRect.offset(0, -(focusedRect.height() + 1));
                break;
        }
        View closest = null;
        int numFocusables = focusables.size();
        for (int i = 0; i < numFocusables; i++) {
            View focusable = focusables.get(i);
            if (focusable == focused || focusable == root) continue;
            focusable.getFocusedRect(mOtherRect);
            root.offsetDescendantRectToMyCoords(focusable, mOtherRect);
            if (isBetterCandidate(direction, focusedRect, mOtherRect, mBestCandidateRect)) {
                mBestCandidateRect.set(mOtherRect);
                closest = focusable;
            }
        }
        return closest;
    }

    public View findNearestTouchable(ViewGroup root, int x, int y, int direction, int[] deltas) {
        ArrayList<View> touchables = root.getTouchables();
        int minDistance = Integer.MAX_VALUE;
        View closest = null;
        int numTouchables = touchables.size();
        int edgeSlop = ViewConfiguration.get(root.getContext()).getScaledEdgeSlop();
        Rect closestBounds = new Rect();
        Rect touchableBounds = mOtherRect;
        for (int i = 0; i < numTouchables; i++) {
            View touchable = touchables.get(i);
            touchable.getDrawingRect(touchableBounds);
            root.offsetRectBetweenParentAndChild(touchable, touchableBounds, true, true);
            if (!isTouchCandidate(x, y, touchableBounds, direction)) continue;
            int distance = Integer.MAX_VALUE;
            switch (direction) {
                case View.FOCUS_LEFT: distance = x - touchableBounds.right + 1; break;
                case View.FOCUS_RIGHT: distance = touchableBounds.left; break;
                case View.FOCUS_UP: distance = y - touchableBounds.bottom + 1; break;
                case View.FOCUS_DOWN: distance = touchableBounds.top; break;
            }
            if (distance < edgeSlop) {
                if (closest == null || closestBounds.contains(touchableBounds)
                        || (!touchableBounds.contains(closestBounds) && distance < minDistance)) {
                    minDistance = distance;
                    closest = touchable;
                    closestBounds.set(touchableBounds);
                    switch (direction) {
                        case View.FOCUS_LEFT: deltas[0] = -distance; break;
                        case View.FOCUS_RIGHT: deltas[0] = distance; break;
                        case View.FOCUS_UP: deltas[1] = -distance; break;
                        case View.FOCUS_DOWN: deltas[1] = distance; break;
                    }
                }
            }
        }
        return closest;
    }

    private boolean isTouchCandidate(int x, int y, Rect destRect, int direction) {
        switch (direction) {
            case View.FOCUS_LEFT: return destRect.left <= x && destRect.top <= y && y <= destRect.bottom;
            case View.FOCUS_RIGHT: return destRect.left >= x && destRect.top <= y && y <= destRect.bottom;
            case View.FOCUS_UP: return destRect.top <= y && destRect.left <= x && x <= destRect.right;
            case View.FOCUS_DOWN: return destRect.top >= y && destRect.left <= x && x <= destRect.right;
        }
        throw new IllegalArgumentException("direction must be one of {FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT}.");
    }

    boolean isBetterCandidate(int direction, Rect source, Rect rect1, Rect rect2) {
        if (!isCandidate(source, rect1, direction)) return false;
        if (!isCandidate(source, rect2, direction)) return true;
        if (beamBeats(direction, source, rect1, rect2)) return true;
        if (beamBeats(direction, source, rect2, rect1)) return false;
        return getWeightedDistanceFor(majorAxisDistance(direction, source, rect1),
                minorAxisDistance(direction, source, rect1))
                < getWeightedDistanceFor(majorAxisDistance(direction, source, rect2),
                        minorAxisDistance(direction, source, rect2));
    }

    boolean beamBeats(int direction, Rect source, Rect rect1, Rect rect2) {
        final boolean rect1InSrcBeam = beamsOverlap(direction, source, rect1);
        final boolean rect2InSrcBeam = beamsOverlap(direction, source, rect2);
        if (rect2InSrcBeam || !rect1InSrcBeam) return false;
        if (!isToDirectionOf(direction, source, rect2)) return true;
        if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) return true;
        return majorAxisDistance(direction, source, rect1) < majorAxisDistanceToFarEdge(direction, source, rect2);
    }

    long getWeightedDistanceFor(long majorAxisDistance, long minorAxisDistance) {
        return 13 * majorAxisDistance * majorAxisDistance + minorAxisDistance * minorAxisDistance;
    }

    boolean isCandidate(Rect srcRect, Rect destRect, int direction) {
        switch (direction) {
            case View.FOCUS_LEFT:
                return (srcRect.right > destRect.right || srcRect.left >= destRect.right) && srcRect.left > destRect.left;
            case View.FOCUS_RIGHT:
                return (srcRect.left < destRect.left || srcRect.right <= destRect.left) && srcRect.right < destRect.right;
            case View.FOCUS_UP:
                return (srcRect.bottom > destRect.bottom || srcRect.top >= destRect.bottom) && srcRect.top > destRect.top;
            case View.FOCUS_DOWN:
                return (srcRect.top < destRect.top || srcRect.bottom <= destRect.top)
                        && srcRect.bottom < destRect.bottom;
        }
        throw new IllegalArgumentException("direction must be one of {FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT}.");
    }

    boolean beamsOverlap(int direction, Rect rect1, Rect rect2) {
        switch (direction) {
            case View.FOCUS_LEFT:
            case View.FOCUS_RIGHT:
                return (rect2.bottom > rect1.top) && (rect2.top < rect1.bottom);
            case View.FOCUS_UP:
            case View.FOCUS_DOWN:
                return (rect2.right > rect1.left) && (rect2.left < rect1.right);
        }
        throw new IllegalArgumentException("direction must be one of {FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT}.");
    }

    boolean isToDirectionOf(int direction, Rect src, Rect dest) {
        switch (direction) {
            case View.FOCUS_LEFT: return src.left >= dest.right;
            case View.FOCUS_RIGHT: return src.right <= dest.left;
            case View.FOCUS_UP: return src.top >= dest.bottom;
            case View.FOCUS_DOWN: return src.bottom <= dest.top;
        }
        throw new IllegalArgumentException("direction must be one of {FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT}.");
    }

    static int majorAxisDistance(int direction, Rect source, Rect dest) {
        return Math.max(0, majorAxisDistanceRaw(direction, source, dest));
    }

    static int majorAxisDistanceRaw(int direction, Rect source, Rect dest) {
        switch (direction) {
            case View.FOCUS_LEFT: return source.left - dest.right;
            case View.FOCUS_RIGHT: return dest.left - source.right;
            case View.FOCUS_UP: return source.top - dest.bottom;
            case View.FOCUS_DOWN: return dest.top - source.bottom;
        }
        throw new IllegalArgumentException("direction must be one of {FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT}.");
    }

    static int majorAxisDistanceToFarEdge(int direction, Rect source, Rect dest) {
        return Math.max(1, majorAxisDistanceToFarEdgeRaw(direction, source, dest));
    }

    static int majorAxisDistanceToFarEdgeRaw(int direction, Rect source, Rect dest) {
        switch (direction) {
            case View.FOCUS_LEFT: return source.left - dest.left;
            case View.FOCUS_RIGHT: return dest.right - source.right;
            case View.FOCUS_UP: return source.top - dest.top;
            case View.FOCUS_DOWN: return dest.bottom - source.bottom;
        }
        throw new IllegalArgumentException("direction must be one of {FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT}.");
    }

    static int minorAxisDistance(int direction, Rect source, Rect dest) {
        switch (direction) {
            case View.FOCUS_LEFT:
            case View.FOCUS_RIGHT:
                return Math.abs(((source.top + source.height() / 2) - ((dest.top + dest.height() / 2))));
            case View.FOCUS_UP:
            case View.FOCUS_DOWN:
                return Math.abs(((source.left + source.width() / 2) - ((dest.left + dest.width() / 2))));
        }
        throw new IllegalArgumentException("direction must be one of {FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT}.");
    }
}
