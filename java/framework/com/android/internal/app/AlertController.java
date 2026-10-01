package com.android.internal.app;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.view.ViewStub;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import com.android.internal.util.InternalRes;
import java.lang.ref.WeakReference;

/**
 * Builds the content of an AlertDialog from the framework's alert layouts
 * (port of AOSP AlertController, material layouts only).
 */
public class AlertController {
    public static final int MICRO = 1;

    private final Context mContext;
    private final DialogInterface mDialogInterface;
    protected final Window mWindow;

    private CharSequence mTitle;
    protected CharSequence mMessage;
    protected ListView mListView;
    private View mView;
    private int mViewLayoutResId;
    private int mViewSpacingLeft;
    private int mViewSpacingTop;
    private int mViewSpacingRight;
    private int mViewSpacingBottom;
    private boolean mViewSpacingSpecified = false;

    private Button mButtonPositive;
    private CharSequence mButtonPositiveText;
    private Message mButtonPositiveMessage;
    private Button mButtonNegative;
    private CharSequence mButtonNegativeText;
    private Message mButtonNegativeMessage;
    private Button mButtonNeutral;
    private CharSequence mButtonNeutralText;
    private Message mButtonNeutralMessage;

    protected ScrollView mScrollView;
    private int mIconId = 0;
    private Drawable mIcon;
    private ImageView mIconView;
    private TextView mTitleView;
    protected TextView mMessageView;
    private View mCustomTitleView;
    private boolean mForceInverseBackground;
    private ListAdapter mAdapter;
    private int mCheckedItem = -1;

    private int mAlertDialogLayout;
    private int mButtonPanelSideLayout;
    private int mListLayout;
    private int mMultiChoiceItemLayout;
    private int mSingleChoiceItemLayout;
    private int mListItemLayout;
    private boolean mShowTitle;
    private int mSelectionScrollOffset;
    private int mButtonPanelLayoutHint = AlertDialogLayoutHint.NONE;

    private final Handler mHandler;

    /** Hidden AOSP layout hints (AlertDialog.LAYOUT_HINT_*). */
    static final class AlertDialogLayoutHint {
        static final int NONE = 0;
        static final int SIDE = 1;
    }

    private final View.OnClickListener mButtonHandler = new View.OnClickListener() {
        public void onClick(View v) {
            final Message m;
            if (v == mButtonPositive && mButtonPositiveMessage != null) {
                m = Message.obtain(mButtonPositiveMessage);
            } else if (v == mButtonNegative && mButtonNegativeMessage != null) {
                m = Message.obtain(mButtonNegativeMessage);
            } else if (v == mButtonNeutral && mButtonNeutralMessage != null) {
                m = Message.obtain(mButtonNeutralMessage);
            } else {
                m = null;
            }
            if (m != null) m.sendToTarget();
            mHandler.obtainMessage(ButtonHandler.MSG_DISMISS_DIALOG, mDialogInterface).sendToTarget();
        }
    };

    private static final class ButtonHandler extends Handler {
        private static final int MSG_DISMISS_DIALOG = 1;
        private final WeakReference<DialogInterface> mDialog;

        public ButtonHandler(DialogInterface dialog) { mDialog = new WeakReference<DialogInterface>(dialog); }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DialogInterface.BUTTON_POSITIVE:
                case DialogInterface.BUTTON_NEGATIVE:
                case DialogInterface.BUTTON_NEUTRAL:
                    ((DialogInterface.OnClickListener) msg.obj).onClick(mDialog.get(), msg.what);
                    break;
                case MSG_DISMISS_DIALOG:
                    ((DialogInterface) msg.obj).dismiss();
                    break;
                default:
                    break;
            }
        }
    }

    private static int[] sAlertAttrs;

    /** Index order of {@link #alertAttrs}. */
    private static final int A_LAYOUT = 0;
    private static final int A_SIDE_LAYOUT = 1;
    private static final int A_LIST_LAYOUT = 2;
    private static final int A_MULTI_LAYOUT = 3;
    private static final int A_SINGLE_LAYOUT = 4;
    private static final int A_ITEM_LAYOUT = 5;
    private static final int A_SHOW_TITLE = 6;
    private static final int A_SCROLL_OFFSET = 7;

    static int[] alertAttrs() {
        if (sAlertAttrs == null) {
            sAlertAttrs = InternalRes.attrs("android:layout", "buttonPanelSideLayout", "listLayout",
                    "multiChoiceItemLayout", "singleChoiceItemLayout", "listItemLayout", "showTitle",
                    "selectionScrollOffset");
        }
        return sAlertAttrs;
    }

    public static AlertController create(Context context, DialogInterface di, Window window) {
        return new AlertController(context, di, window);
    }

    protected AlertController(Context context, DialogInterface di, Window window) {
        mContext = context;
        mDialogInterface = di;
        mWindow = window;
        mHandler = new ButtonHandler(di);
        final TypedArray a = context.obtainStyledAttributes(null, alertAttrs(), android.R.attr.alertDialogStyle, 0);
        mAlertDialogLayout = a.getResourceId(A_LAYOUT, InternalRes.layout("alert_dialog_material"));
        mButtonPanelSideLayout = a.getResourceId(A_SIDE_LAYOUT, 0);
        mListLayout = a.getResourceId(A_LIST_LAYOUT, InternalRes.layout("select_dialog_material"));
        mMultiChoiceItemLayout = a.getResourceId(A_MULTI_LAYOUT, InternalRes.layout("select_dialog_multichoice_material"));
        mSingleChoiceItemLayout = a.getResourceId(A_SINGLE_LAYOUT,
                InternalRes.layout("select_dialog_singlechoice_material"));
        mListItemLayout = a.getResourceId(A_ITEM_LAYOUT, InternalRes.layout("select_dialog_item_material"));
        mShowTitle = a.getBoolean(A_SHOW_TITLE, true);
        mSelectionScrollOffset = a.getDimensionPixelSize(A_SCROLL_OFFSET, 0);
        a.recycle();
        window.requestFeature(Window.FEATURE_NO_TITLE);
    }

    static boolean canTextInput(View v) {
        if (v.onCheckIsTextEditor()) return true;
        if (!(v instanceof ViewGroup)) return false;
        ViewGroup vg = (ViewGroup) v;
        int i = vg.getChildCount();
        while (i > 0) {
            i--;
            v = vg.getChildAt(i);
            if (canTextInput(v)) return true;
        }
        return false;
    }

    public void installContent(AlertParams params) {
        params.apply(this);
        installContent();
    }

    public void installContent() {
        int contentView = selectContentView();
        mWindow.setContentView(contentView);
        setupView();
    }

    private int selectContentView() {
        if (mButtonPanelSideLayout == 0) return mAlertDialogLayout;
        if (mButtonPanelLayoutHint == AlertDialogLayoutHint.SIDE) return mButtonPanelSideLayout;
        return mAlertDialogLayout;
    }

    public void setTitle(CharSequence title) {
        mTitle = title;
        if (mTitleView != null) mTitleView.setText(title);
    }

    public void setCustomTitle(View customTitleView) { mCustomTitleView = customTitleView; }

    public void setMessage(CharSequence message) {
        mMessage = message;
        if (mMessageView != null) mMessageView.setText(message);
    }

    public void setView(int layoutResId) {
        mView = null;
        mViewLayoutResId = layoutResId;
        mViewSpacingSpecified = false;
    }

    public void setView(View view) {
        mView = view;
        mViewLayoutResId = 0;
        mViewSpacingSpecified = false;
    }

    public void setView(View view, int viewSpacingLeft, int viewSpacingTop, int viewSpacingRight,
            int viewSpacingBottom) {
        mView = view;
        mViewLayoutResId = 0;
        mViewSpacingSpecified = true;
        mViewSpacingLeft = viewSpacingLeft;
        mViewSpacingTop = viewSpacingTop;
        mViewSpacingRight = viewSpacingRight;
        mViewSpacingBottom = viewSpacingBottom;
    }

    public void setButtonPanelLayoutHint(int layoutHint) { mButtonPanelLayoutHint = layoutHint; }

    public void setButton(int whichButton, CharSequence text, DialogInterface.OnClickListener listener, Message msg) {
        if (msg == null && listener != null) msg = mHandler.obtainMessage(whichButton, listener);
        switch (whichButton) {
            case DialogInterface.BUTTON_POSITIVE:
                mButtonPositiveText = text;
                mButtonPositiveMessage = msg;
                break;
            case DialogInterface.BUTTON_NEGATIVE:
                mButtonNegativeText = text;
                mButtonNegativeMessage = msg;
                break;
            case DialogInterface.BUTTON_NEUTRAL:
                mButtonNeutralText = text;
                mButtonNeutralMessage = msg;
                break;
            default:
                throw new IllegalArgumentException("Button does not exist");
        }
    }

    public void setIcon(int resId) {
        mIcon = null;
        mIconId = resId;
        if (mIconView != null) {
            if (resId != 0) {
                mIconView.setVisibility(View.VISIBLE);
                mIconView.setImageResource(mIconId);
            } else {
                mIconView.setVisibility(View.GONE);
            }
        }
    }

    public void setIcon(Drawable icon) {
        mIcon = icon;
        mIconId = 0;
        if (mIconView != null) {
            if (icon != null) {
                mIconView.setVisibility(View.VISIBLE);
                mIconView.setImageDrawable(icon);
            } else {
                mIconView.setVisibility(View.GONE);
            }
        }
    }

    public int getIconAttributeResId(int attrId) {
        TypedValue out = new TypedValue();
        mContext.getTheme().resolveAttribute(attrId, out, true);
        return out.resourceId;
    }

    public void setInverseBackgroundForced(boolean forceInverseBackground) {
        mForceInverseBackground = forceInverseBackground;
    }

    public ListView getListView() { return mListView; }

    public Button getButton(int whichButton) {
        switch (whichButton) {
            case DialogInterface.BUTTON_POSITIVE: return mButtonPositive;
            case DialogInterface.BUTTON_NEGATIVE: return mButtonNegative;
            case DialogInterface.BUTTON_NEUTRAL: return mButtonNeutral;
            default: return null;
        }
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) { return mScrollView != null && mScrollView.executeKeyEvent(event); }

    public boolean onKeyUp(int keyCode, KeyEvent event) { return mScrollView != null && mScrollView.executeKeyEvent(event); }

    private ViewGroup resolvePanel(View customPanel, View defaultPanel) {
        if (customPanel == null) {
            if (defaultPanel instanceof ViewStub) defaultPanel = ((ViewStub) defaultPanel).inflate();
            return (ViewGroup) defaultPanel;
        }
        if (defaultPanel != null) {
            final ViewParent parent = defaultPanel.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(defaultPanel);
        }
        if (customPanel instanceof ViewStub) customPanel = ((ViewStub) customPanel).inflate();
        return (ViewGroup) customPanel;
    }

    private static View find(View parent, String name) {
        int id = InternalRes.viewId(name);
        return id != 0 && parent != null ? parent.findViewById(id) : null;
    }

    private void setupView() {
        final View parentPanel = mWindow.findViewById(InternalRes.viewId("parentPanel"));
        final View defaultTopPanel = find(parentPanel, "topPanel");
        final View defaultContentPanel = find(parentPanel, "contentPanel");
        final View defaultButtonPanel = find(parentPanel, "buttonPanel");
        final ViewGroup customPanel = (ViewGroup) find(parentPanel, "customPanel");
        setupCustomContent(customPanel);
        final View customTopPanel = find(customPanel, "topPanel");
        final View customContentPanel = find(customPanel, "contentPanel");
        final View customButtonPanel = find(customPanel, "buttonPanel");
        final ViewGroup topPanel = resolvePanel(customTopPanel, defaultTopPanel);
        final ViewGroup contentPanel = resolvePanel(customContentPanel, defaultContentPanel);
        final ViewGroup buttonPanel = resolvePanel(customButtonPanel, defaultButtonPanel);
        setupContent(contentPanel);
        setupButtons(buttonPanel);
        setupTitle(topPanel);
        final boolean hasCustomPanel = customPanel != null && customPanel.getVisibility() != View.GONE;
        final boolean hasTopPanel = topPanel != null && topPanel.getVisibility() != View.GONE;
        final boolean hasButtonPanel = buttonPanel != null && buttonPanel.getVisibility() != View.GONE;
        if (parentPanel != null && !parentPanel.isInTouchMode()) {
            final View content = hasCustomPanel ? customPanel : contentPanel;
            if (!requestFocusForContent(content)) requestFocusForDefaultButton();
        }
        if (!hasButtonPanel) {
            if (contentPanel != null) {
                final View spacer = find(contentPanel, "textSpacerNoButtons");
                if (spacer != null) spacer.setVisibility(View.VISIBLE);
            }
            mWindow.setCloseOnTouchOutsideIfNotSet(true);
        }
        if (hasTopPanel) {
            if (mScrollView != null) mScrollView.setClipToPadding(true);
            View divider = null;
            if (mMessage != null || mListView != null || hasCustomPanel) {
                if (!hasCustomPanel) divider = find(topPanel, "titleDividerNoCustom");
                if (divider == null) divider = find(topPanel, "titleDivider");
            } else {
                divider = find(topPanel, "titleDividerTop");
            }
            if (divider != null) divider.setVisibility(View.VISIBLE);
        } else {
            if (contentPanel != null) {
                final View spacer = find(contentPanel, "textSpacerNoTitle");
                if (spacer != null) spacer.setVisibility(View.VISIBLE);
            }
        }
        if (mListView instanceof RecycleListView) ((RecycleListView) mListView).setHasDecor(hasTopPanel, hasButtonPanel);
        if (!hasCustomPanel) {
            final View content = mListView != null ? mListView : mScrollView;
            if (content != null) {
                final int indicators = (hasTopPanel ? View.SCROLL_INDICATOR_TOP : 0)
                        | (hasButtonPanel ? View.SCROLL_INDICATOR_BOTTOM : 0);
                content.setScrollIndicators(indicators, View.SCROLL_INDICATOR_TOP | View.SCROLL_INDICATOR_BOTTOM);
            }
        }
        final ListView listView = mListView;
        if (listView != null && mAdapter != null) {
            listView.setAdapter(mAdapter);
            final int checkedItem = mCheckedItem;
            if (checkedItem > -1) {
                listView.setItemChecked(checkedItem, true);
                listView.setSelectionFromTop(checkedItem, mSelectionScrollOffset);
            }
        }
    }

    private boolean requestFocusForContent(View content) {
        if (content != null && content.requestFocus()) return true;
        if (mListView != null) {
            mListView.setSelection(0);
            return true;
        }
        return false;
    }

    private void requestFocusForDefaultButton() {
        if (mButtonPositive != null && mButtonPositive.getVisibility() == View.VISIBLE) {
            mButtonPositive.requestFocus();
        } else if (mButtonNegative != null && mButtonNegative.getVisibility() == View.VISIBLE) {
            mButtonNegative.requestFocus();
        } else if (mButtonNeutral != null && mButtonNeutral.getVisibility() == View.VISIBLE) {
            mButtonNeutral.requestFocus();
        }
    }

    private void setupCustomContent(ViewGroup customPanel) {
        final View customView;
        if (mView != null) {
            customView = mView;
        } else if (mViewLayoutResId != 0) {
            final LayoutInflater inflater = LayoutInflater.from(mContext);
            customView = inflater.inflate(mViewLayoutResId, customPanel, false);
        } else {
            customView = null;
        }
        final boolean hasCustomView = customView != null;
        if (!hasCustomView || !canTextInput(customView)) {
            mWindow.setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        }
        if (customPanel == null) return;
        if (hasCustomView) {
            final FrameLayout custom = (FrameLayout) mWindow.findViewById(android.R.id.custom);
            custom.addView(customView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            if (mViewSpacingSpecified) {
                custom.setPadding(mViewSpacingLeft, mViewSpacingTop, mViewSpacingRight, mViewSpacingBottom);
            }
            if (mListView != null) ((LinearLayout.LayoutParams) customPanel.getLayoutParams()).weight = 0;
        } else {
            customPanel.setVisibility(View.GONE);
        }
    }

    private void setupTitle(ViewGroup topPanel) {
        if (topPanel == null) return;
        if (mCustomTitleView != null && mShowTitle) {
            final LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            topPanel.addView(mCustomTitleView, 0, lp);
            final View titleTemplate = mWindow.findViewById(InternalRes.viewId("title_template"));
            if (titleTemplate != null) titleTemplate.setVisibility(View.GONE);
        } else {
            mIconView = (ImageView) mWindow.findViewById(android.R.id.icon);
            final boolean hasTextTitle = !TextUtils.isEmpty(mTitle);
            if (hasTextTitle && mShowTitle) {
                mTitleView = (TextView) mWindow.findViewById(InternalRes.viewId("alertTitle"));
                mTitleView.setText(mTitle);
                if (mIconId != 0) {
                    mIconView.setImageResource(mIconId);
                } else if (mIcon != null) {
                    mIconView.setImageDrawable(mIcon);
                } else if (mIconView != null) {
                    mTitleView.setPadding(mIconView.getPaddingLeft(), mIconView.getPaddingTop(),
                            mIconView.getPaddingRight(), mIconView.getPaddingBottom());
                    mIconView.setVisibility(View.GONE);
                }
            } else {
                final View titleTemplate = mWindow.findViewById(InternalRes.viewId("title_template"));
                if (titleTemplate != null) titleTemplate.setVisibility(View.GONE);
                if (mIconView != null) mIconView.setVisibility(View.GONE);
                topPanel.setVisibility(View.GONE);
            }
        }
    }

    protected void setupContent(ViewGroup contentPanel) {
        if (contentPanel == null) return;
        mScrollView = (ScrollView) find(contentPanel, "scrollView");
        if (mScrollView != null) mScrollView.setFocusable(false);
        mMessageView = (TextView) contentPanel.findViewById(android.R.id.message);
        if (mMessageView == null) return;
        if (mMessage != null) {
            mMessageView.setText(mMessage);
        } else {
            mMessageView.setVisibility(View.GONE);
            if (mListView != null && mScrollView != null) {
                final ViewGroup scrollParent = (ViewGroup) mScrollView.getParent();
                final int childIndex = scrollParent.indexOfChild(mScrollView);
                scrollParent.removeViewAt(childIndex);
                scrollParent.addView(mListView, childIndex,
                        new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            } else {
                contentPanel.setVisibility(View.GONE);
            }
        }
    }

    protected void setupButtons(ViewGroup buttonPanel) {
        if (buttonPanel == null) return;
        int whichButtons = 0;
        mButtonPositive = (Button) buttonPanel.findViewById(android.R.id.button1);
        mButtonPositive.setOnClickListener(mButtonHandler);
        if (TextUtils.isEmpty(mButtonPositiveText)) {
            mButtonPositive.setVisibility(View.GONE);
        } else {
            mButtonPositive.setText(mButtonPositiveText);
            mButtonPositive.setVisibility(View.VISIBLE);
            whichButtons |= 1;
        }
        mButtonNegative = (Button) buttonPanel.findViewById(android.R.id.button2);
        mButtonNegative.setOnClickListener(mButtonHandler);
        if (TextUtils.isEmpty(mButtonNegativeText)) {
            mButtonNegative.setVisibility(View.GONE);
        } else {
            mButtonNegative.setText(mButtonNegativeText);
            mButtonNegative.setVisibility(View.VISIBLE);
            whichButtons |= 2;
        }
        mButtonNeutral = (Button) buttonPanel.findViewById(android.R.id.button3);
        mButtonNeutral.setOnClickListener(mButtonHandler);
        if (TextUtils.isEmpty(mButtonNeutralText)) {
            mButtonNeutral.setVisibility(View.GONE);
        } else {
            mButtonNeutral.setText(mButtonNeutralText);
            mButtonNeutral.setVisibility(View.VISIBLE);
            whichButtons |= 4;
        }
        if (whichButtons == 0) buttonPanel.setVisibility(View.GONE);
    }

    /** ListView with padding that adapts to a missing title or button bar (AOSP RecycleListView). */
    public static class RecycleListView extends ListView {
        private final int mPaddingTopNoTitle;
        private final int mPaddingBottomNoButtons;
        boolean mRecycleOnMeasure = true;

        public RecycleListView(Context context) { this(context, null); }

        public RecycleListView(Context context, AttributeSet attrs) {
            super(context, attrs);
            final TypedArray ta = context.obtainStyledAttributes(attrs,
                    InternalRes.attrs("paddingBottomNoButtons", "paddingTopNoTitle"));
            mPaddingBottomNoButtons = ta.getDimensionPixelOffset(0, -1);
            mPaddingTopNoTitle = ta.getDimensionPixelOffset(1, -1);
            ta.recycle();
        }

        public void setHasDecor(boolean hasTitle, boolean hasButtons) {
            if (!hasButtons || !hasTitle) {
                final int paddingLeft = getPaddingLeft();
                final int paddingTop = hasTitle || mPaddingTopNoTitle < 0 ? getPaddingTop() : mPaddingTopNoTitle;
                final int paddingRight = getPaddingRight();
                final int paddingBottom = hasButtons || mPaddingBottomNoButtons < 0 ? getPaddingBottom()
                        : mPaddingBottomNoButtons;
                setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
            }
        }
    }

    /** Builder state applied to an AlertController (AOSP AlertController.AlertParams). */
    public static class AlertParams {
        public final Context mContext;
        public final LayoutInflater mInflater;
        public int mIconId = 0;
        public Drawable mIcon;
        public int mIconAttrId = 0;
        public CharSequence mTitle;
        public View mCustomTitleView;
        public CharSequence mMessage;
        public CharSequence mPositiveButtonText;
        public DialogInterface.OnClickListener mPositiveButtonListener;
        public CharSequence mNegativeButtonText;
        public DialogInterface.OnClickListener mNegativeButtonListener;
        public CharSequence mNeutralButtonText;
        public DialogInterface.OnClickListener mNeutralButtonListener;
        public boolean mCancelable;
        public DialogInterface.OnCancelListener mOnCancelListener;
        public DialogInterface.OnDismissListener mOnDismissListener;
        public DialogInterface.OnKeyListener mOnKeyListener;
        public CharSequence[] mItems;
        public ListAdapter mAdapter;
        public DialogInterface.OnClickListener mOnClickListener;
        public int mViewLayoutResId;
        public View mView;
        public int mViewSpacingLeft;
        public int mViewSpacingTop;
        public int mViewSpacingRight;
        public int mViewSpacingBottom;
        public boolean mViewSpacingSpecified = false;
        public boolean[] mCheckedItems;
        public boolean mIsMultiChoice;
        public boolean mIsSingleChoice;
        public int mCheckedItem = -1;
        public DialogInterface.OnMultiChoiceClickListener mOnCheckboxClickListener;
        public AdapterView.OnItemSelectedListener mOnItemSelectedListener;
        public OnPrepareListViewListener mOnPrepareListViewListener;
        public boolean mRecycleOnMeasure = true;

        public interface OnPrepareListViewListener {
            void onPrepareListView(ListView listView);
        }

        public AlertParams(Context context) {
            mContext = context;
            mCancelable = true;
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        public void apply(AlertController dialog) {
            if (mCustomTitleView != null) {
                dialog.setCustomTitle(mCustomTitleView);
            } else {
                if (mTitle != null) dialog.setTitle(mTitle);
                if (mIcon != null) dialog.setIcon(mIcon);
                if (mIconId != 0) dialog.setIcon(mIconId);
                if (mIconAttrId != 0) dialog.setIcon(dialog.getIconAttributeResId(mIconAttrId));
            }
            if (mMessage != null) dialog.setMessage(mMessage);
            if (mPositiveButtonText != null) {
                dialog.setButton(DialogInterface.BUTTON_POSITIVE, mPositiveButtonText, mPositiveButtonListener, null);
            }
            if (mNegativeButtonText != null) {
                dialog.setButton(DialogInterface.BUTTON_NEGATIVE, mNegativeButtonText, mNegativeButtonListener, null);
            }
            if (mNeutralButtonText != null) {
                dialog.setButton(DialogInterface.BUTTON_NEUTRAL, mNeutralButtonText, mNeutralButtonListener, null);
            }
            if (mItems != null || mAdapter != null) createListView(dialog);
            if (mView != null) {
                if (mViewSpacingSpecified) {
                    dialog.setView(mView, mViewSpacingLeft, mViewSpacingTop, mViewSpacingRight, mViewSpacingBottom);
                } else {
                    dialog.setView(mView);
                }
            } else if (mViewLayoutResId != 0) {
                dialog.setView(mViewLayoutResId);
            }
        }

        private void createListView(final AlertController dialog) {
            final RecycleListView listView = (RecycleListView) mInflater.inflate(dialog.mListLayout, null);
            final ListAdapter adapter;
            if (mIsMultiChoice) {
                adapter = new ArrayAdapter<CharSequence>(mContext, dialog.mMultiChoiceItemLayout, android.R.id.text1,
                        mItems) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View view = super.getView(position, convertView, parent);
                        if (mCheckedItems != null) {
                            boolean isItemChecked = mCheckedItems[position];
                            if (isItemChecked) listView.setItemChecked(position, true);
                        }
                        return view;
                    }
                };
            } else {
                final int layout = mIsSingleChoice ? dialog.mSingleChoiceItemLayout : dialog.mListItemLayout;
                if (mAdapter != null) adapter = mAdapter;
                else adapter = new CheckedItemAdapter(mContext, layout, android.R.id.text1, mItems);
            }
            if (mOnPrepareListViewListener != null) mOnPrepareListViewListener.onPrepareListView(listView);
            dialog.mAdapter = adapter;
            dialog.mCheckedItem = mCheckedItem;
            if (mOnClickListener != null) {
                listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                        mOnClickListener.onClick(dialog.mDialogInterface, position);
                        if (!mIsSingleChoice) dialog.mDialogInterface.dismiss();
                    }
                });
            } else if (mOnCheckboxClickListener != null) {
                listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                        if (mCheckedItems != null) mCheckedItems[position] = listView.isItemChecked(position);
                        mOnCheckboxClickListener.onClick(dialog.mDialogInterface, position,
                                listView.isItemChecked(position));
                    }
                });
            }
            if (mOnItemSelectedListener != null) listView.setOnItemSelectedListener(mOnItemSelectedListener);
            if (mIsSingleChoice) listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            else if (mIsMultiChoice) listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
            listView.mRecycleOnMeasure = mRecycleOnMeasure;
            dialog.mListView = listView;
        }
    }

    private static class CheckedItemAdapter extends ArrayAdapter<CharSequence> {
        public CheckedItemAdapter(Context context, int resource, int textViewResourceId, CharSequence[] objects) {
            super(context, resource, textViewResourceId, objects);
        }

        @Override
        public boolean hasStableIds() { return true; }

        @Override
        public long getItemId(int position) { return position; }
    }
}
