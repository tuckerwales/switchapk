package android.graphics.drawable;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class LevelListDrawable extends DrawableContainer {
    private LevelListState mLevelListState;
    private boolean mMutated;

    public LevelListDrawable() { this(null, null); }

    private LevelListDrawable(LevelListState state, Resources res) {
        final LevelListState as = new LevelListState(state, this, res);
        setConstantState(as);
        onLevelChange(getLevel());
    }

    public void addLevel(int low, int high, Drawable drawable) {
        if (drawable != null) {
            mLevelListState.addLevel(low, high, drawable);
            onLevelChange(getLevel());
        }
    }

    @Override
    protected boolean onLevelChange(int level) {
        int idx = mLevelListState.indexOfLevel(level);
        if (selectDrawable(idx)) return true;
        return super.onLevelChange(level);
    }

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        int type;
        int low = 0;
        final int innerDepth = parser.getDepth() + 1;
        int depth;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT && ((depth = parser.getDepth()) >= innerDepth || type != XmlPullParser.END_TAG)) {
            if (type != XmlPullParser.START_TAG || depth > innerDepth || !parser.getName().equals("item")) continue;
            final TypedArray a = obtainAttributes(r, theme, attrs, new int[] {android.R.attr.minLevel, android.R.attr.maxLevel, android.R.attr.drawable});
            low = a.getInt(0, 0);
            int high = a.getInt(1, 0);
            Drawable dr = a.getDrawable(2);
            a.recycle();
            if (dr == null) {
                while ((type = parser.next()) == XmlPullParser.TEXT) {}
                if (type != XmlPullParser.START_TAG) throw new XmlPullParserException(parser.getPositionDescription() + ": <item> tag requires a 'drawable' attribute or child tag defining a drawable");
                dr = Drawable.createFromXmlInner(r, parser, attrs, theme);
            }
            mLevelListState.addLevel(low, high, dr);
        }
        onLevelChange(getLevel());
    }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mLevelListState.mLows = mLevelListState.mLows.clone();
            mLevelListState.mHighs = mLevelListState.mHighs.clone();
            mMutated = true;
        }
        return this;
    }

    private static final class LevelListState extends DrawableContainerState {
        private int[] mLows;
        private int[] mHighs;

        LevelListState(LevelListState orig, LevelListDrawable owner, Resources res) {
            super(orig, owner, res);
            if (orig != null) {
                mLows = orig.mLows.clone();
                mHighs = orig.mHighs.clone();
            } else {
                mLows = new int[getCapacity()];
                mHighs = new int[getCapacity()];
            }
        }

        public void addLevel(int low, int high, Drawable drawable) {
            int pos = addChild(drawable);
            mLows[pos] = low;
            mHighs[pos] = high;
        }

        public int indexOfLevel(int level) {
            final int[] lows = mLows;
            final int[] highs = mHighs;
            final int N = getChildCount();
            for (int i = 0; i < N; i++) if (level >= lows[i] && level <= highs[i]) return i;
            return -1;
        }

        @Override
        public Drawable newDrawable() { return new LevelListDrawable(this, null); }
        @Override
        public Drawable newDrawable(Resources res) { return new LevelListDrawable(this, res); }

        @Override
        public void growArray(int oldSize, int newSize) {
            super.growArray(oldSize, newSize);
            int[] newInts = new int[newSize];
            System.arraycopy(mLows, 0, newInts, 0, oldSize);
            mLows = newInts;
            newInts = new int[newSize];
            System.arraycopy(mHighs, 0, newInts, 0, oldSize);
            mHighs = newInts;
        }
    }

    @Override
    protected void setConstantState(DrawableContainerState state) {
        super.setConstantState(state);
        if (state instanceof LevelListState) mLevelListState = (LevelListState) state;
    }
}
