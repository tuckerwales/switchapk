package android.app;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.View;
import java.util.ArrayList;

/** Saved form of a back stack entry (AOSP BackStackState). */
final class BackStackState implements Parcelable {
    final int[] mOps;
    final int mTransition;
    final int mTransitionStyle;
    final String mName;
    final int mIndex;
    final int mBreadCrumbTitleRes;
    final CharSequence mBreadCrumbTitleText;
    final int mBreadCrumbShortTitleRes;
    final CharSequence mBreadCrumbShortTitleText;
    final boolean mReorderingAllowed;

    BackStackState(FragmentManagerImpl fm, BackStackRecord bse) {
        final int numOps = bse.mOps.size();
        mOps = new int[numOps * 6];
        if (!bse.mAddToBackStack) throw new IllegalStateException("Not on back stack");
        int pos = 0;
        for (int opNum = 0; opNum < numOps; opNum++) {
            final BackStackRecord.Op op = bse.mOps.get(opNum);
            mOps[pos++] = op.cmd;
            mOps[pos++] = op.fragment != null ? op.fragment.mIndex : -1;
            mOps[pos++] = op.enterAnim;
            mOps[pos++] = op.exitAnim;
            mOps[pos++] = op.popEnterAnim;
            mOps[pos++] = op.popExitAnim;
        }
        mTransition = bse.mTransition;
        mTransitionStyle = bse.mTransitionStyle;
        mName = bse.mName;
        mIndex = bse.mIndex;
        mBreadCrumbTitleRes = bse.mBreadCrumbTitleRes;
        mBreadCrumbTitleText = bse.mBreadCrumbTitleText;
        mBreadCrumbShortTitleRes = bse.mBreadCrumbShortTitleRes;
        mBreadCrumbShortTitleText = bse.mBreadCrumbShortTitleText;
        mReorderingAllowed = bse.mReorderingAllowed;
    }

    BackStackRecord instantiate(FragmentManagerImpl fm) {
        BackStackRecord bse = new BackStackRecord(fm);
        int pos = 0;
        while (pos < mOps.length) {
            BackStackRecord.Op op = new BackStackRecord.Op();
            op.cmd = mOps[pos++];
            int findex = mOps[pos++];
            op.fragment = findex >= 0 ? fm.mActive.get(findex) : null;
            op.enterAnim = mOps[pos++];
            op.exitAnim = mOps[pos++];
            op.popEnterAnim = mOps[pos++];
            op.popExitAnim = mOps[pos++];
            bse.mEnterAnim = op.enterAnim;
            bse.mExitAnim = op.exitAnim;
            bse.mPopEnterAnim = op.popEnterAnim;
            bse.mPopExitAnim = op.popExitAnim;
            bse.addOp(op);
        }
        bse.mTransition = mTransition;
        bse.mTransitionStyle = mTransitionStyle;
        bse.mName = mName;
        bse.mIndex = mIndex;
        bse.mAddToBackStack = true;
        bse.mBreadCrumbTitleRes = mBreadCrumbTitleRes;
        bse.mBreadCrumbTitleText = mBreadCrumbTitleText;
        bse.mBreadCrumbShortTitleRes = mBreadCrumbShortTitleRes;
        bse.mBreadCrumbShortTitleText = mBreadCrumbShortTitleText;
        bse.mReorderingAllowed = mReorderingAllowed;
        bse.bumpBackStackNesting(1);
        return bse;
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) { dest.writeValue(this); }

    public static final Parcelable.Creator<BackStackState> CREATOR = new Parcelable.Creator<BackStackState>() {
        public BackStackState createFromParcel(Parcel in) { return (BackStackState) in.readValue(null); }

        public BackStackState[] newArray(int size) { return new BackStackState[size]; }
    };
}

/** A fragment transaction and back stack entry (port of AOSP BackStackRecord, no animations). */
final class BackStackRecord extends FragmentTransaction
        implements FragmentManager.BackStackEntry, FragmentManagerImpl.OpGenerator {
    static final String TAG = FragmentManagerImpl.TAG;

    final FragmentManagerImpl mManager;

    static final int OP_NULL = 0;
    static final int OP_ADD = 1;
    static final int OP_REPLACE = 2;
    static final int OP_REMOVE = 3;
    static final int OP_HIDE = 4;
    static final int OP_SHOW = 5;
    static final int OP_DETACH = 6;
    static final int OP_ATTACH = 7;
    static final int OP_SET_PRIMARY_NAV = 8;
    static final int OP_UNSET_PRIMARY_NAV = 9;

    static final class Op {
        int cmd;
        Fragment fragment;
        int enterAnim;
        int exitAnim;
        int popEnterAnim;
        int popExitAnim;

        Op() {}

        Op(int cmd, Fragment fragment) {
            this.cmd = cmd;
            this.fragment = fragment;
        }
    }

    ArrayList<Op> mOps = new ArrayList<Op>();
    int mEnterAnim;
    int mExitAnim;
    int mPopEnterAnim;
    int mPopExitAnim;
    int mTransition;
    int mTransitionStyle;
    boolean mAddToBackStack;
    boolean mAllowAddToBackStack = true;
    String mName;
    boolean mCommitted;
    int mIndex = -1;
    boolean mReorderingAllowed;
    ArrayList<Runnable> mCommitRunnables;
    int mBreadCrumbTitleRes;
    CharSequence mBreadCrumbTitleText;
    int mBreadCrumbShortTitleRes;
    CharSequence mBreadCrumbShortTitleText;

    BackStackRecord(FragmentManagerImpl manager) { mManager = manager; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("BackStackEntry{");
        sb.append(Integer.toHexString(System.identityHashCode(this)));
        if (mIndex >= 0) {
            sb.append(" #");
            sb.append(mIndex);
        }
        if (mName != null) {
            sb.append(" ");
            sb.append(mName);
        }
        sb.append("}");
        return sb.toString();
    }

    public int getId() { return mIndex; }

    public int getBreadCrumbTitleRes() { return mBreadCrumbTitleRes; }

    public int getBreadCrumbShortTitleRes() { return mBreadCrumbShortTitleRes; }

    public CharSequence getBreadCrumbTitle() {
        if (mBreadCrumbTitleRes != 0 && mManager.mHost != null) {
            return mManager.mHost.getContext().getText(mBreadCrumbTitleRes);
        }
        return mBreadCrumbTitleText;
    }

    public CharSequence getBreadCrumbShortTitle() {
        if (mBreadCrumbShortTitleRes != 0 && mManager.mHost != null) {
            return mManager.mHost.getContext().getText(mBreadCrumbShortTitleRes);
        }
        return mBreadCrumbShortTitleText;
    }

    void addOp(Op op) {
        mOps.add(op);
        op.enterAnim = mEnterAnim;
        op.exitAnim = mExitAnim;
        op.popEnterAnim = mPopEnterAnim;
        op.popExitAnim = mPopExitAnim;
    }

    public FragmentTransaction add(Fragment fragment, String tag) {
        doAddOp(0, fragment, tag, OP_ADD);
        return this;
    }

    public FragmentTransaction add(int containerViewId, Fragment fragment) {
        doAddOp(containerViewId, fragment, null, OP_ADD);
        return this;
    }

    public FragmentTransaction add(int containerViewId, Fragment fragment, String tag) {
        doAddOp(containerViewId, fragment, tag, OP_ADD);
        return this;
    }

    private void doAddOp(int containerViewId, Fragment fragment, String tag, int opcmd) {
        if (mManager.mHost != null && mManager.mHost.getContext() != null) {
            final int targetSdk = mManager.mHost.getContext().getApplicationInfo().targetSdkVersion;
            final Class<?> fragmentClass = fragment.getClass();
            final int modifiers = fragmentClass.getModifiers();
            if (targetSdk > 25 && (fragmentClass.isAnonymousClass() || !java.lang.reflect.Modifier.isPublic(modifiers)
                    || (fragmentClass.isMemberClass() && !java.lang.reflect.Modifier.isStatic(modifiers)))) {
                throw new IllegalStateException("Fragment " + fragmentClass.getCanonicalName()
                        + " must be a public static class to be  properly recreated from instance state.");
            }
        }
        fragment.mFragmentManager = mManager;
        if (tag != null) {
            if (fragment.mTag != null && !tag.equals(fragment.mTag)) {
                throw new IllegalStateException("Can't change tag of fragment " + fragment + ": was " + fragment.mTag
                        + " now " + tag);
            }
            fragment.mTag = tag;
        }
        if (containerViewId != 0) {
            if (containerViewId == View.NO_ID) {
                throw new IllegalArgumentException("Can't add fragment " + fragment + " with tag " + tag
                        + " to container view with no id");
            }
            if (fragment.mFragmentId != 0 && fragment.mFragmentId != containerViewId) {
                throw new IllegalStateException("Can't change container ID of fragment " + fragment + ": was "
                        + fragment.mFragmentId + " now " + containerViewId);
            }
            fragment.mContainerId = fragment.mFragmentId = containerViewId;
        }
        addOp(new Op(opcmd, fragment));
    }

    public FragmentTransaction replace(int containerViewId, Fragment fragment) { return replace(containerViewId, fragment, null); }

    public FragmentTransaction replace(int containerViewId, Fragment fragment, String tag) {
        if (containerViewId == 0) throw new IllegalArgumentException("Must use non-zero containerViewId");
        doAddOp(containerViewId, fragment, tag, OP_REPLACE);
        return this;
    }

    public FragmentTransaction remove(Fragment fragment) {
        addOp(new Op(OP_REMOVE, fragment));
        return this;
    }

    public FragmentTransaction hide(Fragment fragment) {
        addOp(new Op(OP_HIDE, fragment));
        return this;
    }

    public FragmentTransaction show(Fragment fragment) {
        addOp(new Op(OP_SHOW, fragment));
        return this;
    }

    public FragmentTransaction detach(Fragment fragment) {
        addOp(new Op(OP_DETACH, fragment));
        return this;
    }

    public FragmentTransaction attach(Fragment fragment) {
        addOp(new Op(OP_ATTACH, fragment));
        return this;
    }

    public FragmentTransaction setPrimaryNavigationFragment(Fragment fragment) {
        addOp(new Op(OP_SET_PRIMARY_NAV, fragment));
        return this;
    }

    public FragmentTransaction setCustomAnimations(int enter, int exit) { return setCustomAnimations(enter, exit, 0, 0); }

    public FragmentTransaction setCustomAnimations(int enter, int exit, int popEnter, int popExit) {
        mEnterAnim = enter;
        mExitAnim = exit;
        mPopEnterAnim = popEnter;
        mPopExitAnim = popExit;
        return this;
    }

    public FragmentTransaction setTransition(int transition) {
        mTransition = transition;
        return this;
    }

    public FragmentTransaction addSharedElement(View sharedElement, String name) { return this; }

    public FragmentTransaction setTransitionStyle(int styleRes) {
        mTransitionStyle = styleRes;
        return this;
    }

    public FragmentTransaction addToBackStack(String name) {
        if (!mAllowAddToBackStack) {
            throw new IllegalStateException("This FragmentTransaction is not allowed to be added to the back stack.");
        }
        mAddToBackStack = true;
        mName = name;
        return this;
    }

    public boolean isAddToBackStackAllowed() { return mAllowAddToBackStack; }

    public FragmentTransaction disallowAddToBackStack() {
        if (mAddToBackStack) {
            throw new IllegalStateException("This transaction is already being added to the back stack");
        }
        mAllowAddToBackStack = false;
        return this;
    }

    public FragmentTransaction setBreadCrumbTitle(int res) {
        mBreadCrumbTitleRes = res;
        mBreadCrumbTitleText = null;
        return this;
    }

    public FragmentTransaction setBreadCrumbTitle(CharSequence text) {
        mBreadCrumbTitleRes = 0;
        mBreadCrumbTitleText = text;
        return this;
    }

    public FragmentTransaction setBreadCrumbShortTitle(int res) {
        mBreadCrumbShortTitleRes = res;
        mBreadCrumbShortTitleText = null;
        return this;
    }

    public FragmentTransaction setBreadCrumbShortTitle(CharSequence text) {
        mBreadCrumbShortTitleRes = 0;
        mBreadCrumbShortTitleText = text;
        return this;
    }

    public FragmentTransaction setReorderingAllowed(boolean reorderingAllowed) {
        mReorderingAllowed = reorderingAllowed;
        return this;
    }

    void bumpBackStackNesting(int amt) {
        if (!mAddToBackStack) return;
        final int numOps = mOps.size();
        for (int opNum = 0; opNum < numOps; opNum++) {
            final Op op = mOps.get(opNum);
            if (op.fragment != null) op.fragment.mBackStackNesting += amt;
        }
    }

    public FragmentTransaction runOnCommit(Runnable runnable) {
        if (runnable == null) throw new IllegalArgumentException("runnable cannot be null");
        disallowAddToBackStack();
        if (mCommitRunnables == null) mCommitRunnables = new ArrayList<Runnable>();
        mCommitRunnables.add(runnable);
        return this;
    }

    void runOnCommitRunnables() {
        if (mCommitRunnables != null) {
            for (int i = 0; i < mCommitRunnables.size(); i++) mCommitRunnables.get(i).run();
            mCommitRunnables = null;
        }
    }

    public int commit() { return commitInternal(false); }

    public int commitAllowingStateLoss() { return commitInternal(true); }

    public void commitNow() {
        disallowAddToBackStack();
        mManager.execSingleAction(this, false);
    }

    public void commitNowAllowingStateLoss() {
        disallowAddToBackStack();
        mManager.execSingleAction(this, true);
    }

    int commitInternal(boolean allowStateLoss) {
        if (mCommitted) throw new IllegalStateException("commit already called");
        mCommitted = true;
        if (mAddToBackStack) mIndex = mManager.allocBackStackIndex(this);
        else mIndex = -1;
        mManager.enqueueAction(this, allowStateLoss);
        return mIndex;
    }

    public boolean generateOps(ArrayList<BackStackRecord> records, ArrayList<Boolean> isRecordPop) {
        records.add(this);
        isRecordPop.add(false);
        if (mAddToBackStack) mManager.addBackStackState(this);
        return true;
    }

    void executeOps() {
        final int numOps = mOps.size();
        for (int opNum = 0; opNum < numOps; opNum++) {
            final Op op = mOps.get(opNum);
            final Fragment f = op.fragment;
            switch (op.cmd) {
                case OP_ADD: mManager.addFragment(f, false); break;
                case OP_REMOVE: mManager.removeFragment(f); break;
                case OP_HIDE: mManager.hideFragment(f); break;
                case OP_SHOW: mManager.showFragment(f); break;
                case OP_DETACH: mManager.detachFragment(f); break;
                case OP_ATTACH: mManager.attachFragment(f); break;
                case OP_SET_PRIMARY_NAV: mManager.setPrimaryNavigationFragment(f); break;
                case OP_UNSET_PRIMARY_NAV: mManager.setPrimaryNavigationFragment(null); break;
                default: throw new IllegalArgumentException("Unknown cmd: " + op.cmd);
            }
            if (!mReorderingAllowed && op.cmd != OP_ADD && f != null) mManager.moveFragmentToExpectedState(f);
        }
        if (!mReorderingAllowed) mManager.moveToState(mManager.mCurState, true);
    }

    void executePopOps(boolean moveToState) {
        for (int opNum = mOps.size() - 1; opNum >= 0; opNum--) {
            final Op op = mOps.get(opNum);
            Fragment f = op.fragment;
            switch (op.cmd) {
                case OP_ADD: mManager.removeFragment(f); break;
                case OP_REMOVE: mManager.addFragment(f, false); break;
                case OP_HIDE: mManager.showFragment(f); break;
                case OP_SHOW: mManager.hideFragment(f); break;
                case OP_DETACH: mManager.attachFragment(f); break;
                case OP_ATTACH: mManager.detachFragment(f); break;
                case OP_SET_PRIMARY_NAV: mManager.setPrimaryNavigationFragment(null); break;
                case OP_UNSET_PRIMARY_NAV: mManager.setPrimaryNavigationFragment(f); break;
                default: throw new IllegalArgumentException("Unknown cmd: " + op.cmd);
            }
            if (!mReorderingAllowed && op.cmd != OP_REMOVE && f != null) mManager.moveFragmentToExpectedState(f);
        }
        if (!mReorderingAllowed && moveToState) mManager.moveToState(mManager.mCurState, true);
    }

    /** Turns replace into removes of the container's fragments plus an add (AOSP expandReplaceOps). */
    Fragment expandReplaceOps(ArrayList<Fragment> added) {
        Fragment oldPrimaryNav = null;
        for (int opNum = 0; opNum < mOps.size(); opNum++) {
            final Op op = mOps.get(opNum);
            switch (op.cmd) {
                case OP_ADD:
                case OP_ATTACH:
                    added.add(op.fragment);
                    break;
                case OP_REMOVE:
                case OP_DETACH:
                    added.remove(op.fragment);
                    if (op.fragment == oldPrimaryNav) {
                        mOps.add(opNum, new Op(OP_UNSET_PRIMARY_NAV, op.fragment));
                        opNum++;
                        oldPrimaryNav = null;
                    }
                    break;
                case OP_REPLACE: {
                    final Fragment f = op.fragment;
                    final int containerId = f.mContainerId;
                    boolean alreadyAdded = false;
                    for (int i = added.size() - 1; i >= 0; i--) {
                        final Fragment old = added.get(i);
                        if (old.mContainerId == containerId) {
                            if (old == f) {
                                alreadyAdded = true;
                            } else {
                                if (old == oldPrimaryNav) {
                                    mOps.add(opNum, new Op(OP_UNSET_PRIMARY_NAV, old));
                                    opNum++;
                                    oldPrimaryNav = null;
                                }
                                final Op removeOp = new Op(OP_REMOVE, old);
                                removeOp.enterAnim = op.enterAnim;
                                removeOp.popEnterAnim = op.popEnterAnim;
                                removeOp.exitAnim = op.exitAnim;
                                removeOp.popExitAnim = op.popExitAnim;
                                mOps.add(opNum, removeOp);
                                added.remove(old);
                                opNum++;
                            }
                        }
                    }
                    if (alreadyAdded) {
                        mOps.remove(opNum);
                        opNum--;
                    } else {
                        op.cmd = OP_ADD;
                        added.add(f);
                    }
                    break;
                }
                case OP_SET_PRIMARY_NAV:
                    mOps.add(opNum, new Op(OP_UNSET_PRIMARY_NAV, oldPrimaryNav));
                    opNum++;
                    oldPrimaryNav = op.fragment;
                    break;
                default:
                    break;
            }
        }
        return oldPrimaryNav;
    }

    Fragment trackAddedFragmentsInPop(ArrayList<Fragment> added) {
        for (int opNum = 0; opNum < mOps.size(); opNum++) {
            final Op op = mOps.get(opNum);
            switch (op.cmd) {
                case OP_ADD:
                case OP_ATTACH:
                    added.remove(op.fragment);
                    break;
                case OP_REMOVE:
                case OP_DETACH:
                    added.add(op.fragment);
                    break;
                default:
                    break;
            }
        }
        return null;
    }

    public String getName() { return mName; }

    public int getTransition() { return mTransition; }

    public int getTransitionStyle() { return mTransitionStyle; }

    public boolean isEmpty() { return mOps.isEmpty(); }
}
