package android.graphics.drawable;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.StateSet;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class StateListDrawable extends DrawableContainer {
    private StateListState mStateListState;
    private boolean mMutated;

    public StateListDrawable() { this(null, null); }

    StateListDrawable(StateListState state, Resources res) {
        final StateListState newState = new StateListState(state, this, res);
        setConstantState(newState);
        onStateChange(getState());
    }

    public void addState(int[] stateSet, Drawable drawable) {
        if (drawable != null) {
            mStateListState.addStateSet(stateSet, drawable);
            onStateChange(getState());
        }
    }

    @Override
    public boolean isStateful() { return true; }

    @Override
    public boolean hasFocusStateSpecified() { return mStateListState.hasFocusStateSpecified(); }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        final boolean changed = super.onStateChange(stateSet);
        int idx = mStateListState.indexOfStateSet(stateSet);
        if (idx < 0) idx = mStateListState.indexOfStateSet(StateSet.WILD_CARD);
        return selectDrawable(idx) || changed;
    }

    private static final int[] ATTRS = {android.R.attr.variablePadding, android.R.attr.constantSize, android.R.attr.dither,
            android.R.attr.enterFadeDuration, android.R.attr.exitFadeDuration, android.R.attr.autoMirrored, android.R.attr.visible};

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        final TypedArray a = obtainAttributes(r, theme, attrs, ATTRS);
        mStateListState.mVariablePadding = a.getBoolean(0, mStateListState.mVariablePadding);
        mStateListState.mConstantSize = a.getBoolean(1, mStateListState.mConstantSize);
        mStateListState.mAutoMirrored = a.getBoolean(5, mStateListState.mAutoMirrored);
        a.recycle();
        inflateChildElements(r, parser, attrs, theme);
        onStateChange(getState());
    }

    private void inflateChildElements(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        final StateListState state = mStateListState;
        final int innerDepth = parser.getDepth() + 1;
        int type;
        int depth;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT && ((depth = parser.getDepth()) >= innerDepth || type != XmlPullParser.END_TAG)) {
            if (type != XmlPullParser.START_TAG) continue;
            if (depth > innerDepth || !parser.getName().equals("item")) continue;
            final TypedArray a = obtainAttributes(r, theme, attrs, new int[] {android.R.attr.drawable});
            Drawable dr = a.getDrawable(0);
            a.recycle();
            final int[] states = extractStateSet(attrs);
            if (dr == null) {
                while ((type = parser.next()) == XmlPullParser.TEXT) {}
                if (type != XmlPullParser.START_TAG) {
                    throw new XmlPullParserException(parser.getPositionDescription() + ": <item> tag requires a 'drawable' attribute or child tag defining a drawable");
                }
                dr = Drawable.createFromXmlInner(r, parser, attrs, theme);
            }
            state.addStateSet(states, dr);
        }
    }

    static int[] extractStateSet(AttributeSet attrs) {
        int j = 0;
        final int numAttrs = attrs.getAttributeCount();
        int[] states = new int[numAttrs];
        for (int i = 0; i < numAttrs; i++) {
            final int stateResId = attrs.getAttributeNameResource(i);
            switch (stateResId) {
                case 0:
                    break;
                case android.R.attr.drawable:
                case android.R.attr.id:
                case android.R.attr.duration:
                case android.R.attr.fromId:
                case android.R.attr.toId:
                case android.R.attr.reversible:
                    continue;
                default:
                    states[j++] = attrs.getAttributeBooleanValue(i, false) ? stateResId : -stateResId;
            }
        }
        return StateSet.trimStateSet(states, j);
    }

    public int getStateCount() { return mStateListState.getChildCount(); }
    public int[] getStateSet(int index) { return mStateListState.mStateSets[index]; }
    public Drawable getStateDrawable(int index) { return mStateListState.getChild(index); }
    public int findStateDrawableIndex(int[] stateSet) { return mStateListState.indexOfStateSet(stateSet); }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mStateListState.mutate();
            mMutated = true;
        }
        return this;
    }

    static class StateListState extends DrawableContainerState {
        int[][] mStateSets;

        StateListState(StateListState orig, StateListDrawable owner, Resources res) {
            super(orig, owner, res);
            if (orig != null) mStateSets = orig.mStateSets.clone();
            else mStateSets = new int[getCapacity()][];
        }

        void mutate() {
            super.mutate();
            final int[][] stateSets = new int[mStateSets.length][];
            for (int i = mStateSets.length - 1; i >= 0; i--) stateSets[i] = mStateSets[i] != null ? mStateSets[i].clone() : null;
            mStateSets = stateSets;
        }

        int addStateSet(int[] stateSet, Drawable drawable) {
            final int pos = addChild(drawable);
            mStateSets[pos] = stateSet;
            return pos;
        }

        int indexOfStateSet(int[] stateSet) {
            final int[][] stateSets = mStateSets;
            final int count = getChildCount();
            for (int i = 0; i < count; i++) if (StateSet.stateSetMatches(stateSets[i], stateSet)) return i;
            return -1;
        }

        boolean hasFocusStateSpecified() {
            for (int i = 0; i < getChildCount(); i++) {
                for (int s : mStateSets[i]) if (s == android.R.attr.state_focused) return true;
            }
            return false;
        }

        @Override
        public Drawable newDrawable() { return new StateListDrawable(this, null); }

        @Override
        public Drawable newDrawable(Resources res) { return new StateListDrawable(this, res); }

        @Override
        public void growArray(int oldSize, int newSize) {
            super.growArray(oldSize, newSize);
            final int[][] newStateSets = new int[newSize][];
            System.arraycopy(mStateSets, 0, newStateSets, 0, oldSize);
            mStateSets = newStateSets;
        }
    }

    @Override
    protected void setConstantState(DrawableContainerState state) {
        super.setConstantState(state);
        if (state instanceof StateListState) mStateListState = (StateListState) state;
    }
}
