package android.util;

public class StateSet {
    public static final int[] WILD_CARD = new int[0];
    public static final int[] NOTHING = new int[] {0};

    /** framework-internal (hidden in AOSP): view state bits for {@link #get(int)}. */
    public static final int VIEW_STATE_WINDOW_FOCUSED = 1;
    public static final int VIEW_STATE_SELECTED = 1 << 1;
    public static final int VIEW_STATE_FOCUSED = 1 << 2;
    public static final int VIEW_STATE_ENABLED = 1 << 3;
    public static final int VIEW_STATE_PRESSED = 1 << 4;
    public static final int VIEW_STATE_ACTIVATED = 1 << 5;
    public static final int VIEW_STATE_ACCELERATED = 1 << 6;
    public static final int VIEW_STATE_HOVERED = 1 << 7;
    public static final int VIEW_STATE_DRAG_CAN_ACCEPT = 1 << 8;
    public static final int VIEW_STATE_DRAG_HOVERED = 1 << 9;

    private static final int[] VIEW_STATE_ATTRS = {
        android.R.attr.state_window_focused,
        android.R.attr.state_selected,
        android.R.attr.state_focused,
        android.R.attr.state_enabled,
        android.R.attr.state_pressed,
        android.R.attr.state_activated,
        android.R.attr.state_accelerated,
        android.R.attr.state_hovered,
        android.R.attr.state_drag_can_accept,
        android.R.attr.state_drag_hovered,
    };

    private static final int[][] sViewStateSets = new int[1 << VIEW_STATE_ATTRS.length][];

    /** framework-internal (hidden in AOSP): the state set for a mask of VIEW_STATE_* bits. */
    public static int[] get(int mask) {
        if (mask < 0 || mask >= sViewStateSets.length) {
            throw new IllegalArgumentException("Invalid state set mask");
        }
        synchronized (sViewStateSets) {
            int[] set = sViewStateSets[mask];
            if (set == null) {
                set = new int[Integer.bitCount(mask)];
                int n = 0;
                for (int i = 0; i < VIEW_STATE_ATTRS.length; i++) {
                    if ((mask & (1 << i)) != 0) set[n++] = VIEW_STATE_ATTRS[i];
                }
                sViewStateSets[mask] = set;
            }
            return set;
        }
    }

    public static boolean isWildCard(int[] stateSetOrSpec) { return stateSetOrSpec.length == 0 || stateSetOrSpec[0] == 0; }

    public static boolean stateSetMatches(int[] stateSpec, int[] stateSet) {
        if (stateSet == null) return (stateSpec == null || isWildCard(stateSpec));
        int stateSpecSize = stateSpec.length;
        int stateSetSize = stateSet.length;
        for (int i = 0; i < stateSpecSize; i++) {
            int stateSpecState = stateSpec[i];
            if (stateSpecState == 0) return true;
            final boolean mustMatch;
            if (stateSpecState > 0) {
                mustMatch = true;
            } else {
                mustMatch = false;
                stateSpecState = -stateSpecState;
            }
            boolean found = false;
            for (int j = 0; j < stateSetSize; j++) {
                final int state = stateSet[j];
                if (state == 0) {
                    if (mustMatch) return false;
                    else break;
                }
                if (state == stateSpecState) {
                    if (mustMatch) {
                        found = true;
                        break;
                    } else {
                        return false;
                    }
                }
            }
            if (mustMatch && !found) return false;
        }
        return true;
    }

    public static boolean stateSetMatches(int[] stateSpec, int state) {
        int stateSpecSize = stateSpec.length;
        for (int i = 0; i < stateSpecSize; i++) {
            int stateSpecState = stateSpec[i];
            if (stateSpecState == 0) return true;
            if (stateSpecState > 0) {
                if (state != stateSpecState) return false;
            } else {
                if (state == -stateSpecState) return false;
            }
        }
        return true;
    }

    public static int[] trimStateSet(int[] states, int newSize) {
        if (states.length == newSize) return states;
        int[] trimmedStates = new int[newSize];
        System.arraycopy(states, 0, trimmedStates, 0, newSize);
        return trimmedStates;
    }

    public static String dump(int[] states) {
        StringBuilder sb = new StringBuilder();
        for (int s : states) sb.append(s).append(' ');
        return sb.toString();
    }
}
