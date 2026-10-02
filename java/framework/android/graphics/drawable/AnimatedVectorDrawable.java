package android.graphics.drawable;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Plays the animators named by {@code target} against the vector's groups and
 * paths. Each {@code start} clones the prototypes so the start values are fresh.
 * pathData is not morphed.
 */
public class AnimatedVectorDrawable extends Drawable implements Animatable2 {
    private static final String TAG = "AnimatedVectorDrawable";

    private VectorDrawable mVector;
    private final ArrayList<Pending> mPending = new ArrayList<Pending>();
    private AnimatorSet mSet;
    private boolean mRunning;
    private final ArrayList<AnimationCallback> mCallbacks = new ArrayList<AnimationCallback>();

    public AnimatedVectorDrawable() {}

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme)
            throws XmlPullParserException, IOException {
        final TypedArray a = obtainAttributes(r, theme, attrs, new int[] {android.R.attr.drawable});
        Drawable loaded = null;
        try {
            int id = a.getResourceId(0, 0);
            if (id != 0) loaded = r.getDrawable(id, theme);
        } catch (RuntimeException e) {
            loaded = null;
        }
        a.recycle();
        int type;
        final int depth = parser.getDepth();
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > depth)) {
            if (type != XmlPullParser.START_TAG) continue;
            String tag = parser.getName();
            if ("vector".equals(tag)) {
                // inflate consumes through the vector end tag. The loop's next() moves on.
                VectorDrawable vd = new VectorDrawable();
                vd.inflate(r, parser, attrs, theme);
                loaded = vd;
            } else if ("target".equals(tag)) {
                parseTarget(r, theme, parser, attrs);
            }
        }
        if (loaded instanceof VectorDrawable) mVector = (VectorDrawable) loaded;
        else if (loaded != null) Log.w(TAG, "animated-vector drawable is not a vector");
        wireCallback();
    }

    private void parseTarget(Resources r, Resources.Theme theme, XmlPullParser parser, AttributeSet attrs)
            throws XmlPullParserException, IOException {
        TypedArray a = obtainAttributes(r, theme, attrs, new int[] {android.R.attr.name, android.R.attr.animation});
        String name = a.getString(0);
        int animId = a.getResourceId(1, 0);
        a.recycle();
        int depth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > depth)) {
        }
        if (name == null || animId == 0) return;
        try {
            Animator anim = AnimatorInflater.loadAnimator(r, theme, animId);
            if (anim != null) mPending.add(new Pending(name, anim));
        } catch (RuntimeException e) {
            Log.w(TAG, "target " + name + " animator failed", e);
        }
    }

    private void wireCallback() {
        if (mVector == null) return;
        mVector.setCallback(new Callback() {
            public void invalidateDrawable(Drawable who) { invalidateSelf(); }
            public void scheduleDrawable(Drawable who, Runnable what, long when) { scheduleSelf(what, when); }
            public void unscheduleDrawable(Drawable who, Runnable what) { unscheduleSelf(what); }
        });
    }

    @Override
    public void draw(Canvas canvas) { if (mVector != null) mVector.draw(canvas); }

    @Override
    protected void onBoundsChange(Rect bounds) { if (mVector != null) mVector.setBounds(bounds); }

    @Override
    public int getIntrinsicWidth() { return mVector != null ? mVector.getIntrinsicWidth() : -1; }
    @Override
    public int getIntrinsicHeight() { return mVector != null ? mVector.getIntrinsicHeight() : -1; }
    @Override
    public void setAlpha(int alpha) { if (mVector != null) mVector.setAlpha(alpha); }
    @Override
    public int getAlpha() { return mVector != null ? mVector.getAlpha() : 255; }
    @Override
    public void setColorFilter(ColorFilter colorFilter) { if (mVector != null) mVector.setColorFilter(colorFilter); }
    @Override
    public void setTintList(android.content.res.ColorStateList tint) { if (mVector != null) mVector.setTintList(tint); }
    @Override
    public void setTintMode(android.graphics.PorterDuff.Mode tintMode) {
        if (mVector != null) mVector.setTintMode(tintMode);
    }
    @Override
    public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    @Override
    public boolean isStateful() { return mVector != null && mVector.isStateful(); }

    @Override
    protected boolean onStateChange(int[] state) { return mVector != null && mVector.setState(state); }

    public void start() {
        if (mRunning) return;
        mSet = buildSet();
        if (mSet == null) {
            fireStart();
            fireEnd();
            return;
        }
        mRunning = true;
        fireStart();
        mSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!mRunning) return;
                mRunning = false;
                fireEnd();
            }
        });
        mSet.start();
    }

    public void stop() {
        if (mSet != null && mRunning) mSet.cancel();
        mRunning = false;
    }

    public boolean isRunning() { return mRunning; }

    /** Cancels a running set and seeks every target back to the start value. */
    public void reset() {
        if (mSet != null) mSet.cancel();
        mSet = null;
        mRunning = false;
        AnimatorSet fresh = buildSet();
        if (fresh != null) seekZero(fresh);
    }

    public void reverse() {}

    public boolean canReverse() { return false; }

    public void registerAnimationCallback(AnimationCallback callback) {
        if (callback != null && !mCallbacks.contains(callback)) mCallbacks.add(callback);
    }

    public boolean unregisterAnimationCallback(AnimationCallback callback) { return mCallbacks.remove(callback); }

    public void clearAnimationCallbacks() { mCallbacks.clear(); }

    private AnimatorSet buildSet() {
        if (mVector == null || mPending.isEmpty()) return null;
        ArrayList<Animator> kids = new ArrayList<Animator>();
        for (int i = 0; i < mPending.size(); i++) {
            Pending pending = mPending.get(i);
            Object target = mVector.getTargetByName(pending.name);
            if (target == null) {
                Log.w(TAG, "No target named " + pending.name);
                continue;
            }
            Animator anim = pending.animator.clone();
            anim.setTarget(target);
            hook(anim);
            kids.add(anim);
        }
        if (kids.isEmpty()) return null;
        AnimatorSet set = new AnimatorSet();
        set.playTogether(kids);
        return set;
    }

    /** Invalidates this drawable on every frame of a value animator, including set children. */
    private void hook(Animator anim) {
        if (anim instanceof AnimatorSet) {
            ArrayList<Animator> kids = ((AnimatorSet) anim).getChildAnimations();
            for (int i = 0; i < kids.size(); i++) hook(kids.get(i));
        }
        if (anim instanceof ValueAnimator) {
            ((ValueAnimator) anim).addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) { invalidateSelf(); }
            });
        }
    }

    private void seekZero(Animator anim) {
        if (anim instanceof AnimatorSet) {
            ArrayList<Animator> kids = ((AnimatorSet) anim).getChildAnimations();
            for (int i = 0; i < kids.size(); i++) seekZero(kids.get(i));
        }
        if (anim instanceof ValueAnimator) ((ValueAnimator) anim).setCurrentFraction(0f);
    }

    private void fireStart() {
        for (AnimationCallback c : new ArrayList<AnimationCallback>(mCallbacks)) c.onAnimationStart(this);
    }

    private void fireEnd() {
        for (AnimationCallback c : new ArrayList<AnimationCallback>(mCallbacks)) c.onAnimationEnd(this);
    }

    @Override
    public ConstantState getConstantState() {
        final VectorDrawable vector = mVector;
        final ArrayList<Pending> pending = new ArrayList<Pending>(mPending);
        return new ConstantState() {
            public Drawable newDrawable() {
                AnimatedVectorDrawable d = new AnimatedVectorDrawable();
                if (vector != null && vector.getConstantState() != null) {
                    d.mVector = (VectorDrawable) vector.getConstantState().newDrawable();
                    d.wireCallback();
                }
                for (int i = 0; i < pending.size(); i++) {
                    Pending p = pending.get(i);
                    d.mPending.add(new Pending(p.name, p.animator.clone()));
                }
                return d;
            }
            public int getChangingConfigurations() { return 0; }
        };
    }

    private static final class Pending {
        final String name;
        final Animator animator;

        Pending(String name, Animator animator) {
            this.name = name;
            this.animator = animator;
        }
    }
}
