package android.app;

import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Message;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import com.android.internal.app.AlertController;
import com.android.internal.util.InternalRes;

/** A dialog with a title, message, up to three buttons, a list or a custom view (port of AOSP AlertDialog). */
public class AlertDialog extends Dialog implements DialogInterface {
    private final AlertController mAlert;

    @Deprecated
    public static final int THEME_TRADITIONAL = 1;
    @Deprecated
    public static final int THEME_HOLO_DARK = 2;
    @Deprecated
    public static final int THEME_HOLO_LIGHT = 3;
    @Deprecated
    public static final int THEME_DEVICE_DEFAULT_DARK = 4;
    @Deprecated
    public static final int THEME_DEVICE_DEFAULT_LIGHT = 5;

    /** Hidden AOSP constants. */
    public static final int LAYOUT_HINT_NONE = 0;
    public static final int LAYOUT_HINT_SIDE = 1;

    protected AlertDialog(Context context) { this(context, 0); }

    protected AlertDialog(Context context, boolean cancelable, OnCancelListener cancelListener) {
        this(context, 0);
        setCancelable(cancelable);
        setOnCancelListener(cancelListener);
    }

    protected AlertDialog(Context context, int themeResId) { this(context, themeResId, true); }

    AlertDialog(Context context, int themeResId, boolean createContextThemeWrapper) {
        super(context, createContextThemeWrapper ? resolveDialogTheme(context, themeResId) : 0,
                createContextThemeWrapper);
        mAlert = AlertController.create(getContext(), this, getWindow());
    }

    static int resolveDialogTheme(Context context, int themeResId) {
        switch (themeResId) {
            case THEME_TRADITIONAL: return InternalRes.style("Theme.Dialog.Alert");
            case THEME_HOLO_DARK: return InternalRes.style("Theme.Holo.Dialog.Alert");
            case THEME_HOLO_LIGHT: return InternalRes.style("Theme.Holo.Light.Dialog.Alert");
            case THEME_DEVICE_DEFAULT_DARK: return android.R.style.Theme_DeviceDefault_Dialog_Alert;
            case THEME_DEVICE_DEFAULT_LIGHT: return android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
            default:
                break;
        }
        if ((themeResId >>> 24) >= 0x01) return themeResId;
        final TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.alertDialogTheme, outValue, true);
        return outValue.resourceId;
    }

    public Button getButton(int whichButton) { return mAlert.getButton(whichButton); }

    public ListView getListView() { return mAlert.getListView(); }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        mAlert.setTitle(title);
    }

    public void setCustomTitle(View customTitleView) { mAlert.setCustomTitle(customTitleView); }

    public void setMessage(CharSequence message) { mAlert.setMessage(message); }

    public void setView(View view) { mAlert.setView(view); }

    public void setView(View view, int viewSpacingLeft, int viewSpacingTop, int viewSpacingRight,
            int viewSpacingBottom) {
        mAlert.setView(view, viewSpacingLeft, viewSpacingTop, viewSpacingRight, viewSpacingBottom);
    }

    /** Hidden AOSP API. */
    void setButtonPanelLayoutHint(int layoutHint) { mAlert.setButtonPanelLayoutHint(layoutHint); }

    public void setButton(int whichButton, CharSequence text, Message msg) { mAlert.setButton(whichButton, text, null, msg); }

    public void setButton(int whichButton, CharSequence text, OnClickListener listener) {
        mAlert.setButton(whichButton, text, listener, null);
    }

    @Deprecated
    public void setButton(CharSequence text, Message msg) { setButton(BUTTON_POSITIVE, text, msg); }

    @Deprecated
    public void setButton2(CharSequence text, Message msg) { setButton(BUTTON_NEGATIVE, text, msg); }

    @Deprecated
    public void setButton3(CharSequence text, Message msg) { setButton(BUTTON_NEUTRAL, text, msg); }

    @Deprecated
    public void setButton(CharSequence text, final OnClickListener listener) { setButton(BUTTON_POSITIVE, text, listener); }

    @Deprecated
    public void setButton2(CharSequence text, final OnClickListener listener) { setButton(BUTTON_NEGATIVE, text, listener); }

    @Deprecated
    public void setButton3(CharSequence text, final OnClickListener listener) { setButton(BUTTON_NEUTRAL, text, listener); }

    public void setIcon(int resId) { mAlert.setIcon(resId); }

    public void setIcon(Drawable icon) { mAlert.setIcon(icon); }

    public void setIconAttribute(int attrId) {
        TypedValue out = new TypedValue();
        getContext().getTheme().resolveAttribute(attrId, out, true);
        mAlert.setIcon(out.resourceId);
    }

    public void setInverseBackgroundForced(boolean forceInverseBackground) {
        mAlert.setInverseBackgroundForced(forceInverseBackground);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAlert.installContent();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mAlert.onKeyDown(keyCode, event)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mAlert.onKeyUp(keyCode, event)) return true;
        return super.onKeyUp(keyCode, event);
    }

    public static class Builder {
        private final AlertController.AlertParams P;

        public Builder(Context context) { this(context, resolveDialogTheme(context, 0)); }

        public Builder(Context context, int themeResId) {
            P = new AlertController.AlertParams(new ContextThemeWrapper(context, resolveDialogTheme(context, themeResId)));
        }

        public Context getContext() { return P.mContext; }

        public Builder setTitle(int titleId) {
            P.mTitle = P.mContext.getText(titleId);
            return this;
        }

        public Builder setTitle(CharSequence title) {
            P.mTitle = title;
            return this;
        }

        public Builder setCustomTitle(View customTitleView) {
            P.mCustomTitleView = customTitleView;
            return this;
        }

        public Builder setMessage(int messageId) {
            P.mMessage = P.mContext.getText(messageId);
            return this;
        }

        public Builder setMessage(CharSequence message) {
            P.mMessage = message;
            return this;
        }

        public Builder setIcon(int iconId) {
            P.mIconId = iconId;
            return this;
        }

        public Builder setIcon(Drawable icon) {
            P.mIcon = icon;
            return this;
        }

        public Builder setIconAttribute(int attrId) {
            TypedValue out = new TypedValue();
            P.mContext.getTheme().resolveAttribute(attrId, out, true);
            P.mIconId = out.resourceId;
            return this;
        }

        public Builder setPositiveButton(int textId, final OnClickListener listener) {
            P.mPositiveButtonText = P.mContext.getText(textId);
            P.mPositiveButtonListener = listener;
            return this;
        }

        public Builder setPositiveButton(CharSequence text, final OnClickListener listener) {
            P.mPositiveButtonText = text;
            P.mPositiveButtonListener = listener;
            return this;
        }

        public Builder setNegativeButton(int textId, final OnClickListener listener) {
            P.mNegativeButtonText = P.mContext.getText(textId);
            P.mNegativeButtonListener = listener;
            return this;
        }

        public Builder setNegativeButton(CharSequence text, final OnClickListener listener) {
            P.mNegativeButtonText = text;
            P.mNegativeButtonListener = listener;
            return this;
        }

        public Builder setNeutralButton(int textId, final OnClickListener listener) {
            P.mNeutralButtonText = P.mContext.getText(textId);
            P.mNeutralButtonListener = listener;
            return this;
        }

        public Builder setNeutralButton(CharSequence text, final OnClickListener listener) {
            P.mNeutralButtonText = text;
            P.mNeutralButtonListener = listener;
            return this;
        }

        public Builder setCancelable(boolean cancelable) {
            P.mCancelable = cancelable;
            return this;
        }

        public Builder setOnCancelListener(OnCancelListener onCancelListener) {
            P.mOnCancelListener = onCancelListener;
            return this;
        }

        public Builder setOnDismissListener(OnDismissListener onDismissListener) {
            P.mOnDismissListener = onDismissListener;
            return this;
        }

        public Builder setOnKeyListener(OnKeyListener onKeyListener) {
            P.mOnKeyListener = onKeyListener;
            return this;
        }

        public Builder setItems(int itemsId, final OnClickListener listener) {
            P.mItems = P.mContext.getResources().getTextArray(itemsId);
            P.mOnClickListener = listener;
            return this;
        }

        public Builder setItems(CharSequence[] items, final OnClickListener listener) {
            P.mItems = items;
            P.mOnClickListener = listener;
            return this;
        }

        public Builder setAdapter(final ListAdapter adapter, final OnClickListener listener) {
            P.mAdapter = adapter;
            P.mOnClickListener = listener;
            return this;
        }

        /** Cursor-backed lists need CursorAdapter, which is not implemented yet; this shows nothing. */
        public Builder setCursor(final Cursor cursor, final OnClickListener listener, String labelColumn) {
            P.mOnClickListener = listener;
            return this;
        }

        public Builder setMultiChoiceItems(int itemsId, boolean[] checkedItems,
                final OnMultiChoiceClickListener listener) {
            P.mItems = P.mContext.getResources().getTextArray(itemsId);
            P.mOnCheckboxClickListener = listener;
            P.mCheckedItems = checkedItems;
            P.mIsMultiChoice = true;
            return this;
        }

        public Builder setMultiChoiceItems(CharSequence[] items, boolean[] checkedItems,
                final OnMultiChoiceClickListener listener) {
            P.mItems = items;
            P.mOnCheckboxClickListener = listener;
            P.mCheckedItems = checkedItems;
            P.mIsMultiChoice = true;
            return this;
        }

        /** Cursor-backed lists need CursorAdapter, which is not implemented yet; this shows nothing. */
        public Builder setMultiChoiceItems(Cursor cursor, String isCheckedColumn, String labelColumn,
                final OnMultiChoiceClickListener listener) {
            P.mOnCheckboxClickListener = listener;
            P.mIsMultiChoice = true;
            return this;
        }

        public Builder setSingleChoiceItems(int itemsId, int checkedItem, final OnClickListener listener) {
            P.mItems = P.mContext.getResources().getTextArray(itemsId);
            P.mOnClickListener = listener;
            P.mCheckedItem = checkedItem;
            P.mIsSingleChoice = true;
            return this;
        }

        /** Cursor-backed lists need CursorAdapter, which is not implemented yet; this shows nothing. */
        public Builder setSingleChoiceItems(Cursor cursor, int checkedItem, String labelColumn,
                final OnClickListener listener) {
            P.mOnClickListener = listener;
            P.mCheckedItem = checkedItem;
            P.mIsSingleChoice = true;
            return this;
        }

        public Builder setSingleChoiceItems(CharSequence[] items, int checkedItem, final OnClickListener listener) {
            P.mItems = items;
            P.mOnClickListener = listener;
            P.mCheckedItem = checkedItem;
            P.mIsSingleChoice = true;
            return this;
        }

        public Builder setSingleChoiceItems(ListAdapter adapter, int checkedItem, final OnClickListener listener) {
            P.mAdapter = adapter;
            P.mOnClickListener = listener;
            P.mCheckedItem = checkedItem;
            P.mIsSingleChoice = true;
            return this;
        }

        public Builder setOnItemSelectedListener(final AdapterView.OnItemSelectedListener listener) {
            P.mOnItemSelectedListener = listener;
            return this;
        }

        public Builder setView(int layoutResId) {
            P.mView = null;
            P.mViewLayoutResId = layoutResId;
            P.mViewSpacingSpecified = false;
            return this;
        }

        public Builder setView(View view) {
            P.mView = view;
            P.mViewLayoutResId = 0;
            P.mViewSpacingSpecified = false;
            return this;
        }

        /** Hidden AOSP API. */
        @Deprecated
        public Builder setView(View view, int viewSpacingLeft, int viewSpacingTop, int viewSpacingRight,
                int viewSpacingBottom) {
            P.mView = view;
            P.mViewLayoutResId = 0;
            P.mViewSpacingSpecified = true;
            P.mViewSpacingLeft = viewSpacingLeft;
            P.mViewSpacingTop = viewSpacingTop;
            P.mViewSpacingRight = viewSpacingRight;
            P.mViewSpacingBottom = viewSpacingBottom;
            return this;
        }

        @Deprecated
        public Builder setInverseBackgroundForced(boolean useInverseBackground) { return this; }

        /** Hidden AOSP API. */
        public Builder setRecycleOnMeasureEnabled(boolean enabled) {
            P.mRecycleOnMeasure = enabled;
            return this;
        }

        public AlertDialog create() {
            final AlertDialog dialog = new AlertDialog(P.mContext, 0, false);
            P.apply(dialog.mAlert);
            dialog.setCancelable(P.mCancelable);
            if (P.mCancelable) dialog.setCanceledOnTouchOutside(true);
            dialog.setOnCancelListener(P.mOnCancelListener);
            dialog.setOnDismissListener(P.mOnDismissListener);
            if (P.mOnKeyListener != null) dialog.setOnKeyListener(P.mOnKeyListener);
            return dialog;
        }

        public AlertDialog show() {
            final AlertDialog dialog = create();
            dialog.show();
            return dialog;
        }
    }
}
