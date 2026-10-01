package android.app;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/** A fragment that shows a dialog window (port of AOSP DialogFragment). */
@Deprecated
public class DialogFragment extends Fragment implements DialogInterface.OnCancelListener, DialogInterface.OnDismissListener {
    public static final int STYLE_NORMAL = 0;
    public static final int STYLE_NO_TITLE = 1;
    public static final int STYLE_NO_FRAME = 2;
    public static final int STYLE_NO_INPUT = 3;

    private static final String SAVED_DIALOG_STATE_TAG = "android:savedDialogState";
    private static final String SAVED_STYLE = "android:style";
    private static final String SAVED_THEME = "android:theme";
    private static final String SAVED_CANCELABLE = "android:cancelable";
    private static final String SAVED_SHOWS_DIALOG = "android:showsDialog";
    private static final String SAVED_BACK_STACK_ID = "android:backStackId";

    int mStyle = STYLE_NORMAL;
    int mTheme = 0;
    boolean mCancelable = true;
    boolean mShowsDialog = true;
    int mBackStackId = -1;
    Dialog mDialog;
    boolean mViewDestroyed;
    boolean mDismissed;
    boolean mShownByMe;

    public DialogFragment() {}

    public void setStyle(int style, int theme) {
        mStyle = style;
        if (mStyle == STYLE_NO_FRAME || mStyle == STYLE_NO_INPUT) {
            mTheme = com.android.internal.util.InternalRes.style("Theme.DeviceDefault.Dialog.NoFrame");
        }
        if (theme != 0) mTheme = theme;
    }

    public void show(FragmentManager manager, String tag) {
        mDismissed = false;
        mShownByMe = true;
        FragmentTransaction ft = manager.beginTransaction();
        ft.add(this, tag);
        ft.commit();
    }

    /** Hidden AOSP API. */
    public void showAllowingStateLoss(FragmentManager manager, String tag) {
        mDismissed = false;
        mShownByMe = true;
        FragmentTransaction ft = manager.beginTransaction();
        ft.add(this, tag);
        ft.commitAllowingStateLoss();
    }

    public int show(FragmentTransaction transaction, String tag) {
        mDismissed = false;
        mShownByMe = true;
        transaction.add(this, tag);
        mViewDestroyed = false;
        mBackStackId = transaction.commit();
        return mBackStackId;
    }

    public void dismiss() { dismissInternal(false); }

    public void dismissAllowingStateLoss() { dismissInternal(true); }

    void dismissInternal(boolean allowStateLoss) {
        if (mDismissed) return;
        mDismissed = true;
        mShownByMe = false;
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        mViewDestroyed = true;
        if (mBackStackId >= 0) {
            getFragmentManager().popBackStack(mBackStackId, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            mBackStackId = -1;
        } else {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.remove(this);
            if (allowStateLoss) ft.commitAllowingStateLoss();
            else ft.commit();
        }
    }

    public Dialog getDialog() { return mDialog; }

    public int getTheme() { return mTheme; }

    public void setCancelable(boolean cancelable) {
        mCancelable = cancelable;
        if (mDialog != null) mDialog.setCancelable(cancelable);
    }

    public boolean isCancelable() { return mCancelable; }

    public void setShowsDialog(boolean showsDialog) { mShowsDialog = showsDialog; }

    public boolean getShowsDialog() { return mShowsDialog; }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (!mShownByMe) mDismissed = false;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (!mShownByMe && !mDismissed) mDismissed = true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mShowsDialog = mContainerId == 0;
        if (savedInstanceState != null) {
            mStyle = savedInstanceState.getInt(SAVED_STYLE, STYLE_NORMAL);
            mTheme = savedInstanceState.getInt(SAVED_THEME, 0);
            mCancelable = savedInstanceState.getBoolean(SAVED_CANCELABLE, true);
            mShowsDialog = savedInstanceState.getBoolean(SAVED_SHOWS_DIALOG, mShowsDialog);
            mBackStackId = savedInstanceState.getInt(SAVED_BACK_STACK_ID, -1);
        }
    }

    @Override
    public LayoutInflater onGetLayoutInflater(Bundle savedInstanceState) {
        if (!mShowsDialog) return super.onGetLayoutInflater(savedInstanceState);
        mDialog = onCreateDialog(savedInstanceState);
        switch (mStyle) {
            case STYLE_NO_INPUT:
                mDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                // fall through
            case STYLE_NO_FRAME:
            case STYLE_NO_TITLE:
                mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                break;
            default:
                break;
        }
        if (mDialog != null) {
            return (LayoutInflater) mDialog.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }
        return (LayoutInflater) mHost.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) { return new Dialog(getActivity(), getTheme()); }

    public void onCancel(DialogInterface dialog) {}

    public void onDismiss(DialogInterface dialog) {
        if (!mViewDestroyed) dismissInternal(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (!mShowsDialog) return;
        View view = getView();
        if (view != null) {
            if (view.getParent() != null) {
                throw new IllegalStateException("DialogFragment can not be attached to a container view");
            }
            mDialog.setContentView(view);
        }
        final Activity activity = getActivity();
        if (activity != null) mDialog.setOwnerActivity(activity);
        mDialog.setCancelable(mCancelable);
        if (!mDialog.takeCancelAndDismissListeners("DialogFragment", this, this)) {
            throw new IllegalStateException("You can not set Dialog's OnCancelListener or OnDismissListener");
        }
        if (savedInstanceState != null) {
            Bundle dialogState = savedInstanceState.getBundle(SAVED_DIALOG_STATE_TAG);
            if (dialogState != null) mDialog.onRestoreInstanceState(dialogState);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mDialog != null) {
            mViewDestroyed = false;
            mDialog.show();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mDialog != null) {
            Bundle dialogState = mDialog.onSaveInstanceState();
            if (dialogState != null) outState.putBundle(SAVED_DIALOG_STATE_TAG, dialogState);
        }
        if (mStyle != STYLE_NORMAL) outState.putInt(SAVED_STYLE, mStyle);
        if (mTheme != 0) outState.putInt(SAVED_THEME, mTheme);
        if (!mCancelable) outState.putBoolean(SAVED_CANCELABLE, mCancelable);
        if (!mShowsDialog) outState.putBoolean(SAVED_SHOWS_DIALOG, mShowsDialog);
        if (mBackStackId != -1) outState.putInt(SAVED_BACK_STACK_ID, mBackStackId);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mDialog != null) mDialog.hide();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mDialog != null) {
            mViewDestroyed = true;
            mDialog.dismiss();
            mDialog = null;
        }
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        writer.print(prefix);
        writer.println("DialogFragment:");
        writer.print(prefix);
        writer.print("  mStyle=");
        writer.print(mStyle);
        writer.print(" mTheme=0x");
        writer.println(Integer.toHexString(mTheme));
    }
}
