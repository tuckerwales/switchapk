package android.app;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.util.SuperNotCalledException;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Manages the fragments of an activity or fragment (AOSP API). */
@Deprecated
public abstract class FragmentManager {
    public static final int POP_BACK_STACK_INCLUSIVE = 1 << 0;

    @Deprecated
    public interface BackStackEntry {
        int getId();

        String getName();

        int getBreadCrumbTitleRes();

        int getBreadCrumbShortTitleRes();

        CharSequence getBreadCrumbTitle();

        CharSequence getBreadCrumbShortTitle();
    }

    @Deprecated
    public interface OnBackStackChangedListener {
        void onBackStackChanged();
    }

    public FragmentManager() {}

    public abstract FragmentTransaction beginTransaction();

    @Deprecated
    public FragmentTransaction openTransaction() { return beginTransaction(); }

    public abstract boolean executePendingTransactions();

    public abstract Fragment findFragmentById(int id);

    public abstract Fragment findFragmentByTag(String tag);

    public abstract void popBackStack();

    public abstract boolean popBackStackImmediate();

    public abstract void popBackStack(String name, int flags);

    public abstract boolean popBackStackImmediate(String name, int flags);

    public abstract void popBackStack(int id, int flags);

    public abstract boolean popBackStackImmediate(int id, int flags);

    public abstract int getBackStackEntryCount();

    public abstract BackStackEntry getBackStackEntryAt(int index);

    public abstract void addOnBackStackChangedListener(OnBackStackChangedListener listener);

    public abstract void removeOnBackStackChangedListener(OnBackStackChangedListener listener);

    public abstract void putFragment(Bundle bundle, String key, Fragment fragment);

    public abstract Fragment getFragment(Bundle bundle, String key);

    public abstract List<Fragment> getFragments();

    public abstract Fragment.SavedState saveFragmentInstanceState(Fragment f);

    public abstract boolean isDestroyed();

    public abstract void registerFragmentLifecycleCallbacks(FragmentLifecycleCallbacks cb, boolean recursive);

    public abstract void unregisterFragmentLifecycleCallbacks(FragmentLifecycleCallbacks cb);

    public abstract Fragment getPrimaryNavigationFragment();

    public abstract void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args);

    public static void enableDebugLogging(boolean enabled) { FragmentManagerImpl.DEBUG = enabled; }

    public void invalidateOptionsMenu() {}

    public abstract boolean isStateSaved();

    @Deprecated
    public abstract static class FragmentLifecycleCallbacks {
        public FragmentLifecycleCallbacks() {}

        public void onFragmentPreAttached(FragmentManager fm, Fragment f, Context context) {}

        public void onFragmentAttached(FragmentManager fm, Fragment f, Context context) {}

        public void onFragmentPreCreated(FragmentManager fm, Fragment f, Bundle savedInstanceState) {}

        public void onFragmentCreated(FragmentManager fm, Fragment f, Bundle savedInstanceState) {}

        public void onFragmentActivityCreated(FragmentManager fm, Fragment f, Bundle savedInstanceState) {}

        public void onFragmentViewCreated(FragmentManager fm, Fragment f, View v, Bundle savedInstanceState) {}

        public void onFragmentStarted(FragmentManager fm, Fragment f) {}

        public void onFragmentResumed(FragmentManager fm, Fragment f) {}

        public void onFragmentPaused(FragmentManager fm, Fragment f) {}

        public void onFragmentStopped(FragmentManager fm, Fragment f) {}

        public void onFragmentSaveInstanceState(FragmentManager fm, Fragment f, Bundle outState) {}

        public void onFragmentViewDestroyed(FragmentManager fm, Fragment f) {}

        public void onFragmentDestroyed(FragmentManager fm, Fragment f) {}

        public void onFragmentDetached(FragmentManager fm, Fragment f) {}
    }
}

/** Saved fields of one fragment (AOSP FragmentState). */
final class FragmentState implements Parcelable {
    final String mClassName;
    final int mIndex;
    final boolean mFromLayout;
    final int mFragmentId;
    final int mContainerId;
    final String mTag;
    final boolean mRetainInstance;
    final boolean mDetached;
    final Bundle mArguments;
    final boolean mHidden;
    Bundle mSavedFragmentState;
    Fragment mInstance;

    FragmentState(Fragment frag) {
        mClassName = frag.getClass().getName();
        mIndex = frag.mIndex;
        mFromLayout = frag.mFromLayout;
        mFragmentId = frag.mFragmentId;
        mContainerId = frag.mContainerId;
        mTag = frag.mTag;
        mRetainInstance = frag.mRetainInstance;
        mDetached = frag.mDetached;
        mArguments = frag.mArguments;
        mHidden = frag.mHidden;
    }

    Fragment instantiate(FragmentHostCallback host, FragmentContainer container, Fragment parent,
            FragmentManagerNonConfig childNonConfig) {
        if (mInstance == null) {
            final Context context = host.getContext();
            if (mArguments != null) mArguments.setClassLoader(context.getClassLoader());
            mInstance = container != null ? container.instantiate(context, mClassName, mArguments)
                    : Fragment.instantiate(context, mClassName, mArguments);
            if (mSavedFragmentState != null) {
                mSavedFragmentState.setClassLoader(context.getClassLoader());
                mInstance.mSavedFragmentState = mSavedFragmentState;
            }
            mInstance.setIndex(mIndex, parent);
            mInstance.mFromLayout = mFromLayout;
            mInstance.mRestored = true;
            mInstance.mFragmentId = mFragmentId;
            mInstance.mContainerId = mContainerId;
            mInstance.mTag = mTag;
            mInstance.mRetainInstance = mRetainInstance;
            mInstance.mDetached = mDetached;
            mInstance.mHidden = mHidden;
            mInstance.mFragmentManager = host.mFragmentManager;
        }
        mInstance.mChildNonConfig = childNonConfig;
        return mInstance;
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) { dest.writeValue(this); }

    public static final Parcelable.Creator<FragmentState> CREATOR = new Parcelable.Creator<FragmentState>() {
        public FragmentState createFromParcel(Parcel in) { return (FragmentState) in.readValue(null); }

        public FragmentState[] newArray(int size) { return new FragmentState[size]; }
    };
}

/** Saved state of a whole FragmentManager (AOSP FragmentManagerState). */
final class FragmentManagerState implements Parcelable {
    FragmentState[] mActive;
    int[] mAdded;
    BackStackState[] mBackStack;
    int mPrimaryNavActiveIndex = -1;
    int mNextFragmentIndex;

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) { dest.writeValue(this); }

    public static final Parcelable.Creator<FragmentManagerState> CREATOR = new Parcelable.Creator<FragmentManagerState>() {
        public FragmentManagerState createFromParcel(Parcel in) { return (FragmentManagerState) in.readValue(null); }

        public FragmentManagerState[] newArray(int size) { return new FragmentManagerState[size]; }
    };
}

/**
 * The FragmentManager implementation (port of AOSP FragmentManagerImpl
 * without animations and transitions).
 */
final class FragmentManagerImpl extends FragmentManager implements LayoutInflater.Factory2 {
    static boolean DEBUG = false;
    static final String TAG = "FragmentManager";
    static final String TARGET_REQUEST_CODE_STATE_TAG = "android:target_req_state";
    static final String TARGET_STATE_TAG = "android:target_state";
    static final String VIEW_STATE_TAG = "android:view_state";
    static final String USER_VISIBLE_HINT_TAG = "android:user_visible_hint";

    interface OpGenerator {
        boolean generateOps(ArrayList<BackStackRecord> records, ArrayList<Boolean> isRecordPop);
    }

    ArrayList<OpGenerator> mPendingActions;
    boolean mExecutingActions;
    int mNextFragmentIndex = 0;
    SparseArray<Fragment> mActive;
    final ArrayList<Fragment> mAdded = new ArrayList<Fragment>();
    ArrayList<BackStackRecord> mBackStack;
    ArrayList<Fragment> mCreatedMenus;
    ArrayList<BackStackRecord> mBackStackIndices;
    ArrayList<Integer> mAvailBackStackIndices;
    ArrayList<OnBackStackChangedListener> mBackStackChangeListeners;
    private final CopyOnWriteArrayList<Object[]> mLifecycleCallbacks = new CopyOnWriteArrayList<Object[]>();
    int mCurState = Fragment.INITIALIZING;
    FragmentHostCallback<?> mHost;
    FragmentContainer mContainer;
    Fragment mParent;
    Fragment mPrimaryNav;
    boolean mNeedMenuInvalidate;
    boolean mStateSaved;
    boolean mDestroyed;
    String mNoTransactionsBecause;
    boolean mHavePendingDeferredStart;
    ArrayList<BackStackRecord> mTmpRecords;
    ArrayList<Boolean> mTmpIsPop;
    ArrayList<Fragment> mTmpAddedFragments;
    Bundle mStateBundle = null;
    SparseArray<Parcelable> mStateArray = null;
    FragmentManagerNonConfig mSavedNonConfig;

    final Runnable mExecCommit = new Runnable() {
        public void run() { execPendingActions(); }
    };

    private void throwException(RuntimeException ex) {
        Log.e(TAG, ex.getMessage());
        throw ex;
    }

    static boolean modifiesAlpha(Object anim) { return false; }

    @Override
    public FragmentTransaction beginTransaction() { return new BackStackRecord(this); }

    @Override
    public boolean executePendingTransactions() {
        boolean updates = execPendingActions();
        forcePostponedTransactions();
        return updates;
    }

    @Override
    public void popBackStack() { enqueueAction(new PopBackStackState(null, -1, 0), false); }

    @Override
    public boolean popBackStackImmediate() {
        checkStateLoss();
        return popBackStackImmediate(null, -1, 0);
    }

    @Override
    public void popBackStack(String name, int flags) { enqueueAction(new PopBackStackState(name, -1, flags), false); }

    @Override
    public boolean popBackStackImmediate(String name, int flags) {
        checkStateLoss();
        return popBackStackImmediate(name, -1, flags);
    }

    @Override
    public void popBackStack(int id, int flags) {
        if (id < 0) throw new IllegalArgumentException("Bad id: " + id);
        enqueueAction(new PopBackStackState(null, id, flags), false);
    }

    @Override
    public boolean popBackStackImmediate(int id, int flags) {
        checkStateLoss();
        if (id < 0) throw new IllegalArgumentException("Bad id: " + id);
        return popBackStackImmediate(null, id, flags);
    }

    private boolean popBackStackImmediate(String name, int id, int flags) {
        execPendingActions();
        ensureExecReady(true);
        if (mPrimaryNav != null && id < 0 && name == null) {
            final FragmentManager childManager = mPrimaryNav.mChildFragmentManager;
            if (childManager != null && childManager.popBackStackImmediate()) return true;
        }
        boolean executePop = popBackStackState(mTmpRecords, mTmpIsPop, name, id, flags);
        if (executePop) {
            mExecutingActions = true;
            try {
                executeOpsTogether(mTmpRecords, mTmpIsPop);
            } finally {
                cleanupExec();
            }
        }
        doPendingDeferredStart();
        burpActive();
        return executePop;
    }

    @Override
    public int getBackStackEntryCount() { return mBackStack != null ? mBackStack.size() : 0; }

    @Override
    public BackStackEntry getBackStackEntryAt(int index) { return mBackStack.get(index); }

    @Override
    public void addOnBackStackChangedListener(OnBackStackChangedListener listener) {
        if (mBackStackChangeListeners == null) mBackStackChangeListeners = new ArrayList<OnBackStackChangedListener>();
        mBackStackChangeListeners.add(listener);
    }

    @Override
    public void removeOnBackStackChangedListener(OnBackStackChangedListener listener) {
        if (mBackStackChangeListeners != null) mBackStackChangeListeners.remove(listener);
    }

    @Override
    public void putFragment(Bundle bundle, String key, Fragment fragment) {
        if (fragment.mIndex < 0) {
            throwException(new IllegalStateException("Fragment " + fragment + " is not currently in the FragmentManager"));
        }
        bundle.putInt(key, fragment.mIndex);
    }

    @Override
    public Fragment getFragment(Bundle bundle, String key) {
        int index = bundle.getInt(key, -1);
        if (index == -1) return null;
        Fragment f = mActive.get(index);
        if (f == null) {
            throwException(new IllegalStateException("Fragment no longer exists for key " + key + ": index " + index));
        }
        return f;
    }

    @Override
    public List<Fragment> getFragments() {
        if (mAdded.isEmpty()) return Collections.emptyList();
        synchronized (mAdded) {
            return (List<Fragment>) mAdded.clone();
        }
    }

    @Override
    public Fragment.SavedState saveFragmentInstanceState(Fragment fragment) {
        if (fragment.mIndex < 0) {
            throwException(new IllegalStateException("Fragment " + fragment + " is not currently in the FragmentManager"));
        }
        if (fragment.mState > Fragment.INITIALIZING) {
            Bundle result = saveFragmentBasicState(fragment);
            return result != null ? new Fragment.SavedState(result) : null;
        }
        return null;
    }

    @Override
    public boolean isDestroyed() { return mDestroyed; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("FragmentManager{");
        sb.append(Integer.toHexString(System.identityHashCode(this)));
        sb.append(" in ");
        if (mParent != null) sb.append(mParent.toString());
        else sb.append(String.valueOf(mHost != null ? mHost.onGetHost() : null));
        sb.append("}}");
        return sb.toString();
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        String innerPrefix = prefix + "    ";
        if (mActive != null) {
            int N = mActive.size();
            if (N > 0) {
                writer.print(prefix);
                writer.print("Active Fragments in ");
                writer.print(Integer.toHexString(System.identityHashCode(this)));
                writer.println(":");
                for (int i = 0; i < N; i++) {
                    Fragment f = mActive.valueAt(i);
                    writer.print(prefix);
                    writer.print("  #");
                    writer.print(i);
                    writer.print(": ");
                    writer.println(f);
                    if (f != null) f.dump(innerPrefix, fd, writer, args);
                }
            }
        }
        writer.print(prefix);
        writer.print("FragmentManager misc state: mCurState=");
        writer.print(mCurState);
        writer.print(" mStateSaved=");
        writer.print(mStateSaved);
        writer.print(" mDestroyed=");
        writer.println(mDestroyed);
    }

    void moveToState(Fragment f, int newState, int transit, int transitionStyle, boolean keepActive) {
        if ((!f.mAdded || f.mDetached) && newState > Fragment.CREATED) newState = Fragment.CREATED;
        if (f.mRemoving && newState > f.mState) {
            if (f.mState == Fragment.INITIALIZING && f.isInBackStack()) newState = Fragment.CREATED;
            else newState = f.mState;
        }
        if (f.mDeferStart && f.mState < Fragment.STARTED && newState > Fragment.STOPPED) newState = Fragment.STOPPED;
        if (f.mState <= newState) {
            if (f.mFromLayout && !f.mInLayout) return;
            switch (f.mState) {
                case Fragment.INITIALIZING:
                    if (newState > Fragment.INITIALIZING) {
                        if (f.mSavedFragmentState != null) {
                            f.mSavedViewState = f.mSavedFragmentState.getSparseParcelableArray(VIEW_STATE_TAG);
                            f.mTarget = getFragment(f.mSavedFragmentState, TARGET_STATE_TAG);
                            if (f.mTarget != null) {
                                f.mTargetRequestCode = f.mSavedFragmentState.getInt(TARGET_REQUEST_CODE_STATE_TAG, 0);
                            }
                            f.mUserVisibleHint = f.mSavedFragmentState.getBoolean(USER_VISIBLE_HINT_TAG, true);
                            if (!f.mUserVisibleHint) {
                                f.mDeferStart = true;
                                if (newState > Fragment.STOPPED) newState = Fragment.STOPPED;
                            }
                        }
                        f.mHost = mHost;
                        f.mParentFragment = mParent;
                        f.mFragmentManager = mParent != null ? mParent.mChildFragmentManager : mHost.getFragmentManagerImpl();
                        if (f.mTarget != null) {
                            if (mActive.get(f.mTarget.mIndex) != f.mTarget) {
                                throw new IllegalStateException("Fragment " + f + " declared target fragment " + f.mTarget
                                        + " that does not belong to this FragmentManager!");
                            }
                            if (f.mTarget.mState < Fragment.CREATED) moveToState(f.mTarget, Fragment.CREATED, 0, 0, true);
                        }
                        dispatchOnFragmentPreAttached(f, mHost.getContext(), false);
                        f.mCalled = false;
                        f.onAttach(mHost.getContext());
                        if (!f.mCalled) {
                            throw new SuperNotCalledException("Fragment " + f + " did not call through to super.onAttach()");
                        }
                        if (f.mParentFragment == null) mHost.onAttachFragment(f);
                        else f.mParentFragment.onAttachFragment(f);
                        dispatchOnFragmentAttached(f, mHost.getContext(), false);
                        if (!f.mIsCreated) {
                            dispatchOnFragmentPreCreated(f, f.mSavedFragmentState, false);
                            f.performCreate(f.mSavedFragmentState);
                            dispatchOnFragmentCreated(f, f.mSavedFragmentState, false);
                        } else {
                            f.restoreChildFragmentState(f.mSavedFragmentState, true);
                            f.mState = Fragment.CREATED;
                        }
                        f.mRetaining = false;
                    }
                    // fall through
                case Fragment.CREATED:
                    ensureInflatedFragmentView(f);
                    if (newState > Fragment.CREATED) {
                        if (!f.mFromLayout) {
                            ViewGroup container = null;
                            if (f.mContainerId != 0) {
                                if (f.mContainerId == View.NO_ID) {
                                    throwException(new IllegalArgumentException("Cannot create fragment " + f
                                            + " for a container view with no id"));
                                }
                                container = mContainer.onFindViewById(f.mContainerId);
                                if (container == null && !f.mRestored) {
                                    String resName;
                                    try {
                                        resName = f.getResources().getResourceName(f.mContainerId);
                                    } catch (Exception e) {
                                        resName = "unknown";
                                    }
                                    throwException(new IllegalArgumentException("No view found for id 0x"
                                            + Integer.toHexString(f.mContainerId) + " (" + resName + ") for fragment " + f));
                                }
                            }
                            f.mContainer = container;
                            f.mView = f.performCreateView(f.performGetLayoutInflater(f.mSavedFragmentState), container,
                                    f.mSavedFragmentState);
                            if (f.mView != null) {
                                f.mView.setSaveFromParentEnabled(false);
                                if (container != null) container.addView(f.mView);
                                if (f.mHidden) f.mView.setVisibility(View.GONE);
                                f.onViewCreated(f.mView, f.mSavedFragmentState);
                                dispatchOnFragmentViewCreated(f, f.mView, f.mSavedFragmentState, false);
                                f.mIsNewlyAdded = (f.mView.getVisibility() == View.VISIBLE) && f.mContainer != null;
                            }
                        }
                        f.performActivityCreated(f.mSavedFragmentState);
                        dispatchOnFragmentActivityCreated(f, f.mSavedFragmentState, false);
                        if (f.mView != null) f.restoreViewState(f.mSavedFragmentState);
                        f.mSavedFragmentState = null;
                    }
                    // fall through
                case Fragment.ACTIVITY_CREATED:
                    if (newState > Fragment.ACTIVITY_CREATED) f.mState = Fragment.STOPPED;
                    // fall through
                case Fragment.STOPPED:
                    if (newState > Fragment.STOPPED) {
                        f.performStart();
                        dispatchOnFragmentStarted(f, false);
                    }
                    // fall through
                case Fragment.STARTED:
                    if (newState > Fragment.STARTED) {
                        f.performResume();
                        dispatchOnFragmentResumed(f, false);
                        f.mSavedFragmentState = null;
                        f.mSavedViewState = null;
                    }
                    break;
                default:
                    break;
            }
        } else if (f.mState > newState) {
            switch (f.mState) {
                case Fragment.RESUMED:
                    if (newState < Fragment.RESUMED) {
                        f.performPause();
                        dispatchOnFragmentPaused(f, false);
                    }
                    // fall through
                case Fragment.STARTED:
                    if (newState < Fragment.STARTED) {
                        f.performStop();
                        dispatchOnFragmentStopped(f, false);
                    }
                    // fall through
                case Fragment.STOPPED:
                case Fragment.ACTIVITY_CREATED:
                    if (newState < Fragment.ACTIVITY_CREATED) {
                        if (f.mView != null) {
                            if (mHost.onShouldSaveFragmentState(f) && f.mSavedViewState == null) saveFragmentViewState(f);
                        }
                        f.performDestroyView();
                        dispatchOnFragmentViewDestroyed(f, false);
                        if (f.mView != null && f.mContainer != null) f.mContainer.removeView(f.mView);
                        f.mContainer = null;
                        f.mView = null;
                        f.mInLayout = false;
                    }
                    // fall through
                case Fragment.CREATED:
                    if (newState < Fragment.CREATED) {
                        if (!f.mRetaining) {
                            f.performDestroy();
                            dispatchOnFragmentDestroyed(f, false);
                        } else {
                            f.mState = Fragment.INITIALIZING;
                        }
                        f.performDetach();
                        dispatchOnFragmentDetached(f, false);
                        if (!keepActive) {
                            if (!f.mRetaining) {
                                makeInactive(f);
                            } else {
                                f.mHost = null;
                                f.mParentFragment = null;
                                f.mFragmentManager = null;
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        if (f.mState != newState) {
            Log.w(TAG, "moveToState: Fragment state for " + f + " not updated inline; expected state " + newState
                    + " found " + f.mState);
            f.mState = newState;
        }
    }

    void moveToState(Fragment f) { moveToState(f, mCurState, 0, 0, false); }

    void ensureInflatedFragmentView(Fragment f) {
        if (f.mFromLayout && !f.mPerformedCreateView) {
            f.mView = f.performCreateView(f.performGetLayoutInflater(f.mSavedFragmentState), null, f.mSavedFragmentState);
            if (f.mView != null) {
                f.mView.setSaveFromParentEnabled(false);
                if (f.mHidden) f.mView.setVisibility(View.GONE);
                f.onViewCreated(f.mView, f.mSavedFragmentState);
                dispatchOnFragmentViewCreated(f, f.mView, f.mSavedFragmentState, false);
            }
        }
    }

    void completeShowHideFragment(final Fragment fragment) {
        if (fragment.mView != null) {
            fragment.mView.setVisibility(fragment.mHidden && !fragment.isHideReplaced() ? View.GONE : View.VISIBLE);
            fragment.setHideReplaced(false);
        }
        if (fragment.mAdded && fragment.mHasMenu && fragment.mMenuVisible) mNeedMenuInvalidate = true;
        fragment.mHiddenChanged = false;
        fragment.onHiddenChanged(fragment.mHidden);
    }

    void moveFragmentToExpectedState(final Fragment f) {
        if (f == null) return;
        int nextState = mCurState;
        if (f.mRemoving) {
            if (f.isInBackStack()) nextState = Math.min(nextState, Fragment.CREATED);
            else nextState = Math.min(nextState, Fragment.INITIALIZING);
        }
        moveToState(f, nextState, 0, 0, false);
        if (f.mView != null && f.mContainer != null) {
            // Keep views in the order the fragments were added.
            Fragment underFragment = findFragmentUnder(f);
            if (underFragment != null) {
                final View underView = underFragment.mView;
                final ViewGroup container = f.mContainer;
                int underIndex = container.indexOfChild(underView);
                int viewIndex = container.indexOfChild(f.mView);
                if (viewIndex < underIndex) {
                    container.removeViewAt(viewIndex);
                    container.addView(f.mView, underIndex);
                }
            }
        }
        if (f.mHiddenChanged) completeShowHideFragment(f);
    }

    void moveToState(int newState, boolean always) {
        if (mHost == null && newState != Fragment.INITIALIZING) throw new IllegalStateException("No activity");
        if (!always && mCurState == newState) return;
        mCurState = newState;
        if (mActive != null) {
            final int numAdded = mAdded.size();
            for (int i = 0; i < numAdded; i++) moveFragmentToExpectedState(mAdded.get(i));
            final int numActive = mActive.size();
            for (int i = 0; i < numActive; i++) {
                Fragment f = mActive.valueAt(i);
                if (f != null && (f.mRemoving || f.mDetached) && !f.mIsNewlyAdded) moveFragmentToExpectedState(f);
            }
            startPendingDeferredFragments();
            if (mNeedMenuInvalidate && mHost != null && mCurState == Fragment.RESUMED) {
                mHost.onInvalidateOptionsMenu();
                mNeedMenuInvalidate = false;
            }
        }
    }

    void startPendingDeferredFragments() {
        if (mActive == null) return;
        for (int i = 0; i < mActive.size(); i++) {
            Fragment f = mActive.valueAt(i);
            if (f != null) performPendingDeferredStart(f);
        }
    }

    public void performPendingDeferredStart(Fragment f) {
        if (f.mDeferStart) {
            if (mExecutingActions) {
                mHavePendingDeferredStart = true;
                return;
            }
            f.mDeferStart = false;
            moveToState(f, mCurState, 0, 0, false);
        }
    }

    void makeActive(Fragment f) {
        if (f.mIndex >= 0) return;
        f.setIndex(mNextFragmentIndex++, mParent);
        if (mActive == null) mActive = new SparseArray<Fragment>();
        mActive.put(f.mIndex, f);
    }

    void makeInactive(Fragment f) {
        if (f.mIndex < 0) return;
        mActive.put(f.mIndex, null);
        mHost.inactivateFragment(f.mWho);
        f.initState();
    }

    public void addFragment(Fragment fragment, boolean moveToStateNow) {
        makeActive(fragment);
        if (!fragment.mDetached) {
            if (mAdded.contains(fragment)) throw new IllegalStateException("Fragment already added: " + fragment);
            synchronized (mAdded) {
                mAdded.add(fragment);
            }
            fragment.mAdded = true;
            fragment.mRemoving = false;
            if (fragment.mView == null) fragment.mHiddenChanged = false;
            if (fragment.mHasMenu && fragment.mMenuVisible) mNeedMenuInvalidate = true;
            if (moveToStateNow) moveToState(fragment);
        }
    }

    public void removeFragment(Fragment fragment) {
        final boolean inactive = !fragment.isInBackStack();
        if (!fragment.mDetached || inactive) {
            synchronized (mAdded) {
                mAdded.remove(fragment);
            }
            if (fragment.mHasMenu && fragment.mMenuVisible) mNeedMenuInvalidate = true;
            fragment.mAdded = false;
            fragment.mRemoving = true;
        }
    }

    public void hideFragment(Fragment fragment) {
        if (!fragment.mHidden) {
            fragment.mHidden = true;
            fragment.mHiddenChanged = !fragment.mHiddenChanged;
        }
    }

    public void showFragment(Fragment fragment) {
        if (fragment.mHidden) {
            fragment.mHidden = false;
            fragment.mHiddenChanged = !fragment.mHiddenChanged;
        }
    }

    public void detachFragment(Fragment fragment) {
        if (!fragment.mDetached) {
            fragment.mDetached = true;
            if (fragment.mAdded) {
                synchronized (mAdded) {
                    mAdded.remove(fragment);
                }
                if (fragment.mHasMenu && fragment.mMenuVisible) mNeedMenuInvalidate = true;
                fragment.mAdded = false;
            }
        }
    }

    public void attachFragment(Fragment fragment) {
        if (fragment.mDetached) {
            fragment.mDetached = false;
            if (!fragment.mAdded) {
                if (mAdded.contains(fragment)) throw new IllegalStateException("Fragment already added: " + fragment);
                synchronized (mAdded) {
                    mAdded.add(fragment);
                }
                fragment.mAdded = true;
                if (fragment.mHasMenu && fragment.mMenuVisible) mNeedMenuInvalidate = true;
            }
        }
    }

    @Override
    public Fragment findFragmentById(int id) {
        for (int i = mAdded.size() - 1; i >= 0; i--) {
            Fragment f = mAdded.get(i);
            if (f != null && f.mFragmentId == id) return f;
        }
        if (mActive != null) {
            for (int i = mActive.size() - 1; i >= 0; i--) {
                Fragment f = mActive.valueAt(i);
                if (f != null && f.mFragmentId == id) return f;
            }
        }
        return null;
    }

    @Override
    public Fragment findFragmentByTag(String tag) {
        if (tag != null) {
            for (int i = mAdded.size() - 1; i >= 0; i--) {
                Fragment f = mAdded.get(i);
                if (f != null && tag.equals(f.mTag)) return f;
            }
        }
        if (mActive != null && tag != null) {
            for (int i = mActive.size() - 1; i >= 0; i--) {
                Fragment f = mActive.valueAt(i);
                if (f != null && tag.equals(f.mTag)) return f;
            }
        }
        return null;
    }

    public Fragment findFragmentByWho(String who) {
        if (mActive != null && who != null) {
            for (int i = mActive.size() - 1; i >= 0; i--) {
                Fragment f = mActive.valueAt(i);
                if (f != null && (f = f.findFragmentByWho(who)) != null) return f;
            }
        }
        return null;
    }

    private void checkStateLoss() {
        if (mStateSaved) throw new IllegalStateException("Can not perform this action after onSaveInstanceState");
        if (mNoTransactionsBecause != null) {
            throw new IllegalStateException("Can not perform this action inside of " + mNoTransactionsBecause);
        }
    }

    @Override
    public boolean isStateSaved() { return mStateSaved; }

    public void enqueueAction(OpGenerator action, boolean allowStateLoss) {
        if (!allowStateLoss) checkStateLoss();
        synchronized (this) {
            if (mDestroyed || mHost == null) {
                if (allowStateLoss) return;
                throw new IllegalStateException("Activity has been destroyed");
            }
            if (mPendingActions == null) mPendingActions = new ArrayList<OpGenerator>();
            mPendingActions.add(action);
            scheduleCommit();
        }
    }

    private void scheduleCommit() {
        synchronized (this) {
            if (mPendingActions != null && mPendingActions.size() == 1) {
                mHost.getHandler().removeCallbacks(mExecCommit);
                mHost.getHandler().post(mExecCommit);
            }
        }
    }

    public int allocBackStackIndex(BackStackRecord bse) {
        synchronized (this) {
            if (mAvailBackStackIndices == null || mAvailBackStackIndices.size() <= 0) {
                if (mBackStackIndices == null) mBackStackIndices = new ArrayList<BackStackRecord>();
                int index = mBackStackIndices.size();
                mBackStackIndices.add(bse);
                return index;
            } else {
                int index = mAvailBackStackIndices.remove(mAvailBackStackIndices.size() - 1);
                mBackStackIndices.set(index, bse);
                return index;
            }
        }
    }

    public void setBackStackIndex(int index, BackStackRecord bse) {
        synchronized (this) {
            if (mBackStackIndices == null) mBackStackIndices = new ArrayList<BackStackRecord>();
            int N = mBackStackIndices.size();
            if (index < N) {
                mBackStackIndices.set(index, bse);
            } else {
                while (N < index) {
                    mBackStackIndices.add(null);
                    if (mAvailBackStackIndices == null) mAvailBackStackIndices = new ArrayList<Integer>();
                    mAvailBackStackIndices.add(N);
                    N++;
                }
                mBackStackIndices.add(bse);
            }
        }
    }

    public void freeBackStackIndex(int index) {
        synchronized (this) {
            mBackStackIndices.set(index, null);
            if (mAvailBackStackIndices == null) mAvailBackStackIndices = new ArrayList<Integer>();
            mAvailBackStackIndices.add(index);
        }
    }

    private void ensureExecReady(boolean allowStateLoss) {
        if (mExecutingActions) throw new IllegalStateException("FragmentManager is already executing transactions");
        if (Thread.currentThread() != mHost.getHandler().getLooper().getThread()) {
            throw new IllegalStateException("Must be called from main thread of fragment host");
        }
        if (!allowStateLoss) checkStateLoss();
        if (mTmpRecords == null) {
            mTmpRecords = new ArrayList<BackStackRecord>();
            mTmpIsPop = new ArrayList<Boolean>();
        }
        mExecutingActions = true;
        try {
            executePostponedTransaction(null, null);
        } finally {
            mExecutingActions = false;
        }
    }

    public void execSingleAction(OpGenerator action, boolean allowStateLoss) {
        if (allowStateLoss && (mHost == null || mDestroyed)) return;
        ensureExecReady(allowStateLoss);
        if (action.generateOps(mTmpRecords, mTmpIsPop)) {
            mExecutingActions = true;
            try {
                executeOpsTogether(mTmpRecords, mTmpIsPop);
            } finally {
                cleanupExec();
            }
        }
        doPendingDeferredStart();
        burpActive();
    }

    private void cleanupExec() {
        mExecutingActions = false;
        mTmpIsPop.clear();
        mTmpRecords.clear();
    }

    public boolean execPendingActions() {
        ensureExecReady(true);
        boolean didSomething = false;
        while (generateOpsForPendingActions(mTmpRecords, mTmpIsPop)) {
            mExecutingActions = true;
            try {
                executeOpsTogether(mTmpRecords, mTmpIsPop);
            } finally {
                cleanupExec();
            }
            didSomething = true;
        }
        doPendingDeferredStart();
        burpActive();
        return didSomething;
    }

    private void executePostponedTransaction(ArrayList<BackStackRecord> records, ArrayList<Boolean> isRecordPop) {}

    private void forcePostponedTransactions() {}

    /** Runs records in order: expand replaces, execute or pop, then move fragments to their states. */
    private void executeOpsTogether(ArrayList<BackStackRecord> records, ArrayList<Boolean> isRecordPop) {
        if (records == null || records.isEmpty()) return;
        final int endIndex = records.size();
        boolean addToBackStack = false;
        if (mTmpAddedFragments == null) mTmpAddedFragments = new ArrayList<Fragment>();
        else mTmpAddedFragments.clear();
        mTmpAddedFragments.addAll(mAdded);
        for (int recordNum = 0; recordNum < endIndex; recordNum++) {
            final BackStackRecord record = records.get(recordNum);
            final boolean isPop = isRecordPop.get(recordNum);
            if (!isPop) record.expandReplaceOps(mTmpAddedFragments);
            else record.trackAddedFragmentsInPop(mTmpAddedFragments);
            addToBackStack = addToBackStack || record.mAddToBackStack;
        }
        mTmpAddedFragments.clear();
        for (int i = 0; i < endIndex; i++) {
            final BackStackRecord record = records.get(i);
            final boolean isPop = isRecordPop.get(i);
            if (isPop) {
                record.bumpBackStackNesting(-1);
                record.executePopOps(i == endIndex - 1);
            } else {
                record.bumpBackStackNesting(1);
                record.executeOps();
            }
        }
        moveToState(mCurState, true);
        for (int recordNum = 0; recordNum < endIndex; recordNum++) {
            final BackStackRecord record = records.get(recordNum);
            final boolean isPop = isRecordPop.get(recordNum);
            if (isPop && record.mIndex >= 0) {
                freeBackStackIndex(record.mIndex);
                record.mIndex = -1;
            }
            record.runOnCommitRunnables();
        }
        if (addToBackStack) reportBackStackChanged();
    }

    Fragment findFragmentUnder(Fragment f) {
        final ViewGroup container = f.mContainer;
        final View view = f.mView;
        if (container == null || view == null) return null;
        final int fragmentIndex = mAdded.indexOf(f);
        for (int i = fragmentIndex - 1; i >= 0; i--) {
            Fragment underFragment = mAdded.get(i);
            if (underFragment.mContainer == container && underFragment.mView != null) return underFragment;
        }
        return null;
    }

    private boolean generateOpsForPendingActions(ArrayList<BackStackRecord> records, ArrayList<Boolean> isPop) {
        boolean didSomething = false;
        synchronized (this) {
            if (mPendingActions == null || mPendingActions.size() == 0) return false;
            final int numActions = mPendingActions.size();
            for (int i = 0; i < numActions; i++) didSomething |= mPendingActions.get(i).generateOps(records, isPop);
            mPendingActions.clear();
            mHost.getHandler().removeCallbacks(mExecCommit);
        }
        return didSomething;
    }

    void doPendingDeferredStart() {
        if (mHavePendingDeferredStart) {
            boolean loadersRunning = false;
            for (int i = 0; i < mActive.size(); i++) {
                Fragment f = mActive.valueAt(i);
                if (f != null && f.mLoaderManager != null) loadersRunning |= f.mLoaderManager.hasRunningLoaders();
            }
            if (!loadersRunning) {
                mHavePendingDeferredStart = false;
                startPendingDeferredFragments();
            }
        }
    }

    void reportBackStackChanged() {
        if (mBackStackChangeListeners != null) {
            for (int i = 0; i < mBackStackChangeListeners.size(); i++) mBackStackChangeListeners.get(i).onBackStackChanged();
        }
    }

    void addBackStackState(BackStackRecord state) {
        if (mBackStack == null) mBackStack = new ArrayList<BackStackRecord>();
        mBackStack.add(state);
    }

    boolean popBackStackState(ArrayList<BackStackRecord> records, ArrayList<Boolean> isRecordPop, String name, int id,
            int flags) {
        if (mBackStack == null) return false;
        if (name == null && id < 0 && (flags & POP_BACK_STACK_INCLUSIVE) == 0) {
            int last = mBackStack.size() - 1;
            if (last < 0) return false;
            records.add(mBackStack.remove(last));
            isRecordPop.add(true);
        } else {
            int index = -1;
            if (name != null || id >= 0) {
                index = mBackStack.size() - 1;
                while (index >= 0) {
                    BackStackRecord bss = mBackStack.get(index);
                    if (name != null && name.equals(bss.getName())) break;
                    if (id >= 0 && id == bss.mIndex) break;
                    index--;
                }
                if (index < 0) return false;
                if ((flags & POP_BACK_STACK_INCLUSIVE) != 0) {
                    index--;
                    while (index >= 0) {
                        BackStackRecord bss = mBackStack.get(index);
                        if ((name != null && name.equals(bss.getName())) || (id >= 0 && id == bss.mIndex)) {
                            index--;
                            continue;
                        }
                        break;
                    }
                }
            }
            if (index == mBackStack.size() - 1) return false;
            for (int i = mBackStack.size() - 1; i > index; i--) {
                records.add(mBackStack.remove(i));
                isRecordPop.add(true);
            }
        }
        return true;
    }

    FragmentManagerNonConfig retainNonConfig() {
        setRetaining(mSavedNonConfig);
        return mSavedNonConfig;
    }

    private static void setRetaining(FragmentManagerNonConfig nonConfig) {
        if (nonConfig == null) return;
        List<Fragment> fragments = nonConfig.getFragments();
        if (fragments != null) {
            for (Fragment fragment : fragments) fragment.mRetaining = true;
        }
        List<FragmentManagerNonConfig> children = nonConfig.getChildNonConfigs();
        if (children != null) {
            for (FragmentManagerNonConfig child : children) setRetaining(child);
        }
    }

    void saveNonConfig() {
        ArrayList<Fragment> fragments = null;
        ArrayList<FragmentManagerNonConfig> childFragments = null;
        if (mActive != null) {
            for (int i = 0; i < mActive.size(); i++) {
                Fragment f = mActive.valueAt(i);
                if (f != null) {
                    if (f.mRetainInstance) {
                        if (fragments == null) fragments = new ArrayList<Fragment>();
                        fragments.add(f);
                        f.mTargetIndex = f.mTarget != null ? f.mTarget.mIndex : -1;
                    }
                    FragmentManagerNonConfig child;
                    if (f.mChildFragmentManager != null) {
                        f.mChildFragmentManager.saveNonConfig();
                        child = f.mChildFragmentManager.mSavedNonConfig;
                    } else {
                        child = f.mChildNonConfig;
                    }
                    if (childFragments == null && child != null) {
                        childFragments = new ArrayList<FragmentManagerNonConfig>(mActive.size());
                        for (int j = 0; j < i; j++) childFragments.add(null);
                    }
                    if (childFragments != null) childFragments.add(child);
                }
            }
        }
        if (fragments == null && childFragments == null) mSavedNonConfig = null;
        else mSavedNonConfig = new FragmentManagerNonConfig(fragments, childFragments);
    }

    void saveFragmentViewState(Fragment f) {
        if (f.mView == null) return;
        if (mStateArray == null) mStateArray = new SparseArray<Parcelable>();
        else mStateArray.clear();
        f.mView.saveHierarchyState(mStateArray);
        if (mStateArray.size() > 0) {
            f.mSavedViewState = mStateArray;
            mStateArray = null;
        }
    }

    Bundle saveFragmentBasicState(Fragment f) {
        Bundle result = null;
        if (mStateBundle == null) mStateBundle = new Bundle();
        f.performSaveInstanceState(mStateBundle);
        dispatchOnFragmentSaveInstanceState(f, mStateBundle, false);
        if (!mStateBundle.isEmpty()) {
            result = mStateBundle;
            mStateBundle = null;
        }
        if (f.mView != null) saveFragmentViewState(f);
        if (f.mSavedViewState != null) {
            if (result == null) result = new Bundle();
            result.putSparseParcelableArray(VIEW_STATE_TAG, f.mSavedViewState);
        }
        if (!f.mUserVisibleHint) {
            if (result == null) result = new Bundle();
            result.putBoolean(USER_VISIBLE_HINT_TAG, f.mUserVisibleHint);
        }
        return result;
    }

    Parcelable saveAllState() {
        forcePostponedTransactions();
        execPendingActions();
        mStateSaved = true;
        mSavedNonConfig = null;
        if (mActive == null || mActive.size() <= 0) return null;
        int N = mActive.size();
        FragmentState[] active = new FragmentState[N];
        boolean haveFragments = false;
        for (int i = 0; i < N; i++) {
            Fragment f = mActive.valueAt(i);
            if (f != null) {
                if (f.mIndex < 0) {
                    throwException(new IllegalStateException("Failure saving state: active " + f
                            + " has cleared index: " + f.mIndex));
                }
                haveFragments = true;
                FragmentState fs = new FragmentState(f);
                active[i] = fs;
                if (f.mState > Fragment.INITIALIZING && fs.mSavedFragmentState == null) {
                    fs.mSavedFragmentState = saveFragmentBasicState(f);
                    if (f.mTarget != null) {
                        if (f.mTarget.mIndex < 0) {
                            throwException(new IllegalStateException("Failure saving state: " + f
                                    + " has target not in fragment manager: " + f.mTarget));
                        }
                        if (fs.mSavedFragmentState == null) fs.mSavedFragmentState = new Bundle();
                        putFragment(fs.mSavedFragmentState, TARGET_STATE_TAG, f.mTarget);
                        if (f.mTargetRequestCode != 0) {
                            fs.mSavedFragmentState.putInt(TARGET_REQUEST_CODE_STATE_TAG, f.mTargetRequestCode);
                        }
                    }
                } else {
                    fs.mSavedFragmentState = f.mSavedFragmentState;
                }
            }
        }
        if (!haveFragments) return null;
        int[] added = null;
        BackStackState[] backStack = null;
        N = mAdded.size();
        if (N > 0) {
            added = new int[N];
            for (int i = 0; i < N; i++) {
                added[i] = mAdded.get(i).mIndex;
                if (added[i] < 0) {
                    throwException(new IllegalStateException("Failure saving state: active " + mAdded.get(i)
                            + " has cleared index: " + added[i]));
                }
            }
        }
        if (mBackStack != null) {
            N = mBackStack.size();
            if (N > 0) {
                backStack = new BackStackState[N];
                for (int i = 0; i < N; i++) backStack[i] = new BackStackState(this, mBackStack.get(i));
            }
        }
        FragmentManagerState fms = new FragmentManagerState();
        fms.mActive = active;
        fms.mAdded = added;
        fms.mBackStack = backStack;
        fms.mNextFragmentIndex = mNextFragmentIndex;
        if (mPrimaryNav != null) fms.mPrimaryNavActiveIndex = mPrimaryNav.mIndex;
        saveNonConfig();
        return fms;
    }

    void restoreAllState(Parcelable state, FragmentManagerNonConfig nonConfig) {
        if (state == null) return;
        FragmentManagerState fms = (FragmentManagerState) state;
        if (fms.mActive == null) return;
        List<FragmentManagerNonConfig> childNonConfigs = null;
        if (nonConfig != null) {
            List<Fragment> nonConfigFragments = nonConfig.getFragments();
            childNonConfigs = nonConfig.getChildNonConfigs();
            final int count = nonConfigFragments != null ? nonConfigFragments.size() : 0;
            for (int i = 0; i < count; i++) {
                Fragment f = nonConfigFragments.get(i);
                int index = 0;
                while (index < fms.mActive.length && fms.mActive[index].mIndex != f.mIndex) index++;
                if (index == fms.mActive.length) {
                    throwException(new IllegalStateException("Could not find active fragment with index " + f.mIndex));
                }
                FragmentState fs = fms.mActive[index];
                fs.mInstance = f;
                f.mSavedViewState = null;
                f.mBackStackNesting = 0;
                f.mInLayout = false;
                f.mAdded = false;
                f.mTarget = null;
                if (fs.mSavedFragmentState != null) {
                    fs.mSavedFragmentState.setClassLoader(mHost.getContext().getClassLoader());
                    f.mSavedViewState = fs.mSavedFragmentState.getSparseParcelableArray(VIEW_STATE_TAG);
                    f.mSavedFragmentState = fs.mSavedFragmentState;
                }
            }
        }
        mActive = new SparseArray<Fragment>(fms.mActive.length);
        for (int i = 0; i < fms.mActive.length; i++) {
            FragmentState fs = fms.mActive[i];
            if (fs != null) {
                FragmentManagerNonConfig childNonConfig = null;
                if (childNonConfigs != null && i < childNonConfigs.size()) childNonConfig = childNonConfigs.get(i);
                Fragment f = fs.instantiate(mHost, mContainer, mParent, childNonConfig);
                mActive.put(f.mIndex, f);
                fs.mInstance = null;
            }
        }
        if (nonConfig != null) {
            List<Fragment> nonConfigFragments = nonConfig.getFragments();
            final int count = nonConfigFragments != null ? nonConfigFragments.size() : 0;
            for (int i = 0; i < count; i++) {
                Fragment f = nonConfigFragments.get(i);
                if (f.mTargetIndex >= 0) f.mTarget = mActive.get(f.mTargetIndex);
            }
        }
        mAdded.clear();
        if (fms.mAdded != null) {
            for (int i = 0; i < fms.mAdded.length; i++) {
                Fragment f = mActive.get(fms.mAdded[i]);
                if (f == null) {
                    throwException(new IllegalStateException("No instantiated fragment for index #" + fms.mAdded[i]));
                }
                f.mAdded = true;
                if (mAdded.contains(f)) throw new IllegalStateException("Already added!");
                synchronized (mAdded) {
                    mAdded.add(f);
                }
            }
        }
        if (fms.mBackStack != null) {
            mBackStack = new ArrayList<BackStackRecord>(fms.mBackStack.length);
            for (int i = 0; i < fms.mBackStack.length; i++) {
                BackStackRecord bse = fms.mBackStack[i].instantiate(this);
                mBackStack.add(bse);
                if (bse.mIndex >= 0) setBackStackIndex(bse.mIndex, bse);
            }
        } else {
            mBackStack = null;
        }
        if (fms.mPrimaryNavActiveIndex >= 0) mPrimaryNav = mActive.get(fms.mPrimaryNavActiveIndex);
        mNextFragmentIndex = fms.mNextFragmentIndex;
    }

    private void burpActive() {
        if (mActive != null) {
            for (int i = mActive.size() - 1; i >= 0; i--) {
                if (mActive.valueAt(i) == null) mActive.delete(mActive.keyAt(i));
            }
        }
    }

    public void attachController(FragmentHostCallback<?> host, FragmentContainer container, Fragment parent) {
        if (mHost != null) throw new IllegalStateException("Already attached");
        mHost = host;
        mContainer = container;
        mParent = parent;
    }

    public void noteStateNotSaved() {
        mSavedNonConfig = null;
        mStateSaved = false;
        final int addedCount = mAdded.size();
        for (int i = 0; i < addedCount; i++) {
            Fragment fragment = mAdded.get(i);
            if (fragment != null) fragment.noteStateNotSaved();
        }
    }

    public void dispatchCreate() {
        mStateSaved = false;
        dispatchMoveToState(Fragment.CREATED);
    }

    public void dispatchActivityCreated() {
        mStateSaved = false;
        dispatchMoveToState(Fragment.ACTIVITY_CREATED);
    }

    public void dispatchStart() {
        mStateSaved = false;
        dispatchMoveToState(Fragment.STARTED);
    }

    public void dispatchResume() {
        mStateSaved = false;
        dispatchMoveToState(Fragment.RESUMED);
    }

    public void dispatchPause() { dispatchMoveToState(Fragment.STARTED); }

    public void dispatchStop() { dispatchMoveToState(Fragment.STOPPED); }

    public void dispatchDestroyView() { dispatchMoveToState(Fragment.CREATED); }

    public void dispatchDestroy() {
        mDestroyed = true;
        execPendingActions();
        dispatchMoveToState(Fragment.INITIALIZING);
        mHost = null;
        mContainer = null;
        mParent = null;
    }

    private void dispatchMoveToState(int state) {
        try {
            mExecutingActions = true;
            moveToState(state, false);
        } finally {
            mExecutingActions = false;
        }
        execPendingActions();
    }

    boolean isStateAtLeast(int state) { return mCurState >= state; }

    public void dispatchMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        for (int i = mAdded.size() - 1; i >= 0; --i) {
            final Fragment f = mAdded.get(i);
            if (f != null) f.performMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        }
    }

    public void dispatchPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        for (int i = mAdded.size() - 1; i >= 0; --i) {
            final Fragment f = mAdded.get(i);
            if (f != null) f.performPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        }
    }

    public void dispatchConfigurationChanged(Configuration newConfig) {
        for (int i = 0; i < mAdded.size(); i++) {
            Fragment f = mAdded.get(i);
            if (f != null) f.performConfigurationChanged(newConfig);
        }
    }

    public void dispatchLowMemory() {
        for (int i = 0; i < mAdded.size(); i++) {
            Fragment f = mAdded.get(i);
            if (f != null) f.performLowMemory();
        }
    }

    public void dispatchTrimMemory(int level) {
        for (int i = 0; i < mAdded.size(); i++) {
            Fragment f = mAdded.get(i);
            if (f != null) f.performTrimMemory(level);
        }
    }

    public boolean dispatchCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (mCurState < Fragment.CREATED) return false;
        boolean show = false;
        ArrayList<Fragment> newMenus = null;
        for (int i = 0; i < mAdded.size(); i++) {
            Fragment f = mAdded.get(i);
            if (f != null) {
                if (f.performCreateOptionsMenu(menu, inflater)) {
                    show = true;
                    if (newMenus == null) newMenus = new ArrayList<Fragment>();
                    newMenus.add(f);
                }
            }
        }
        if (mCreatedMenus != null) {
            for (int i = 0; i < mCreatedMenus.size(); i++) {
                Fragment f = mCreatedMenus.get(i);
                if (newMenus == null || !newMenus.contains(f)) f.onDestroyOptionsMenu();
            }
        }
        mCreatedMenus = newMenus;
        return show;
    }

    public boolean dispatchPrepareOptionsMenu(Menu menu) {
        if (mCurState < Fragment.CREATED) return false;
        boolean show = false;
        for (int i = 0; i < mAdded.size(); i++) {
            Fragment f = mAdded.get(i);
            if (f != null && f.performPrepareOptionsMenu(menu)) show = true;
        }
        return show;
    }

    public boolean dispatchOptionsItemSelected(MenuItem item) {
        if (mCurState < Fragment.CREATED) return false;
        for (int i = 0; i < mAdded.size(); i++) {
            Fragment f = mAdded.get(i);
            if (f != null && f.performOptionsItemSelected(item)) return true;
        }
        return false;
    }

    public boolean dispatchContextItemSelected(MenuItem item) {
        if (mCurState < Fragment.CREATED) return false;
        for (int i = 0; i < mAdded.size(); i++) {
            Fragment f = mAdded.get(i);
            if (f != null && f.performContextItemSelected(item)) return true;
        }
        return false;
    }

    public void dispatchOptionsMenuClosed(Menu menu) {
        if (mCurState < Fragment.CREATED) return;
        for (int i = 0; i < mAdded.size(); i++) {
            Fragment f = mAdded.get(i);
            if (f != null) f.performOptionsMenuClosed(menu);
        }
    }

    public void setPrimaryNavigationFragment(Fragment f) {
        if (f != null && (mActive.get(f.mIndex) != f || (f.mHost != null && f.getFragmentManager() != this))) {
            throw new IllegalArgumentException("Fragment " + f + " is not an active fragment of FragmentManager " + this);
        }
        mPrimaryNav = f;
    }

    @Override
    public Fragment getPrimaryNavigationFragment() { return mPrimaryNav; }

    @Override
    public void registerFragmentLifecycleCallbacks(FragmentLifecycleCallbacks cb, boolean recursive) {
        mLifecycleCallbacks.add(new Object[] {cb, recursive});
    }

    @Override
    public void unregisterFragmentLifecycleCallbacks(FragmentLifecycleCallbacks cb) {
        synchronized (mLifecycleCallbacks) {
            for (int i = 0, N = mLifecycleCallbacks.size(); i < N; i++) {
                if (mLifecycleCallbacks.get(i)[0] == cb) {
                    mLifecycleCallbacks.remove(i);
                    break;
                }
            }
        }
    }

    private interface CallbackOp {
        void run(FragmentLifecycleCallbacks cb);
    }

    private void dispatchLifecycle(boolean onlyRecursive, CallbackOp op) {
        if (mParent != null) {
            FragmentManager parentManager = mParent.getFragmentManager();
            if (parentManager instanceof FragmentManagerImpl) ((FragmentManagerImpl) parentManager).dispatchLifecycle(true, op);
        }
        for (Object[] p : mLifecycleCallbacks) {
            if (!onlyRecursive || (Boolean) p[1]) op.run((FragmentLifecycleCallbacks) p[0]);
        }
    }

    void dispatchOnFragmentPreAttached(final Fragment f, final Context context, boolean onlyRecursive) {
        dispatchLifecycle(onlyRecursive, new CallbackOp() {
            public void run(FragmentLifecycleCallbacks cb) { cb.onFragmentPreAttached(FragmentManagerImpl.this, f, context); }
        });
    }

    void dispatchOnFragmentAttached(final Fragment f, final Context context, boolean onlyRecursive) {
        dispatchLifecycle(onlyRecursive, new CallbackOp() {
            public void run(FragmentLifecycleCallbacks cb) { cb.onFragmentAttached(FragmentManagerImpl.this, f, context); }
        });
    }

    void dispatchOnFragmentPreCreated(final Fragment f, final Bundle savedInstanceState, boolean onlyRecursive) {
        dispatchLifecycle(onlyRecursive, new CallbackOp() {
            public void run(FragmentLifecycleCallbacks cb) {
                cb.onFragmentPreCreated(FragmentManagerImpl.this, f, savedInstanceState);
            }
        });
    }

    void dispatchOnFragmentCreated(final Fragment f, final Bundle savedInstanceState, boolean onlyRecursive) {
        dispatchLifecycle(onlyRecursive, new CallbackOp() {
            public void run(FragmentLifecycleCallbacks cb) { cb.onFragmentCreated(FragmentManagerImpl.this, f, savedInstanceState); }
        });
    }

    void dispatchOnFragmentActivityCreated(final Fragment f, final Bundle savedInstanceState, boolean onlyRecursive) {
        dispatchLifecycle(onlyRecursive, new CallbackOp() {
            public void run(FragmentLifecycleCallbacks cb) {
                cb.onFragmentActivityCreated(FragmentManagerImpl.this, f, savedInstanceState);
            }
        });
    }

    void dispatchOnFragmentViewCreated(final Fragment f, final View v, final Bundle savedInstanceState,
            boolean onlyRecursive) {
        dispatchLifecycle(onlyRecursive, new CallbackOp() {
            public void run(FragmentLifecycleCallbacks cb) {
                cb.onFragmentViewCreated(FragmentManagerImpl.this, f, v, savedInstanceState);
            }
        });
    }

    void dispatchOnFragmentStarted(final Fragment f, boolean onlyRecursive) {
        dispatchLifecycle(onlyRecursive, new CallbackOp() {
            public void run(FragmentLifecycleCallbacks cb) { cb.onFragmentStarted(FragmentManagerImpl.this, f); }
        });
    }

    void dispatchOnFragmentResumed(final Fragment f, boolean onlyRecursive) {
        dispatchLifecycle(onlyRecursive, new CallbackOp() {
            public void run(FragmentLifecycleCallbacks cb) { cb.onFragmentResumed(FragmentManagerImpl.this, f); }
        });
    }

    void dispatchOnFragmentPaused(final Fragment f, boolean onlyRecursive) {
        dispatchLifecycle(onlyRecursive, new CallbackOp() {
            public void run(FragmentLifecycleCallbacks cb) { cb.onFragmentPaused(FragmentManagerImpl.this, f); }
        });
    }

    void dispatchOnFragmentStopped(final Fragment f, boolean onlyRecursive) {
        dispatchLifecycle(onlyRecursive, new CallbackOp() {
            public void run(FragmentLifecycleCallbacks cb) { cb.onFragmentStopped(FragmentManagerImpl.this, f); }
        });
    }

    void dispatchOnFragmentSaveInstanceState(final Fragment f, final Bundle outState, boolean onlyRecursive) {
        dispatchLifecycle(onlyRecursive, new CallbackOp() {
            public void run(FragmentLifecycleCallbacks cb) {
                cb.onFragmentSaveInstanceState(FragmentManagerImpl.this, f, outState);
            }
        });
    }

    void dispatchOnFragmentViewDestroyed(final Fragment f, boolean onlyRecursive) {
        dispatchLifecycle(onlyRecursive, new CallbackOp() {
            public void run(FragmentLifecycleCallbacks cb) { cb.onFragmentViewDestroyed(FragmentManagerImpl.this, f); }
        });
    }

    void dispatchOnFragmentDestroyed(final Fragment f, boolean onlyRecursive) {
        dispatchLifecycle(onlyRecursive, new CallbackOp() {
            public void run(FragmentLifecycleCallbacks cb) { cb.onFragmentDestroyed(FragmentManagerImpl.this, f); }
        });
    }

    void dispatchOnFragmentDetached(final Fragment f, boolean onlyRecursive) {
        dispatchLifecycle(onlyRecursive, new CallbackOp() {
            public void run(FragmentLifecycleCallbacks cb) { cb.onFragmentDetached(FragmentManagerImpl.this, f); }
        });
    }

    @Override
    public void invalidateOptionsMenu() {
        if (mHost != null && mCurState == Fragment.RESUMED) mHost.onInvalidateOptionsMenu();
        else mNeedMenuInvalidate = true;
    }

    void addRetainedFragment(Fragment f) {}

    void removeRetainedFragment(Fragment f) {}

    // ---------------------------------------------------------------- <fragment> inflation

    private static int[] sFragmentAttrs;

    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        if (!"fragment".equals(name)) return null;
        String fname = attrs.getAttributeValue(null, "class");
        if (sFragmentAttrs == null) sFragmentAttrs = new int[] {android.R.attr.name, android.R.attr.id, android.R.attr.tag};
        TypedArray a = context.obtainStyledAttributes(attrs, sFragmentAttrs);
        if (fname == null) fname = a.getString(0);
        int id = a.getResourceId(1, View.NO_ID);
        String tag = a.getString(2);
        a.recycle();
        int containerId = parent != null ? parent.getId() : 0;
        if (containerId == View.NO_ID && id == View.NO_ID && tag == null) {
            throw new IllegalArgumentException(attrs.getPositionDescription()
                    + ": Must specify unique android:id, android:tag, or have a parent with an id for " + fname);
        }
        Fragment fragment = id != View.NO_ID ? findFragmentById(id) : null;
        if (fragment == null && tag != null) fragment = findFragmentByTag(tag);
        if (fragment == null && containerId != View.NO_ID) fragment = findFragmentById(containerId);
        if (fragment == null) {
            fragment = mContainer.instantiate(context, fname, null);
            fragment.mFromLayout = true;
            fragment.mFragmentId = id != 0 ? id : containerId;
            fragment.mContainerId = containerId;
            fragment.mTag = tag;
            fragment.mInLayout = true;
            fragment.mFragmentManager = this;
            fragment.mHost = mHost;
            fragment.onInflate(mHost.getContext(), attrs, fragment.mSavedFragmentState);
            addFragment(fragment, true);
        } else if (fragment.mInLayout) {
            throw new IllegalArgumentException(attrs.getPositionDescription() + ": Duplicate id 0x"
                    + Integer.toHexString(id) + ", tag " + tag + ", or parent id 0x" + Integer.toHexString(containerId)
                    + " with another fragment for " + fname);
        } else {
            fragment.mInLayout = true;
            fragment.mHost = mHost;
            if (!fragment.mRetaining) fragment.onInflate(mHost.getContext(), attrs, fragment.mSavedFragmentState);
        }
        if (mCurState < Fragment.CREATED && fragment.mFromLayout) moveToState(fragment, Fragment.CREATED, 0, 0, false);
        else moveToState(fragment);
        if (fragment.mView == null) throw new IllegalStateException("Fragment " + fname + " did not create a view.");
        if (id != 0) fragment.mView.setId(id);
        if (fragment.mView.getTag() == null) fragment.mView.setTag(tag);
        return fragment.mView;
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) { return null; }

    LayoutInflater.Factory2 getLayoutInflaterFactory() { return this; }

    /** Pops the back stack (used by the generic pop action). */
    private class PopBackStackState implements OpGenerator {
        final String mName;
        final int mId;
        final int mFlags;

        PopBackStackState(String name, int id, int flags) {
            mName = name;
            mId = id;
            mFlags = flags;
        }

        public boolean generateOps(ArrayList<BackStackRecord> records, ArrayList<Boolean> isRecordPop) {
            if (mPrimaryNav != null && mId < 0 && mName == null) {
                FragmentManagerImpl childManager = mPrimaryNav.mChildFragmentManager;
                if (childManager != null && childManager.popBackStackImmediate()) return false;
            }
            return popBackStackState(records, isRecordPop, mName, mId, mFlags);
        }
    }
}
