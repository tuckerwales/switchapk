package android.graphics.drawable;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.SystemClock;
import android.util.AttributeSet;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class AnimationDrawable extends DrawableContainer implements Runnable, Animatable {
    private AnimationState mAnimationState;
    private int mCurFrame = 0;
    private boolean mRunning;
    private boolean mAnimating;
    private boolean mMutated;

    public AnimationDrawable() { this(null, null); }

    private AnimationDrawable(AnimationState state, Resources res) {
        final AnimationState as = new AnimationState(state, this, res);
        setConstantState(as);
        if (state != null) setFrame(0, true, false);
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        final boolean changed = super.setVisible(visible, restart);
        if (visible) {
            if (restart || changed) {
                final boolean startFromZero = restart || (!mRunning && !mAnimationState.mOneShot) || mCurFrame >= mAnimationState.getChildCount();
                setFrame(startFromZero ? 0 : mCurFrame, true, mAnimating);
            }
        } else {
            unscheduleSelf(this);
        }
        return changed;
    }

    public void start() {
        mAnimating = true;
        if (!isRunning()) setFrame(0, false, mAnimationState.getChildCount() > 1 || !mAnimationState.mOneShot);
    }

    public void stop() {
        mAnimating = false;
        if (isRunning()) {
            mCurFrame = 0;
            unscheduleSelf(this);
        }
    }

    public boolean isRunning() { return mRunning; }

    public void run() { nextFrame(false); }

    @Override
    public void unscheduleSelf(Runnable what) {
        mRunning = false;
        super.unscheduleSelf(what);
    }

    public int getNumberOfFrames() { return mAnimationState.getChildCount(); }
    public Drawable getFrame(int index) { return mAnimationState.getChild(index); }
    public int getDuration(int i) { return mAnimationState.mDurations[i]; }
    public boolean isOneShot() { return mAnimationState.mOneShot; }
    public void setOneShot(boolean oneShot) { mAnimationState.mOneShot = oneShot; }

    public void addFrame(Drawable frame, int duration) {
        mAnimationState.addFrame(frame, duration);
        if (!mRunning) setFrame(0, true, false);
    }

    private void nextFrame(boolean unschedule) {
        int nextFrame = mCurFrame + 1;
        final int numFrames = mAnimationState.getChildCount();
        final boolean isLastFrame = mAnimationState.mOneShot && nextFrame >= (numFrames - 1);
        if (!mAnimationState.mOneShot && nextFrame >= numFrames) nextFrame = 0;
        setFrame(nextFrame, unschedule, !isLastFrame);
    }

    private void setFrame(int frame, boolean unschedule, boolean animate) {
        if (frame >= mAnimationState.getChildCount()) return;
        mAnimating = animate;
        mCurFrame = frame;
        selectDrawable(frame);
        if (unschedule || animate) unscheduleSelf(this);
        if (animate) {
            mCurFrame = frame;
            mRunning = true;
            scheduleSelf(this, SystemClock.uptimeMillis() + mAnimationState.mDurations[frame]);
        }
    }

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        final TypedArray a = obtainAttributes(r, theme, attrs, new int[] {android.R.attr.oneshot, android.R.attr.variablePadding, android.R.attr.visible});
        mAnimationState.mOneShot = a.getBoolean(0, mAnimationState.mOneShot);
        mAnimationState.mVariablePadding = a.getBoolean(1, mAnimationState.mVariablePadding);
        a.recycle();
        final int innerDepth = parser.getDepth() + 1;
        int type;
        int depth;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT && ((depth = parser.getDepth()) >= innerDepth || type != XmlPullParser.END_TAG)) {
            if (type != XmlPullParser.START_TAG || depth > innerDepth || !parser.getName().equals("item")) continue;
            final TypedArray b = obtainAttributes(r, theme, attrs, new int[] {android.R.attr.drawable, android.R.attr.duration});
            final int duration = b.getInt(1, -1);
            Drawable dr = b.getDrawable(0);
            b.recycle();
            if (dr == null) {
                while ((type = parser.next()) == XmlPullParser.TEXT) {}
                if (type != XmlPullParser.START_TAG) throw new XmlPullParserException(parser.getPositionDescription() + ": <item> tag requires a 'drawable' attribute or child tag defining a drawable");
                dr = Drawable.createFromXmlInner(r, parser, attrs, theme);
            }
            mAnimationState.addFrame(dr, duration);
            if (dr != null) dr.setCallback(this);
        }
        setFrame(0, true, false);
    }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mAnimationState.mDurations = mAnimationState.mDurations.clone();
            mMutated = true;
        }
        return this;
    }

    private static final class AnimationState extends DrawableContainerState {
        private int[] mDurations;
        private boolean mOneShot = false;

        AnimationState(AnimationState orig, AnimationDrawable owner, Resources res) {
            super(orig, owner, res);
            if (orig != null) {
                mDurations = orig.mDurations.clone();
                mOneShot = orig.mOneShot;
            } else {
                mDurations = new int[getCapacity()];
                mOneShot = false;
            }
        }

        @Override
        public Drawable newDrawable() { return new AnimationDrawable(this, null); }
        @Override
        public Drawable newDrawable(Resources res) { return new AnimationDrawable(this, res); }

        public void addFrame(Drawable dr, int dur) {
            int pos = super.addChild(dr);
            mDurations[pos] = dur;
        }

        @Override
        public void growArray(int oldSize, int newSize) {
            super.growArray(oldSize, newSize);
            int[] newDurations = new int[newSize];
            System.arraycopy(mDurations, 0, newDurations, 0, oldSize);
            mDurations = newDurations;
        }
    }

    @Override
    protected void setConstantState(DrawableContainerState state) {
        super.setConstantState(state);
        if (state instanceof AnimationState) mAnimationState = (AnimationState) state;
    }
}
