package android.animation;

import android.os.Handler;
import android.os.Looper;
import android.view.animation.AnimationUtils;
import java.util.ArrayList;

/**
 * Plays child animators together or in a dependency order. A duration set on the set replaces
 * each child's duration. Seeking one child to its end starts the animators that wait on it.
 */
public final class AnimatorSet extends Animator {
    private ArrayList<Node> mNodes = new ArrayList<Node>();
    private long mDuration = -1;
    private boolean mDurationSet;
    private long mStartDelay;
    private TimeInterpolator mInterpolator;
    private boolean mInterpolatorSet;
    private boolean mStarted;
    private boolean mEnding;
    private boolean mNotifiedEnd;
    private long mPlayStart = -1;
    private long mSeekTime;
    private ChildListener mChildListener = new ChildListener();

    public AnimatorSet() {}

    public void playTogether(Animator... items) {
        if (items == null) return;
        if (items.length == 0) return;
        Builder builder = play(items[0]);
        for (int i = 1; i < items.length; i++) builder.with(items[i]);
    }

    public void playTogether(java.util.Collection<Animator> items) {
        if (items == null || items.isEmpty()) return;
        Animator[] arr = items.toArray(new Animator[items.size()]);
        playTogether(arr);
    }

    public void playSequentially(Animator... items) {
        if (items == null || items.length == 0) return;
        if (items.length == 1) {
            play(items[0]);
            return;
        }
        for (int i = 0; i < items.length - 1; i++) play(items[i]).before(items[i + 1]);
    }

    public void playSequentially(java.util.List<Animator> items) {
        if (items == null || items.isEmpty()) return;
        playSequentially(items.toArray(new Animator[items.size()]));
    }

    public ArrayList<Animator> getChildAnimations() {
        ArrayList<Animator> out = new ArrayList<Animator>();
        for (int i = 0; i < mNodes.size(); i++) out.add(mNodes.get(i).animation);
        return out;
    }

    public Builder play(Animator anim) { return new Builder(nodeOf(anim)); }

    @Override
    public void setTarget(Object target) {
        for (int i = 0; i < mNodes.size(); i++) mNodes.get(i).animation.setTarget(target);
    }

    @Override
    public void setInterpolator(TimeInterpolator interpolator) {
        mInterpolator = interpolator;
        mInterpolatorSet = true;
        for (int i = 0; i < mNodes.size(); i++) mNodes.get(i).animation.setInterpolator(interpolator);
    }

    @Override
    public TimeInterpolator getInterpolator() { return mInterpolator; }

    @Override
    public void cancel() {
        if (!mStarted || mNotifiedEnd) return;
        mEnding = true;
        notifyListeners(NOTIFY_CANCEL);
        for (int i = 0; i < mNodes.size(); i++) {
            Node node = mNodes.get(i);
            if (node.mStarted && !node.mEnded) node.animation.cancel();
        }
        mEnding = false;
        finishSet();
    }

    @Override
    public void end() {
        if (mNotifiedEnd) return;
        if (!mStarted) mStarted = true;
        mEnding = true;
        ArrayList<Node> copy = new ArrayList<Node>(mNodes);
        for (int i = 0; i < copy.size(); i++) {
            Node node = copy.get(i);
            if (!node.mEnded) node.animation.end();
        }
        mEnding = false;
        finishSet();
    }

    @Override
    public boolean isRunning() {
        for (int i = 0; i < mNodes.size(); i++) if (mNodes.get(i).animation.isRunning()) return true;
        return false;
    }

    @Override
    public boolean isStarted() { return mStarted && !mNotifiedEnd; }

    @Override
    public long getStartDelay() { return mStartDelay; }

    @Override
    public void setStartDelay(long startDelay) {
        if (startDelay < 0) throw new IllegalArgumentException("Animators cannot have negative start delay");
        mStartDelay = startDelay;
    }

    @Override
    public long getDuration() { return mDurationSet ? mDuration : -1; }

    @Override
    public AnimatorSet setDuration(long duration) {
        if (duration < 0) throw new IllegalArgumentException("Animators cannot have negative duration: " + duration);
        mDuration = duration;
        mDurationSet = true;
        for (int i = 0; i < mNodes.size(); i++) mNodes.get(i).animation.setDuration(duration);
        return this;
    }

    @Override
    public void setupStartValues() {
        for (int i = 0; i < mNodes.size(); i++) mNodes.get(i).animation.setupStartValues();
    }

    @Override
    public void setupEndValues() {
        for (int i = 0; i < mNodes.size(); i++) mNodes.get(i).animation.setupEndValues();
    }

    @Override
    public void pause() {
        if (!isStarted() || mPaused) return;
        mPaused = true;
        for (int i = 0; i < mNodes.size(); i++) {
            Node node = mNodes.get(i);
            if (node.animation.isStarted()) node.animation.pause();
        }
        notifyListeners(NOTIFY_PAUSE);
    }

    @Override
    public void resume() {
        if (!mPaused) return;
        mPaused = false;
        for (int i = 0; i < mNodes.size(); i++) {
            Node node = mNodes.get(i);
            if (node.animation.isPaused()) node.animation.resume();
        }
        notifyListeners(NOTIFY_RESUME);
    }

    @Override
    public void start() {
        if (mStarted && !mNotifiedEnd) return;
        mStarted = true;
        mNotifiedEnd = false;
        if (mStartDelay > 0) {
            Looper looper = Looper.myLooper();
            if (looper == null) throw new IllegalStateException("Animators may only be run on Looper threads");
            new Handler(looper).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mStarted && !mNotifiedEnd) startChildren();
                }
            }, mStartDelay);
            return;
        }
        startChildren();
    }

    @Override
    public long getTotalDuration() {
        if (mStartDelay == DURATION_INFINITE) return DURATION_INFINITE;
        long longest = 0;
        for (int i = 0; i < mNodes.size(); i++) {
            long path = pathDuration(mNodes.get(i), 0);
            if (path == DURATION_INFINITE) return DURATION_INFINITE;
            if (path > longest) longest = path;
        }
        return mStartDelay + longest;
    }

    private long pathDuration(Node node, int guard) {
        if (node == null || guard > mNodes.size()) return 0;
        long own = node.animation.getTotalDuration();
        if (own == DURATION_INFINITE) return DURATION_INFINITE;
        long wait = 0;
        for (int i = 0; i < node.mDependencies.size(); i++) {
            long dep = pathDuration(node.mDependencies.get(i), guard + 1);
            if (dep == DURATION_INFINITE) return DURATION_INFINITE;
            if (dep > wait) wait = dep;
        }
        return wait + own;
    }

    @Override
    public AnimatorSet clone() {
        AnimatorSet set = (AnimatorSet) super.clone();
        set.mStarted = false;
        set.mEnding = false;
        set.mNotifiedEnd = false;
        set.mPaused = false;
        set.mNodes = new ArrayList<Node>();
        set.mChildListener = set.new ChildListener();
        ArrayList<Node> cloned = new ArrayList<Node>();
        for (int i = 0; i < mNodes.size(); i++) {
            Node node = mNodes.get(i);
            Node copy = new Node(node.animation.clone());
            copy.animation.removeListener(mChildListener);
            cloned.add(copy);
        }
        for (int i = 0; i < mNodes.size(); i++) {
            Node node = mNodes.get(i);
            Node copy = cloned.get(i);
            for (int d = 0; d < node.mDependencies.size(); d++) {
                int index = mNodes.indexOf(node.mDependencies.get(d));
                if (index >= 0) copy.mDependencies.add(cloned.get(index));
            }
        }
        set.mNodes.addAll(cloned);
        return set;
    }

    @Override
    public String toString() { return "AnimatorSet@" + Integer.toHexString(hashCode()) + " children " + mNodes.size(); }

    private void startChildren() {
        for (int i = 0; i < mNodes.size(); i++) {
            Node node = mNodes.get(i);
            node.mStarted = false;
            node.mEnded = false;
            if (!node.mListening) {
                node.animation.addListener(mChildListener);
                node.mListening = true;
            }
        }
        notifyListeners(NOTIFY_START, false);
        mPlayStart = AnimationUtils.currentAnimationTimeMillis();
        mSeekTime = 0;
        startReady();
        if (mNodes.isEmpty() || allEnded()) finishSet();
    }

    public void setCurrentPlayTime(long playTime) {
        if (playTime < 0) throw new IllegalArgumentException("Animators cannot have negative play time: " + playTime);
        long total = getTotalDuration();
        if (total != DURATION_INFINITE && playTime > total) playTime = total;
        long local = playTime - mStartDelay;
        if (local < 0) local = 0;
        for (int i = 0; i < mNodes.size(); i++) {
            Node node = mNodes.get(i);
            seekAnimator(node.animation, local - nodeStart(node));
        }
        mSeekTime = playTime;
    }

    public long getCurrentPlayTime() {
        if (mStarted && !mNotifiedEnd && mPlayStart >= 0) {
            return Math.max(0, AnimationUtils.currentAnimationTimeMillis() - mPlayStart);
        }
        return mSeekTime;
    }

    /** Seeks to the end when the set is idle. Reverses children that are already running. */
    public void reverse() {
        if (!mStarted || mNotifiedEnd) {
            long total = getTotalDuration();
            if (total == DURATION_INFINITE || total < 0) return;
            setCurrentPlayTime(total);
            return;
        }
        mEnding = true;
        for (int i = 0; i < mNodes.size(); i++) {
            Animator child = mNodes.get(i).animation;
            if (child instanceof ValueAnimator && child.isStarted()) ((ValueAnimator) child).reverse();
        }
        mEnding = false;
    }

    private long nodeStart(Node node) {
        long wait = 0;
        for (int i = 0; i < node.mDependencies.size(); i++) {
            long end = pathDuration(node.mDependencies.get(i), 0);
            if (end > wait) wait = end;
        }
        return wait;
    }

    private void seekAnimator(Animator anim, long local) {
        if (anim instanceof AnimatorSet) {
            ((AnimatorSet) anim).setCurrentPlayTime(Math.max(0, local));
            return;
        }
        if (!(anim instanceof ValueAnimator)) return;
        ValueAnimator va = (ValueAnimator) anim;
        if (va.getRepeatCount() == ValueAnimator.INFINITE || va.getDuration() < 0) {
            if (local > 0) va.setCurrentPlayTime(local);
            else va.setCurrentFraction(0f);
            return;
        }
        long span = va.getDuration() * ((long) va.getRepeatCount() + 1);
        if (local <= 0) va.setCurrentFraction(0f);
        else if (span <= 0 || local >= span) va.setCurrentFraction(va.getRepeatCount() + 1f);
        else va.setCurrentPlayTime(local);
    }

    private void startReady() {
        boolean progressed = true;
        int guard = 0;
        while (progressed && guard++ < mNodes.size() + 1) {
            progressed = false;
            for (int i = 0; i < mNodes.size(); i++) {
                Node node = mNodes.get(i);
                if (node.mStarted || node.mEnded) continue;
                if (!dependenciesEnded(node)) continue;
                node.mStarted = true;
                node.animation.start();
                progressed = true;
            }
        }
    }

    private boolean dependenciesEnded(Node node) {
        for (int i = 0; i < node.mDependencies.size(); i++) {
            if (!node.mDependencies.get(i).mEnded) return false;
        }
        return true;
    }

    private boolean allEnded() {
        for (int i = 0; i < mNodes.size(); i++) if (!mNodes.get(i).mEnded) return false;
        return true;
    }

    private void onChildEnded(Animator animation) {
        Node node = findNode(animation);
        if (node == null || node.mEnded) return;
        node.mEnded = true;
        node.mStarted = true;
        if (!mEnding) startReady();
        if (allEnded()) finishSet();
    }

    private void finishSet() {
        if (mNotifiedEnd) return;
        mNotifiedEnd = true;
        mStarted = false;
        notifyListeners(NOTIFY_END, false);
    }

    private Node findNode(Animator animation) {
        for (int i = 0; i < mNodes.size(); i++) {
            if (mNodes.get(i).animation == animation) return mNodes.get(i);
        }
        return null;
    }

    private Node nodeOf(Animator anim) {
        if (anim == null) throw new NullPointerException("Animators passed to AnimatorSet must not be null");
        for (int i = 0; i < mNodes.size(); i++) {
            if (mNodes.get(i).animation == anim) return mNodes.get(i);
        }
        if (mDurationSet) anim.setDuration(mDuration);
        if (mInterpolatorSet) anim.setInterpolator(mInterpolator);
        Node node = new Node(anim);
        mNodes.add(node);
        return node;
    }

    private final class ChildListener extends AnimatorListenerAdapter {
        @Override
        public void onAnimationEnd(Animator animation) { onChildEnded(animation); }
    }

    private static final class Node {
        final Animator animation;
        final ArrayList<Node> mDependencies = new ArrayList<Node>();
        boolean mStarted;
        boolean mEnded;
        boolean mListening;

        Node(Animator animation) { this.animation = animation; }
    }

    public final class Builder {
        private final Node mNode;

        Builder(Node node) { mNode = node; }

        public Builder with(Animator anim) {
            Node other = nodeOf(anim);
            other.mDependencies.clear();
            other.mDependencies.addAll(mNode.mDependencies);
            return this;
        }

        public Builder before(Animator anim) {
            Node other = nodeOf(anim);
            if (!other.mDependencies.contains(mNode)) other.mDependencies.add(mNode);
            return this;
        }

        public Builder after(Animator anim) {
            Node other = nodeOf(anim);
            if (!mNode.mDependencies.contains(other)) mNode.mDependencies.add(other);
            return this;
        }

        public Builder after(long delay) {
            long current = mNode.animation.getStartDelay();
            mNode.animation.setStartDelay(current + delay);
            return this;
        }
    }
}
