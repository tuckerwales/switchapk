package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristic;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.AllCapsTransformationMethod;
import android.text.method.DigitsKeyListener;
import android.text.method.KeyListener;
import android.text.method.LinkMovementMethod;
import android.text.method.MetaKeyKeyListener;
import android.text.method.MovementMethod;
import android.text.method.PasswordTransformationMethod;
import android.text.method.SingleLineTransformationMethod;
import android.text.method.TextKeyListener;
import android.text.method.TransformationMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.style.UpdateAppearance;
import android.text.style.UpdateLayout;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Displays and, when editable, edits text (port of AOSP TextView for software
 * rendering). Wrapping, spans, ellipsize, gravity and compound drawables go
 * through {@link Layout}. Marquee scrolling and uniform auto-size are recorded
 * but not animated or fitted yet.
 *
 * {@code mCursorDrawableRes} keeps its AOSP name: AppCompat reads it by reflection.
 */
public class TextView extends View implements ViewTreeObserver.OnPreDrawListener {
    public static final int AUTO_SIZE_TEXT_TYPE_NONE = 0;
    public static final int AUTO_SIZE_TEXT_TYPE_UNIFORM = 1;
    public static final int FOCUSED_SEARCH_RESULT_INDEX_NONE = -1;

    public enum BufferType { NORMAL, SPANNABLE, EDITABLE }

    public interface OnEditorActionListener {
        boolean onEditorAction(TextView v, int actionId, KeyEvent event);
    }

    private static final int LINES = 1;
    private static final int PIXELS = 2;
    private static final int EMS = 3;

    private static final int[] ATTRS = {
        android.R.attr.textAppearance, android.R.attr.textSize, android.R.attr.textColor,
        android.R.attr.textColorHint, android.R.attr.textColorHighlight, android.R.attr.textColorLink,
        android.R.attr.textStyle, android.R.attr.typeface, android.R.attr.fontFamily, android.R.attr.gravity,
        android.R.attr.ellipsize, android.R.attr.maxLines, android.R.attr.minLines, android.R.attr.lines,
        android.R.attr.maxEms, android.R.attr.minEms, android.R.attr.ems, android.R.attr.maxWidth,
        android.R.attr.minWidth, android.R.attr.singleLine, android.R.attr.lineSpacingExtra,
        android.R.attr.lineSpacingMultiplier, android.R.attr.includeFontPadding, android.R.attr.textScaleX,
        android.R.attr.letterSpacing, android.R.attr.textAllCaps, android.R.attr.password,
        android.R.attr.inputType, android.R.attr.imeOptions, android.R.attr.maxLength,
        android.R.attr.cursorVisible, android.R.attr.textIsSelectable, android.R.attr.scrollHorizontally,
        android.R.attr.shadowColor, android.R.attr.shadowDx, android.R.attr.shadowDy, android.R.attr.shadowRadius,
        android.R.attr.drawableLeft, android.R.attr.drawableTop, android.R.attr.drawableRight,
        android.R.attr.drawableBottom, android.R.attr.drawableStart, android.R.attr.drawableEnd,
        android.R.attr.drawablePadding, android.R.attr.freezesText, android.R.attr.editable,
        android.R.attr.digits, android.R.attr.numeric, android.R.attr.phoneNumber, android.R.attr.autoLink,
        android.R.attr.linksClickable, android.R.attr.breakStrategy, android.R.attr.hyphenationFrequency,
        android.R.attr.justificationMode, android.R.attr.elegantTextHeight, android.R.attr.fontFeatureSettings,
        android.R.attr.lineHeight, android.R.attr.firstBaselineToTopHeight,
        android.R.attr.lastBaselineToBottomHeight, android.R.attr.fallbackLineSpacing,
        android.R.attr.textCursorDrawable, android.R.attr.bufferType, android.R.attr.text, android.R.attr.hint,
        android.R.attr.selectAllOnFocus, android.R.attr.maxHeight,
    };

    private static final int[] APPEARANCE = {
        android.R.attr.textSize, android.R.attr.typeface, android.R.attr.textStyle, android.R.attr.textColor,
        android.R.attr.textColorLink, android.R.attr.fontFamily, android.R.attr.shadowColor,
        android.R.attr.shadowDx, android.R.attr.shadowDy, android.R.attr.shadowRadius,
        android.R.attr.elegantTextHeight, android.R.attr.letterSpacing, android.R.attr.fontFeatureSettings,
        android.R.attr.fontVariationSettings, android.R.attr.textFontWeight, android.R.attr.textLocale,
    };

    private CharSequence mText = "";
    private CharSequence mTransformed = "";
    private BufferType mBufferType = BufferType.NORMAL;
    private CharSequence mHint;
    private final TextPaint mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Layout mLayout;
    private Layout mHintLayout;
    private int mContentWidth = -1;
    private TransformationMethod mTransformation;
    private MovementMethod mMovement;
    private KeyListener mKeyListener;
    private ArrayList<TextWatcher> mListeners;
    private ChangeWatcher mChangeWatcher;
    private Editable.Factory mEditableFactory = Editable.Factory.getInstance();
    private Spannable.Factory mSpannableFactory = Spannable.Factory.getInstance();
    private InputFilter[] mFilters = new InputFilter[0];

    private ColorStateList mTextColor = ColorStateList.valueOf(0xFF000000);
    private ColorStateList mHintColor;
    private ColorStateList mLinkColor;
    private int mCurTextColor = 0xFF000000;
    private int mCurHintTextColor = 0x80000000;
    private int mHighlightColor = 0x6633B5E5;
    private int[] mSearchResultHighlights;
    private int mFocusedSearchResultIndex = FOCUSED_SEARCH_RESULT_INDEX_NONE;
    private int mSearchResultHighlightColor = 0xFFFFFF00;
    private int mFocusedSearchResultHighlightColor = 0xFFFF9632;
    private int mGravity = Gravity.TOP | Gravity.START;
    private TextUtils.TruncateAt mEllipsize;
    private boolean mSingleLine;
    private boolean mHorizontallyScrolling;
    private boolean mIncludePad = true;
    private boolean mFallbackLineSpacing;
    private boolean mCursorVisible = true;
    private boolean mSelectAllOnFocus;
    private boolean mTextIsSelectable;
    private boolean mFreezesText;
    private boolean mLinksClickable = true;
    private boolean mAllCaps;
    private float mSpacingMult = 1.0f;
    private float mSpacingAdd;
    private int mMaximum = Integer.MAX_VALUE;
    private int mMinimum;
    private int mMaxMode = LINES;
    private int mMinMode = LINES;
    private int mMaxWidth = Integer.MAX_VALUE;
    private int mMinWidth;
    private int mMaxWidthMode = PIXELS;
    private int mMinWidthMode = PIXELS;
    private int mAutoLinkMask;
    private int mInputType = InputType.TYPE_NULL;
    private int mImeOptions;
    private CharSequence mImeActionLabel;
    private int mImeActionId;
    private String mPrivateImeOptions;
    private Bundle mInputExtras;
    private LocaleList mImeHintLocales;
    private OnEditorActionListener mEditorActionListener;
    private CharSequence mError;
    private int mBreakStrategy;
    private int mHyphenationFrequency;
    private int mJustificationMode;
    private int mTextSizeUnit = TypedValue.COMPLEX_UNIT_SP;
    private float mShadowRadius;
    private float mShadowDx;
    private float mShadowDy;
    private int mShadowColor;
    private int mAutoSizeType = AUTO_SIZE_TEXT_TYPE_NONE;
    private int mAutoSizeMin;
    private int mAutoSizeMax;
    private int mAutoSizeGranularity;
    private int mMarqueeRepeatLimit = 3;
    private boolean mUseBoundsForWidth;
    private boolean mShiftDrawingOffsetForStartOverhang;
    private Paint.FontMetrics mMinimumFontMetrics;
    private final Path mHighlightPath = new Path();
    private Paint mHighlightPaint;

    // AppCompat reads this resource id by reflection.
    int mCursorDrawableRes;
    int mTextSelectHandleLeftRes;
    int mTextSelectHandleRightRes;
    int mTextSelectHandleRes;

    private Drawable mDrawableLeft;
    private Drawable mDrawableTop;
    private Drawable mDrawableRight;
    private Drawable mDrawableBottom;
    private Drawable mDrawableStart;
    private Drawable mDrawableEnd;
    private Drawable mResolvedLeft;
    private Drawable mResolvedRight;
    private boolean mUseRelative;
    private int mDrawablePadding;
    private ColorStateList mDrawableTint;
    private android.graphics.PorterDuff.Mode mDrawableTintMode;
    private android.graphics.BlendMode mDrawableBlendMode;

    public TextView(Context context) { this(context, null); }

    public TextView(Context context, AttributeSet attrs) { this(context, attrs, android.R.attr.textViewStyle); }

    public TextView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TextView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mTextPaint.density = getResources().getDisplayMetrics().density;
        mTextPaint.setColor(mCurTextColor);
        boolean sawTextSize = false;
        BufferType buffer = BufferType.NORMAL;
        CharSequence text = "";
        boolean haveText = false;
        CharSequence hint = null;
        boolean allCaps = false;
        boolean password = false;
        boolean singleLine = false;
        int inputType = -1;
        int numeric = 0;
        boolean phone = false;
        String digits = null;
        int maxLength = -1;
        int appearance = 0;
        if (attrs != null || defStyleAttr != 0 || defStyleRes != 0) {
            TypedArray a = context.obtainStyledAttributes(attrs, ATTRS, defStyleAttr, defStyleRes);
            appearance = a.getResourceId(0, 0);
            a.recycle();
        }
        if (appearance != 0) sawTextSize |= applyAppearance(appearance);
        if (attrs != null || defStyleAttr != 0 || defStyleRes != 0) {
            TypedArray a = context.obtainStyledAttributes(attrs, ATTRS, defStyleAttr, defStyleRes);
            if (a.hasValue(1)) {
                setRawTextSize(a.getDimensionPixelSize(1, (int) mTextPaint.getTextSize()));
                sawTextSize = true;
            }
            if (a.hasValue(2)) setTextColor(a.getColorStateList(2));
            if (a.hasValue(3)) setHintTextColor(a.getColorStateList(3));
            if (a.hasValue(4)) setHighlightColor(a.getColor(4, mHighlightColor));
            if (a.hasValue(5)) setLinkTextColor(a.getColorStateList(5));
            applyTypeface(a.getString(8), a.hasValue(7) ? a.getInt(7, 0) : -1, a.hasValue(6) ? a.getInt(6, 0) : -1);
            if (a.hasValue(9)) setGravity(a.getInt(9, mGravity));
            if (a.hasValue(10)) setEllipsize(ellipsizeFromInt(a.getInt(10, 0)));
            if (a.hasValue(11)) setMaxLines(a.getInt(11, Integer.MAX_VALUE));
            if (a.hasValue(12)) setMinLines(a.getInt(12, 0));
            if (a.hasValue(13)) setLines(a.getInt(13, 1));
            if (a.hasValue(14)) setMaxEms(a.getInt(14, Integer.MAX_VALUE));
            if (a.hasValue(15)) setMinEms(a.getInt(15, 0));
            if (a.hasValue(16)) setEms(a.getInt(16, 0));
            if (a.hasValue(17)) setMaxWidth(a.getDimensionPixelSize(17, Integer.MAX_VALUE));
            if (a.hasValue(18)) setMinWidth(a.getDimensionPixelSize(18, 0));
            if (a.hasValue(ATTRS.length - 1)) setMaxHeight(a.getDimensionPixelSize(ATTRS.length - 1, Integer.MAX_VALUE));
            singleLine = a.getBoolean(19, false);
            if (a.hasValue(20) || a.hasValue(21)) {
                setLineSpacing(a.getDimension(20, 0f), a.getFloat(21, 1f));
            }
            if (a.hasValue(22)) setIncludeFontPadding(a.getBoolean(22, true));
            if (a.hasValue(23)) setTextScaleX(a.getFloat(23, 1f));
            if (a.hasValue(24)) setLetterSpacing(a.getFloat(24, 0f));
            allCaps = a.getBoolean(25, false);
            password = a.getBoolean(26, false);
            if (a.hasValue(27)) inputType = a.getInt(27, 0);
            if (a.hasValue(28)) setImeOptions(a.getInt(28, 0));
            if (a.hasValue(29)) maxLength = a.getInt(29, -1);
            if (a.hasValue(30)) setCursorVisible(a.getBoolean(30, true));
            if (a.getBoolean(31, false)) setTextIsSelectable(true);
            if (a.getBoolean(32, false)) setHorizontallyScrolling(true);
            if (a.hasValue(33) || a.hasValue(36)) {
                setShadowLayer(a.getDimension(36, 0f), a.getDimension(34, 0f), a.getDimension(35, 0f),
                        a.getColor(33, 0));
            }
            Drawable left = a.getDrawable(37);
            Drawable top = a.getDrawable(38);
            Drawable right = a.getDrawable(39);
            Drawable bottom = a.getDrawable(40);
            Drawable start = a.getDrawable(41);
            Drawable end = a.getDrawable(42);
            if (a.hasValue(43)) setCompoundDrawablePadding(a.getDimensionPixelSize(43, 0));
            if (start != null || end != null) setCompoundDrawablesRelative(start, top, end, bottom);
            else if (left != null || top != null || right != null || bottom != null) {
                setCompoundDrawables(left, top, right, bottom);
            }
            if (a.hasValue(44)) setFreezesText(a.getBoolean(44, false));
            boolean editable = a.getBoolean(45, false);
            digits = a.getString(46);
            if (a.hasValue(47)) numeric = a.getInt(47, 0);
            phone = a.getBoolean(48, false);
            if (a.hasValue(49)) setAutoLinkMask(a.getInt(49, 0));
            if (a.hasValue(50)) setLinksClickable(a.getBoolean(50, true));
            if (a.hasValue(51)) setBreakStrategy(a.getInt(51, 0));
            if (a.hasValue(52)) setHyphenationFrequency(a.getInt(52, 0));
            if (a.hasValue(53)) setJustificationMode(a.getInt(53, 0));
            if (a.hasValue(54)) setElegantTextHeight(a.getBoolean(54, false));
            if (a.hasValue(55)) setFontFeatureSettings(a.getString(55));
            if (a.hasValue(56)) setLineHeight(a.getDimensionPixelSize(56, 0));
            if (a.hasValue(57)) setFirstBaselineToTopHeight(a.getDimensionPixelSize(57, 0));
            if (a.hasValue(58)) setLastBaselineToBottomHeight(a.getDimensionPixelSize(58, 0));
            if (a.hasValue(59)) setFallbackLineSpacing(a.getBoolean(59, false));
            if (a.hasValue(60)) mCursorDrawableRes = a.getResourceId(60, 0);
            if (a.hasValue(61)) {
                int bt = a.getInt(61, 0);
                if (bt == 1) buffer = BufferType.SPANNABLE;
                else if (bt == 2) buffer = BufferType.EDITABLE;
            } else if (getDefaultEditable()) {
                buffer = BufferType.EDITABLE;
            }
            if (editable) buffer = BufferType.EDITABLE;
            if (a.hasValue(62)) {
                text = a.getText(62);
                if (text == null) text = "";
                haveText = true;
            }
            if (a.hasValue(63)) hint = a.getText(63);
            if (a.hasValue(64)) setSelectAllOnFocus(a.getBoolean(64, false));
            a.recycle();
        }
        if (!sawTextSize) setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        if (singleLine) setSingleLine(true);
        if (allCaps) setAllCaps(true);
        if (password) setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (phone) setInputType(InputType.TYPE_CLASS_PHONE);
        if (numeric != 0) {
            int ntype = InputType.TYPE_CLASS_NUMBER;
            if ((numeric & 0x2) != 0) ntype |= InputType.TYPE_NUMBER_FLAG_SIGNED;
            if ((numeric & 0x4) != 0) ntype |= InputType.TYPE_NUMBER_FLAG_DECIMAL;
            setInputType(ntype);
        }
        if (inputType >= 0) setInputType(inputType);
        ArrayList<InputFilter> filters = new ArrayList<InputFilter>();
        if (digits != null) filters.add(new DigitsFilter(digits));
        if (maxLength >= 0) filters.add(new InputFilter.LengthFilter(maxLength));
        if (!filters.isEmpty()) setFilters(filters.toArray(new InputFilter[filters.size()]));
        if (haveText || buffer != BufferType.NORMAL) setText(text, buffer);
        if (hint != null) setHint(hint);
        if (mMovement == null) {
            MovementMethod movement = getDefaultMovementMethod();
            if (movement != null) setMovementMethod(movement);
        }
        if (mKeyListener == null && (mBufferType == BufferType.EDITABLE || mInputType != InputType.TYPE_NULL)) {
            setKeyListener(defaultKeyListener());
        }
        applyEditorFocus();
        updateTextColors();
    }

    private KeyListener defaultKeyListener() {
        int cls = mInputType & InputType.TYPE_MASK_CLASS;
        if (cls == InputType.TYPE_CLASS_NUMBER) {
            return DigitsKeyListener.getInstance((mInputType & InputType.TYPE_NUMBER_FLAG_SIGNED) != 0,
                    (mInputType & InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0);
        }
        if (cls == InputType.TYPE_CLASS_PHONE) return DigitsKeyListener.getInstance();
        return TextKeyListener.getInstance();
    }

    private void applyEditorFocus() {
        if (mKeyListener != null || mMovement != null) {
            setFocusable(true);
            setClickable(true);
            setLongClickable(true);
        }
        if (onCheckIsTextEditor()) setFocusableInTouchMode(true);
    }

    private boolean applyAppearance(int resId) {
        TypedArray a = getContext().obtainStyledAttributes(resId, APPEARANCE);
        boolean sawSize = false;
        if (a.hasValue(0)) {
            setRawTextSize(a.getDimensionPixelSize(0, (int) mTextPaint.getTextSize()));
            sawSize = true;
        }
        applyTypeface(a.getString(5), a.hasValue(1) ? a.getInt(1, 0) : -1, a.hasValue(2) ? a.getInt(2, 0) : -1);
        if (a.hasValue(3)) setTextColor(a.getColorStateList(3));
        if (a.hasValue(4)) setLinkTextColor(a.getColorStateList(4));
        if (a.hasValue(6) || a.hasValue(9)) {
            setShadowLayer(a.getFloat(9, 0f), a.getFloat(7, 0f), a.getFloat(8, 0f), a.getColor(6, 0));
        }
        if (a.hasValue(10)) setElegantTextHeight(a.getBoolean(10, false));
        if (a.hasValue(11)) setLetterSpacing(a.getFloat(11, 0f));
        if (a.hasValue(12)) setFontFeatureSettings(a.getString(12));
        if (a.hasValue(13)) setFontVariationSettings(a.getString(13));
        if (a.hasValue(14)) {
            int weight = a.getInt(14, -1);
            Typeface tf = mTextPaint.getTypeface();
            boolean italic = tf != null && tf.isItalic();
            setTypeface(Typeface.create(tf, weight, italic));
        }
        if (a.hasValue(15)) {
            String locale = a.getString(15);
            if (locale != null) setTextLocales(LocaleList.forLanguageTags(locale));
        }
        a.recycle();
        return sawSize;
    }

    private void applyTypeface(String family, int typefaceIndex, int styleIndex) {
        if (family == null && typefaceIndex < 0 && styleIndex < 0) return;
        Typeface tf = null;
        if (family != null) tf = Typeface.create(family, Typeface.NORMAL);
        if (tf == null) {
            switch (typefaceIndex) {
                case 1: tf = Typeface.SANS_SERIF; break;
                case 2: tf = Typeface.SERIF; break;
                case 3: tf = Typeface.MONOSPACE; break;
                default: tf = mTextPaint.getTypeface(); break;
            }
        }
        int style = styleIndex >= 0 ? styleIndex : (tf != null ? tf.getStyle() : Typeface.NORMAL);
        setTypeface(tf, style);
    }

    private static TextUtils.TruncateAt ellipsizeFromInt(int value) {
        switch (value) {
            case 1: return TextUtils.TruncateAt.START;
            case 2: return TextUtils.TruncateAt.MIDDLE;
            case 3: return TextUtils.TruncateAt.END;
            case 4: return TextUtils.TruncateAt.MARQUEE;
            default: return null;
        }
    }

    // ---- text content ----

    public CharSequence getText() { return mText; }

    public int length() { return mText.length(); }

    public Editable getEditableText() { return mText instanceof Editable ? (Editable) mText : null; }

    public final void setText(CharSequence text) { setText(text, mBufferType); }

    public final void setTextKeepState(CharSequence text) { setText(text, mBufferType, true); }

    public void setText(CharSequence text, BufferType type) { setText(text, type, false); }

    public final void setTextKeepState(CharSequence text, BufferType type) { setText(text, type, true); }

    private void setText(CharSequence text, BufferType type, boolean keepState) {
        if (text == null) text = "";
        if (type == null) type = BufferType.NORMAL;
        if (!keepState && type == mBufferType && text.equals(mText)
                && !(text instanceof Spanned) && !(mText instanceof Spanned)) {
            return;
        }
        int oldLen = mText.length();
        int selStart = keepState ? getSelectionStart() : -1;
        int selEnd = keepState ? getSelectionEnd() : -1;
        if (mText instanceof Spannable && mChangeWatcher != null) ((Spannable) mText).removeSpan(mChangeWatcher);
        sendBeforeTextChanged(mText, 0, oldLen, text.length());

        if (type == BufferType.EDITABLE || text instanceof Editable) {
            Editable e = mEditableFactory.newEditable("");
            if (mFilters != null) e.setFilters(mFilters);
            e.replace(0, 0, text);
            text = e;
            type = BufferType.EDITABLE;
        } else if (type == BufferType.SPANNABLE || text instanceof Spannable) {
            text = mSpannableFactory.newSpannable(text);
            type = BufferType.SPANNABLE;
        } else {
            text = TextUtils.stringOrSpannedString(text);
            type = BufferType.NORMAL;
        }
        mBufferType = type;
        mText = text;
        updateTransformed();
        applyAutoLinks();
        attachWatcher();
        if (mText instanceof Spannable) {
            Spannable sp = (Spannable) mText;
            if (keepState && selStart >= 0) {
                int len = sp.length();
                int s = Math.max(0, Math.min(selStart, len));
                int e = Math.max(0, Math.min(selEnd, len));
                Selection.setSelection(sp, s, e);
            } else if (type == BufferType.EDITABLE) {
                Selection.setSelection(sp, sp.length());
            }
        }
        sendOnTextChanged(mText, 0, oldLen, mText.length());
        if (mText instanceof Editable) sendAfterTextChanged((Editable) mText);
        relayout();
    }

    public final void setText(char[] text, int start, int len) { setText(new String(text, start, len)); }

    public final void setText(int resid) { setText(getResources().getText(resid)); }

    public final void setText(int resid, BufferType type) { setText(getResources().getText(resid), type); }

    public final void append(CharSequence text) {
        if (text == null) return;
        append(text, 0, text.length());
    }

    public void append(CharSequence text, int start, int end) {
        if (!(mText instanceof Editable)) setText(mText, BufferType.EDITABLE);
        ((Editable) mText).append(text, start, end);
    }

    public final void setHint(CharSequence hint) {
        mHint = hint;
        if (mContentWidth >= 0) makeNewLayout(mContentWidth);
        invalidate();
    }

    public final void setHint(int resid) { setHint(getResources().getText(resid)); }

    public CharSequence getHint() { return mHint; }

    public final void setEditableFactory(Editable.Factory factory) { mEditableFactory = factory; }

    public final void setSpannableFactory(Spannable.Factory factory) { mSpannableFactory = factory; }

    private void updateTransformed() {
        if (mTransformation == null) mTransformed = mText;
        else {
            CharSequence t = mTransformation.getTransformation(mText, this);
            mTransformed = t != null ? t : mText;
        }
    }

    private void attachWatcher() {
        if (!(mText instanceof Spannable)) return;
        Spannable sp = (Spannable) mText;
        if (mChangeWatcher == null) mChangeWatcher = new ChangeWatcher();
        sp.removeSpan(mChangeWatcher);
        int flags = Spanned.SPAN_INCLUSIVE_INCLUSIVE | (100 << Spanned.SPAN_PRIORITY_SHIFT);
        sp.setSpan(mChangeWatcher, 0, sp.length(), flags);
        if (mTransformation instanceof TextWatcher) {
            sp.setSpan(mTransformation, 0, sp.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }
    }

    private void sendBeforeTextChanged(CharSequence s, int start, int before, int after) {
        if (mListeners == null) return;
        ArrayList<TextWatcher> list = new ArrayList<TextWatcher>(mListeners);
        for (int i = 0; i < list.size(); i++) list.get(i).beforeTextChanged(s, start, before, after);
    }

    private void sendOnTextChanged(CharSequence s, int start, int before, int after) {
        onTextChanged(s, start, before, after);
        if (mListeners == null) return;
        ArrayList<TextWatcher> list = new ArrayList<TextWatcher>(mListeners);
        for (int i = 0; i < list.size(); i++) list.get(i).onTextChanged(s, start, before, after);
    }

    private void sendAfterTextChanged(Editable s) {
        if (mListeners == null) return;
        ArrayList<TextWatcher> list = new ArrayList<TextWatcher>(mListeners);
        for (int i = 0; i < list.size(); i++) list.get(i).afterTextChanged(s);
    }

    public void addTextChangedListener(TextWatcher watcher) {
        if (mListeners == null) mListeners = new ArrayList<TextWatcher>();
        mListeners.add(watcher);
    }

    public void removeTextChangedListener(TextWatcher watcher) {
        if (mListeners != null) mListeners.remove(watcher);
    }

    public void setFilters(InputFilter[] filters) {
        if (filters == null) throw new IllegalArgumentException();
        mFilters = filters;
        if (mText instanceof Editable) ((Editable) mText).setFilters(filters);
    }

    public InputFilter[] getFilters() { return mFilters; }

    // ---- appearance ----

    public TextPaint getPaint() { return mTextPaint; }

    public float getTextSize() { return mTextPaint.getTextSize(); }

    public int getTextSizeUnit() { return mTextSizeUnit; }

    public void setTextSize(float size) { setTextSize(TypedValue.COMPLEX_UNIT_SP, size); }

    public void setTextSize(int unit, float size) {
        mTextSizeUnit = unit;
        setRawTextSize(TypedValue.applyDimension(unit, size, getResources().getDisplayMetrics()));
    }

    private void setRawTextSize(float size) {
        if (size <= 0 || size == mTextPaint.getTextSize()) return;
        mTextPaint.setTextSize(size);
        relayout();
    }

    public void setTextColor(int color) { setTextColor(ColorStateList.valueOf(color)); }

    public void setTextColor(ColorStateList colors) {
        mTextColor = colors != null ? colors : ColorStateList.valueOf(0xFF000000);
        updateTextColors();
    }

    public final ColorStateList getTextColors() { return mTextColor; }

    public final int getCurrentTextColor() { return mCurTextColor; }

    public final void setHintTextColor(int color) { setHintTextColor(ColorStateList.valueOf(color)); }

    public final void setHintTextColor(ColorStateList colors) {
        mHintColor = colors;
        updateTextColors();
    }

    public final ColorStateList getHintTextColors() { return mHintColor; }

    public final int getCurrentHintTextColor() { return mCurHintTextColor; }

    public final void setLinkTextColor(int color) { setLinkTextColor(ColorStateList.valueOf(color)); }

    public final void setLinkTextColor(ColorStateList colors) {
        mLinkColor = colors;
        updateTextColors();
    }

    public final ColorStateList getLinkTextColors() { return mLinkColor; }

    public void setHighlightColor(int color) {
        if (mHighlightColor != color) {
            mHighlightColor = color;
            invalidate();
        }
    }

    public int getHighlightColor() { return mHighlightColor; }

    public void setTypeface(Typeface tf) { setTypeface(tf, tf != null ? tf.getStyle() : Typeface.NORMAL); }

    public void setTypeface(Typeface tf, int style) {
        if (style > 0) {
            if (tf == null) tf = Typeface.defaultFromStyle(style);
            else tf = Typeface.create(tf, style);
            int fake = style & ~tf.getStyle();
            mTextPaint.setFakeBoldText((fake & Typeface.BOLD) != 0);
            mTextPaint.setTextSkewX((fake & Typeface.ITALIC) != 0 ? -0.25f : 0f);
        } else {
            mTextPaint.setFakeBoldText(false);
            mTextPaint.setTextSkewX(0f);
        }
        mTextPaint.setTypeface(tf);
        relayout();
    }

    public Typeface getTypeface() { return mTextPaint.getTypeface(); }

    public float getTextScaleX() { return mTextPaint.getTextScaleX(); }

    public void setTextScaleX(float size) {
        if (size != mTextPaint.getTextScaleX()) {
            mTextPaint.setTextScaleX(size);
            relayout();
        }
    }

    public float getLetterSpacing() { return mTextPaint.getLetterSpacing(); }

    public void setLetterSpacing(float letterSpacing) {
        if (letterSpacing != mTextPaint.getLetterSpacing()) {
            mTextPaint.setLetterSpacing(letterSpacing);
            relayout();
        }
    }

    public void setElegantTextHeight(boolean elegant) { mTextPaint.setElegantTextHeight(elegant); }

    public boolean isElegantTextHeight() { return false; }

    public void setFallbackLineSpacing(boolean enabled) {
        if (mFallbackLineSpacing != enabled) {
            mFallbackLineSpacing = enabled;
            relayout();
        }
    }

    public boolean isFallbackLineSpacing() { return mFallbackLineSpacing; }

    public String getFontFeatureSettings() { return mTextPaint.getFontFeatureSettings(); }

    public void setFontFeatureSettings(String settings) { mTextPaint.setFontFeatureSettings(settings); }

    public String getFontVariationSettings() { return null; }

    public boolean setFontVariationSettings(String settings) { return false; }

    public Locale getTextLocale() {
        LocaleList list = mTextPaint.getTextLocales();
        return list != null && list.size() > 0 ? list.get(0) : Locale.getDefault();
    }

    public LocaleList getTextLocales() { return mTextPaint.getTextLocales(); }

    public void setTextLocale(Locale locale) { mTextPaint.setTextLocale(locale); }

    public void setTextLocales(LocaleList locales) { mTextPaint.setTextLocales(locales); }

    public void setShadowLayer(float radius, float dx, float dy, int color) {
        mShadowRadius = radius;
        mShadowDx = dx;
        mShadowDy = dy;
        mShadowColor = color;
        mTextPaint.setShadowLayer(radius, dx, dy, color);
        invalidate();
    }

    public float getShadowRadius() { return mShadowRadius; }

    public float getShadowDx() { return mShadowDx; }

    public float getShadowDy() { return mShadowDy; }

    public int getShadowColor() { return mShadowColor; }

    public int getPaintFlags() { return mTextPaint.getFlags(); }

    public void setPaintFlags(int flags) {
        if (mTextPaint.getFlags() != flags) {
            mTextPaint.setFlags(flags);
            relayout();
        }
    }

    public void setTextAppearance(int resId) { setTextAppearance(getContext(), resId); }

    public void setTextAppearance(Context context, int resId) {
        if (resId != 0) applyAppearance(resId);
    }

    public void setGravity(int gravity) {
        // Default to start/top only when the caller left that axis unspecified.
        // CENTER_HORIZONTAL and LEFT/RIGHT are absolute bits; adding START on
        // top of them would win in layoutAlignment and left-align centered text.
        if ((gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0
                && (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == 0) {
            gravity |= Gravity.START;
        }
        if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == 0) gravity |= Gravity.TOP;
        if (mGravity != gravity) {
            mGravity = gravity;
            relayout();
        }
    }

    public int getGravity() { return mGravity; }

    public void setLineSpacing(float add, float mult) {
        if (mSpacingAdd != add || mSpacingMult != mult) {
            mSpacingAdd = add;
            mSpacingMult = mult;
            relayout();
        }
    }

    public float getLineSpacingMultiplier() { return mSpacingMult; }

    public float getLineSpacingExtra() { return mSpacingAdd; }

    public int getLineHeight() {
        return Math.round(mTextPaint.getFontMetricsInt(null) * mSpacingMult + mSpacingAdd);
    }

    public void setLineHeight(int lineHeight) { setLineHeight(TypedValue.COMPLEX_UNIT_PX, lineHeight); }

    public void setLineHeight(int unit, float lineHeight) {
        int px = Math.round(TypedValue.applyDimension(unit, lineHeight, getResources().getDisplayMetrics()));
        int fontHeight = mTextPaint.getFontMetricsInt(null);
        if (px > fontHeight) setLineSpacing(px - fontHeight, 1f);
    }

    public void setIncludeFontPadding(boolean includepad) {
        if (mIncludePad != includepad) {
            mIncludePad = includepad;
            relayout();
        }
    }

    public boolean getIncludeFontPadding() { return mIncludePad; }

    private int fontTop() {
        Paint.FontMetricsInt fm = mTextPaint.getFontMetricsInt();
        return mIncludePad ? fm.top : fm.ascent;
    }

    private int fontBottom() {
        Paint.FontMetricsInt fm = mTextPaint.getFontMetricsInt();
        return mIncludePad ? fm.bottom : fm.descent;
    }

    public void setFirstBaselineToTopHeight(int firstBaselineToTopHeight) {
        int extra = firstBaselineToTopHeight + fontTop();
        if (extra < 0) extra = 0;
        setPadding(getPaddingLeft(), extra, getPaddingRight(), getPaddingBottom());
    }

    public void setLastBaselineToBottomHeight(int lastBaselineToBottomHeight) {
        int extra = lastBaselineToBottomHeight - fontBottom();
        if (extra < 0) extra = 0;
        setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), extra);
    }

    public int getFirstBaselineToTopHeight() { return getPaddingTop() - fontTop(); }

    public int getLastBaselineToBottomHeight() { return getPaddingBottom() + fontBottom(); }

    public void setBreakStrategy(int breakStrategy) {
        mBreakStrategy = breakStrategy;
        relayout();
    }

    public int getBreakStrategy() { return mBreakStrategy; }

    public void setHyphenationFrequency(int hyphenationFrequency) {
        mHyphenationFrequency = hyphenationFrequency;
        relayout();
    }

    public int getHyphenationFrequency() { return mHyphenationFrequency; }

    public void setJustificationMode(int justificationMode) {
        mJustificationMode = justificationMode;
        relayout();
    }

    public int getJustificationMode() { return mJustificationMode; }

    public void setLineBreakStyle(int lineBreakStyle) {}

    public void setLineBreakWordStyle(int lineBreakWordStyle) {}

    public int getLineBreakStyle() { return 0; }

    public int getLineBreakWordStyle() { return 0; }

    public void setUseBoundsForWidth(boolean useBoundsForWidth) {
        mUseBoundsForWidth = useBoundsForWidth;
        relayout();
    }

    public boolean getUseBoundsForWidth() { return mUseBoundsForWidth; }

    public void setShiftDrawingOffsetForStartOverhang(boolean shift) {
        mShiftDrawingOffsetForStartOverhang = shift;
        relayout();
    }

    public boolean getShiftDrawingOffsetForStartOverhang() { return mShiftDrawingOffsetForStartOverhang; }

    public void setMinimumFontMetrics(Paint.FontMetrics minimumFontMetrics) {
        mMinimumFontMetrics = minimumFontMetrics;
        relayout();
    }

    public Paint.FontMetrics getMinimumFontMetrics() { return mMinimumFontMetrics; }

    public boolean isLocalePreferredLineHeightForMinimumUsed() { return false; }

    public void setLocalePreferredLineHeightForMinimumUsed(boolean used) {}

    public void setAutoSizeTextTypeWithDefaults(int autoSizeTextType) { mAutoSizeType = autoSizeTextType; }

    public void setAutoSizeTextTypeUniformWithConfiguration(int min, int max, int granularity, int unit) {
        mAutoSizeType = AUTO_SIZE_TEXT_TYPE_UNIFORM;
        mAutoSizeMin = Math.round(TypedValue.applyDimension(unit, min, getResources().getDisplayMetrics()));
        mAutoSizeMax = Math.round(TypedValue.applyDimension(unit, max, getResources().getDisplayMetrics()));
        mAutoSizeGranularity = Math.round(TypedValue.applyDimension(unit, granularity,
                getResources().getDisplayMetrics()));
    }

    public void setAutoSizeTextTypeUniformWithPresetSizes(int[] presetSizes, int unit) {
        mAutoSizeType = AUTO_SIZE_TEXT_TYPE_UNIFORM;
    }

    public int getAutoSizeTextType() { return mAutoSizeType; }

    public int getAutoSizeStepGranularity() { return mAutoSizeGranularity; }

    public int getAutoSizeMinTextSize() { return mAutoSizeMin; }

    public int getAutoSizeMaxTextSize() { return mAutoSizeMax; }

    public int[] getAutoSizeTextAvailableSizes() { return mAutoSizeType == AUTO_SIZE_TEXT_TYPE_NONE ? null : new int[0]; }

    // ---- limits ----

    public void setMinLines(int minLines) {
        mMinimum = minLines;
        mMinMode = LINES;
        requestLayout();
        invalidate();
    }

    public int getMinLines() { return mMinMode == LINES ? mMinimum : -1; }

    public void setMaxLines(int maxLines) {
        mMaximum = maxLines;
        mMaxMode = LINES;
        relayout();
    }

    public int getMaxLines() { return mMaxMode == LINES ? mMaximum : -1; }

    public void setLines(int lines) {
        setMinLines(lines);
        setMaxLines(lines);
    }

    public void setMinHeight(int minPixels) {
        mMinimum = minPixels;
        mMinMode = PIXELS;
        requestLayout();
        invalidate();
    }

    public int getMinHeight() { return mMinMode == PIXELS ? mMinimum : -1; }

    public void setMaxHeight(int maxPixels) {
        mMaximum = maxPixels;
        mMaxMode = PIXELS;
        requestLayout();
        invalidate();
    }

    public int getMaxHeight() { return mMaxMode == PIXELS ? mMaximum : -1; }

    public void setHeight(int pixels) {
        mMinimum = mMaximum = pixels;
        mMinMode = mMaxMode = PIXELS;
        requestLayout();
        invalidate();
    }

    public void setMinEms(int minEms) {
        mMinWidth = minEms;
        mMinWidthMode = EMS;
        requestLayout();
        invalidate();
    }

    public int getMinEms() { return mMinWidthMode == EMS ? mMinWidth : -1; }

    public void setMaxEms(int maxEms) {
        mMaxWidth = maxEms;
        mMaxWidthMode = EMS;
        requestLayout();
        invalidate();
    }

    public int getMaxEms() { return mMaxWidthMode == EMS ? mMaxWidth : -1; }

    public void setEms(int ems) {
        mMinWidth = mMaxWidth = ems;
        mMinWidthMode = mMaxWidthMode = EMS;
        requestLayout();
        invalidate();
    }

    public void setMinWidth(int minPixels) {
        mMinWidth = minPixels;
        mMinWidthMode = PIXELS;
        requestLayout();
        invalidate();
    }

    public int getMinWidth() { return mMinWidthMode == PIXELS ? mMinWidth : -1; }

    public void setMaxWidth(int maxPixels) {
        mMaxWidth = maxPixels;
        mMaxWidthMode = PIXELS;
        requestLayout();
        invalidate();
    }

    public int getMaxWidth() { return mMaxWidthMode == PIXELS ? mMaxWidth : -1; }

    public void setWidth(int pixels) {
        mMinWidth = mMaxWidth = pixels;
        mMinWidthMode = mMaxWidthMode = PIXELS;
        requestLayout();
        invalidate();
    }

    public boolean isSingleLine() { return mSingleLine; }

    public void setSingleLine() { setSingleLine(true); }

    public void setSingleLine(boolean singleLine) {
        mSingleLine = singleLine;
        if (singleLine) {
            setLines(1);
            if (mTransformation == null || mTransformation instanceof SingleLineTransformationMethod) {
                setTransformationMethod(SingleLineTransformationMethod.getInstance());
            }
        } else {
            if (mTransformation instanceof SingleLineTransformationMethod) setTransformationMethod(null);
            setMaxLines(Integer.MAX_VALUE);
        }
    }

    public void setEllipsize(TextUtils.TruncateAt where) {
        if (mEllipsize != where) {
            mEllipsize = where;
            relayout();
        }
    }

    public TextUtils.TruncateAt getEllipsize() { return mEllipsize; }

    public void setMarqueeRepeatLimit(int marqueeLimit) { mMarqueeRepeatLimit = marqueeLimit; }

    public int getMarqueeRepeatLimit() { return mMarqueeRepeatLimit; }

    public void setHorizontallyScrolling(boolean whether) {
        if (mHorizontallyScrolling != whether) {
            mHorizontallyScrolling = whether;
            relayout();
        }
    }

    public final boolean isHorizontallyScrollable() { return mHorizontallyScrolling; }

    public final boolean getHorizontallyScrolling() { return mHorizontallyScrolling; }

    public void setAllCaps(boolean allCaps) {
        mAllCaps = allCaps;
        if (allCaps) setTransformationMethod(new AllCapsTransformationMethod(getContext()));
        else if (mTransformation instanceof AllCapsTransformationMethod) setTransformationMethod(null);
    }

    public boolean isAllCaps() { return mAllCaps; }

    // ---- movement, input, selection ----

    public final KeyListener getKeyListener() { return mKeyListener; }

    public void setKeyListener(KeyListener input) {
        mKeyListener = input;
        if (input != null) {
            if (mInputType == InputType.TYPE_NULL) mInputType = input.getInputType();
            if (!(mText instanceof Editable)) setText(mText, BufferType.EDITABLE);
            applyEditorFocus();
        }
    }

    public final MovementMethod getMovementMethod() { return mMovement; }

    public final void setMovementMethod(MovementMethod movement) {
        if (mMovement == movement) return;
        mMovement = movement;
        if (movement != null && !(mText instanceof Spannable)) setText(mText, BufferType.SPANNABLE);
        if (movement != null && mText instanceof Spannable) movement.initialize(this, (Spannable) mText);
        if (movement != null) {
            setFocusable(true);
            setClickable(true);
        }
    }

    public final TransformationMethod getTransformationMethod() { return mTransformation; }

    public final void setTransformationMethod(TransformationMethod method) {
        if (mTransformation == method) return;
        if (mTransformation != null && mText instanceof Spannable) ((Spannable) mText).removeSpan(mTransformation);
        mTransformation = method;
        if (method instanceof TextWatcher && mText instanceof Spannable) {
            ((Spannable) mText).setSpan(method, 0, mText.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }
        updateTransformed();
        relayout();
    }

    public void setInputType(int type) {
        boolean password = isPasswordInputType(type);
        mInputType = type;
        if (password) setTransformationMethod(PasswordTransformationMethod.getInstance());
        else if (mTransformation instanceof PasswordTransformationMethod) {
            setTransformationMethod(mSingleLine ? SingleLineTransformationMethod.getInstance() : null);
        }
        if (type != InputType.TYPE_NULL && mBufferType != BufferType.EDITABLE) setText(mText, BufferType.EDITABLE);
    }

    public void setRawInputType(int type) { mInputType = type; }

    public int getInputType() { return mInputType; }

    private static boolean isPasswordInputType(int type) {
        int cls = type & InputType.TYPE_MASK_CLASS;
        int variation = type & InputType.TYPE_MASK_VARIATION;
        return (cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
                || (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    }

    public void setImeOptions(int imeOptions) { mImeOptions = imeOptions; }

    public int getImeOptions() { return mImeOptions; }

    public void setImeActionLabel(CharSequence label, int actionId) {
        mImeActionLabel = label;
        mImeActionId = actionId;
    }

    public CharSequence getImeActionLabel() { return mImeActionLabel; }

    public int getImeActionId() { return mImeActionId; }

    public void setOnEditorActionListener(OnEditorActionListener listener) { mEditorActionListener = listener; }

    public void onEditorAction(int actionCode) {
        if (mEditorActionListener != null) mEditorActionListener.onEditorAction(this, actionCode, null);
    }

    public void setPrivateImeOptions(String type) { mPrivateImeOptions = type; }

    public String getPrivateImeOptions() { return mPrivateImeOptions; }

    public void setInputExtras(int xmlResId) { if (mInputExtras == null) mInputExtras = new Bundle(); }

    public Bundle getInputExtras(boolean create) {
        if (mInputExtras == null && create) mInputExtras = new Bundle();
        return mInputExtras;
    }

    public void setImeHintLocales(LocaleList hintLocales) { mImeHintLocales = hintLocales; }

    public LocaleList getImeHintLocales() { return mImeHintLocales; }

    public final void setShowSoftInputOnFocus(boolean show) {}

    public final boolean getShowSoftInputOnFocus() { return true; }

    public int getSelectionStart() { return Selection.getSelectionStart(mText); }

    public int getSelectionEnd() { return Selection.getSelectionEnd(mText); }

    public boolean hasSelection() {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        return start >= 0 && end >= 0 && start != end;
    }

    /** TextView is not editable; EditText overrides this. */
    protected boolean getDefaultEditable() { return false; }

    /** No movement method until the text is selectable or an editor. */
    protected MovementMethod getDefaultMovementMethod() { return null; }

    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {}

    protected void onSelectionChanged(int selStart, int selEnd) {}

    public void setSearchResultHighlights(int... ranges) {
        if (ranges != null && (ranges.length % 2) != 0) {
            throw new IllegalArgumentException("ranges must have even length, but was " + ranges.length);
        }
        mSearchResultHighlights = ranges;
        if (ranges == null) mFocusedSearchResultIndex = FOCUSED_SEARCH_RESULT_INDEX_NONE;
        invalidate();
    }

    public int[] getSearchResultHighlights() { return mSearchResultHighlights; }

    public void setFocusedSearchResultIndex(int index) {
        if (mSearchResultHighlights == null) {
            if (index != FOCUSED_SEARCH_RESULT_INDEX_NONE) {
                throw new IllegalArgumentException("no search results");
            }
        } else if (index < FOCUSED_SEARCH_RESULT_INDEX_NONE || index >= mSearchResultHighlights.length / 2) {
            throw new IllegalArgumentException("focused search result index is out of range");
        }
        mFocusedSearchResultIndex = index;
        invalidate();
    }

    public int getFocusedSearchResultIndex() { return mFocusedSearchResultIndex; }

    public void setSearchResultHighlightColor(int color) {
        mSearchResultHighlightColor = color;
        invalidate();
    }

    public int getSearchResultHighlightColor() { return mSearchResultHighlightColor; }

    public void setFocusedSearchResultHighlightColor(int color) {
        mFocusedSearchResultHighlightColor = color;
        invalidate();
    }

    public int getFocusedSearchResultHighlightColor() { return mFocusedSearchResultHighlightColor; }

    public boolean onTextContextMenuItem(int id) {
        if (!(mText instanceof Spannable)) return false;
        Spannable sp = (Spannable) mText;
        if (id == android.R.id.selectAll) {
            Selection.selectAll(sp);
            return true;
        }
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (start < 0 || end < 0) return false;
        int a = Math.min(start, end);
        int b = Math.max(start, end);
        android.content.ClipboardManager clip =
                (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (id == android.R.id.copy || id == android.R.id.cut) {
            if (a == b || clip == null) return false;
            clip.setText(mText.subSequence(a, b));
            if (id == android.R.id.cut && mText instanceof Editable) {
                ((Editable) mText).delete(a, b);
            }
            return true;
        }
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            if (clip == null || !clip.hasText() || !(mText instanceof Editable)) return false;
            ((Editable) mText).replace(a, b, clip.getText());
            return true;
        }
        return false;
    }

    public void setSelectAllOnFocus(boolean selectAllOnFocus) { mSelectAllOnFocus = selectAllOnFocus; }

    public void setCursorVisible(boolean visible) {
        if (mCursorVisible != visible) {
            mCursorVisible = visible;
            invalidate();
        }
    }

    public boolean isCursorVisible() { return mCursorVisible; }

    public void setTextIsSelectable(boolean selectable) {
        mTextIsSelectable = selectable;
        if (selectable) {
            setFocusable(true);
            setFocusableInTouchMode(true);
            setClickable(true);
            setLongClickable(true);
            if (!(mText instanceof Spannable)) setText(mText, BufferType.SPANNABLE);
        }
    }

    public boolean isTextSelectable() { return mTextIsSelectable; }

    public void setTextCursorDrawable(Drawable textCursorDrawable) {}

    public void setTextCursorDrawable(int textCursorDrawable) { mCursorDrawableRes = textCursorDrawable; }

    public Drawable getTextCursorDrawable() { return null; }

    public void setTextSelectHandle(Drawable d) {}

    public void setTextSelectHandle(int res) { mTextSelectHandleRes = res; }

    public Drawable getTextSelectHandle() { return null; }

    public void setTextSelectHandleLeft(Drawable d) {}

    public void setTextSelectHandleLeft(int res) { mTextSelectHandleLeftRes = res; }

    public Drawable getTextSelectHandleLeft() { return null; }

    public void setTextSelectHandleRight(Drawable d) {}

    public void setTextSelectHandleRight(int res) { mTextSelectHandleRightRes = res; }

    public Drawable getTextSelectHandleRight() { return null; }

    public final int getAutoLinkMask() { return mAutoLinkMask; }

    public final void setAutoLinkMask(int mask) { mAutoLinkMask = mask; }

    /** Linkify the current text when a mask is set. Editors keep their own movement method. */
    private void applyAutoLinks() {
        if (mAutoLinkMask == 0) return;
        Spannable spannable;
        boolean copied = false;
        if (mText instanceof Spannable) spannable = (Spannable) mText;
        else {
            spannable = mSpannableFactory.newSpannable(mText);
            copied = true;
        }
        if (!Linkify.addLinks(spannable, mAutoLinkMask)) return;
        if (copied) {
            mText = spannable;
            if (mBufferType != BufferType.EDITABLE) mBufferType = BufferType.SPANNABLE;
            updateTransformed();
        }
        if (mLinksClickable && mMovement == null && getDefaultMovementMethod() == null && mKeyListener == null) {
            setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    public final void setLinksClickable(boolean whether) { mLinksClickable = whether; }

    public final boolean getLinksClickable() { return mLinksClickable; }

    public URLSpan[] getUrls() {
        if (mText instanceof Spanned) return ((Spanned) mText).getSpans(0, mText.length(), URLSpan.class);
        return new URLSpan[0];
    }

    public CharSequence getError() { return mError; }

    public void setError(CharSequence error) { setError(error, null); }

    public void setError(CharSequence error, Drawable icon) {
        mError = error;
        invalidate();
    }

    public void clearComposingText() {
        if (mText instanceof Spannable) {
            Spannable sp = (Spannable) mText;
            Object[] spans = sp.getSpans(0, sp.length(), Object.class);
            for (int i = 0; i < spans.length; i++) {
                if ((sp.getSpanFlags(spans[i]) & Spanned.SPAN_COMPOSING) != 0) sp.removeSpan(spans[i]);
            }
        }
    }

    public boolean onCheckIsTextEditor() {
        return mInputType != InputType.TYPE_NULL || mBufferType == BufferType.EDITABLE || mKeyListener != null;
    }

    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (!onCheckIsTextEditor() || outAttrs == null) return null;
        outAttrs.inputType = mInputType;
        outAttrs.imeOptions = mImeOptions;
        outAttrs.hintText = mHint;
        outAttrs.actionLabel = mImeActionLabel;
        outAttrs.actionId = mImeActionId;
        outAttrs.privateImeOptions = mPrivateImeOptions;
        outAttrs.packageName = getContext().getPackageName();
        outAttrs.fieldId = getId();
        outAttrs.initialSelStart = getSelectionStart();
        outAttrs.initialSelEnd = getSelectionEnd();
        outAttrs.hintLocales = mImeHintLocales;
        if (!(mText instanceof Editable)) return null;
        return new EditableInputConnection(this);
    }

    public boolean extractText(ExtractedTextRequest request, ExtractedText outText) {
        if (mText == null || outText == null) return false;
        outText.text = (request != null && (request.flags & InputConnection.GET_TEXT_WITH_STYLES) != 0)
                ? new android.text.SpannableString(mText) : mText.toString();
        outText.startOffset = 0;
        outText.partialStartOffset = -1;
        outText.partialEndOffset = -1;
        outText.selectionStart = getSelectionStart();
        outText.selectionEnd = getSelectionEnd();
        outText.flags = 0;
        if (MetaKeyKeyListener.getMetaState(mText, MetaKeyKeyListener.META_SELECTING) != 0) {
            outText.flags |= ExtractedText.FLAG_SELECTING;
        }
        if (mSingleLine) outText.flags |= ExtractedText.FLAG_SINGLE_LINE;
        outText.hint = mHint;
        return true;
    }

    public void setExtractedText(ExtractedText text) {
        if (text == null || text.text == null) return;
        if (!(mText instanceof Editable)) return;
        Editable content = (Editable) mText;
        if (text.partialStartOffset < 0) {
            content.replace(0, content.length(), text.text);
        } else {
            final int n = content.length();
            int start = Math.min(Math.max(text.partialStartOffset, 0), n);
            int end = Math.min(Math.max(text.partialEndOffset, start), n);
            content.replace(start, end, text.text);
        }
        int len = content.length();
        int selStart = Math.min(Math.max(text.selectionStart, 0), len);
        int selEnd = Math.min(Math.max(text.selectionEnd, 0), len);
        android.text.Selection.setSelection(content, selStart, selEnd);
        if ((text.flags & ExtractedText.FLAG_SELECTING) != 0) {
            MetaKeyKeyListener.startSelecting(this, content);
        } else {
            MetaKeyKeyListener.stopSelecting(this, content);
        }
    }

    public void onCommitCompletion(CompletionInfo text) {}

    public void onCommitCorrection(CorrectionInfo info) {}

    public boolean isInputMethodTarget() { return onCheckIsTextEditor() && isFocused(); }

    public TextDirectionHeuristic getTextDirectionHeuristic() {
        if (isPasswordInputType(mInputType)) return TextDirectionHeuristics.LTR;
        switch (getTextDirection()) {
            case TEXT_DIRECTION_FIRST_STRONG_RTL: return TextDirectionHeuristics.FIRSTSTRONG_RTL;
            case TEXT_DIRECTION_ANY_RTL: return TextDirectionHeuristics.ANYRTL_LTR;
            case TEXT_DIRECTION_LTR: return TextDirectionHeuristics.LTR;
            case TEXT_DIRECTION_RTL: return TextDirectionHeuristics.RTL;
            case TEXT_DIRECTION_LOCALE: return TextDirectionHeuristics.LOCALE;
            case TEXT_DIRECTION_FIRST_STRONG:
            case TEXT_DIRECTION_FIRST_STRONG_LTR:
            default: return TextDirectionHeuristics.FIRSTSTRONG_LTR;
        }
    }

    // ---- compound drawables ----

    public void setCompoundDrawables(Drawable left, Drawable top, Drawable right, Drawable bottom) {
        mUseRelative = false;
        mDrawableStart = null;
        mDrawableEnd = null;
        replaceDrawable(mDrawableLeft, left);
        replaceDrawable(mDrawableTop, top);
        replaceDrawable(mDrawableRight, right);
        replaceDrawable(mDrawableBottom, bottom);
        mDrawableLeft = left;
        mDrawableTop = top;
        mDrawableRight = right;
        mDrawableBottom = bottom;
        resolveDrawables();
        relayout();
    }

    public void setCompoundDrawablesWithIntrinsicBounds(int left, int top, int right, int bottom) {
        setCompoundDrawables(drawable(left), drawable(top), drawable(right), drawable(bottom));
    }

    public void setCompoundDrawablesWithIntrinsicBounds(Drawable left, Drawable top, Drawable right, Drawable bottom) {
        setCompoundDrawables(left, top, right, bottom);
    }

    public void setCompoundDrawablesRelative(Drawable start, Drawable top, Drawable end, Drawable bottom) {
        mUseRelative = true;
        replaceDrawable(mDrawableStart, start);
        replaceDrawable(mDrawableTop, top);
        replaceDrawable(mDrawableEnd, end);
        replaceDrawable(mDrawableBottom, bottom);
        mDrawableStart = start;
        mDrawableTop = top;
        mDrawableEnd = end;
        mDrawableBottom = bottom;
        resolveDrawables();
        relayout();
    }

    public void setCompoundDrawablesRelativeWithIntrinsicBounds(int start, int top, int end, int bottom) {
        setCompoundDrawablesRelative(drawable(start), drawable(top), drawable(end), drawable(bottom));
    }

    public void setCompoundDrawablesRelativeWithIntrinsicBounds(Drawable start, Drawable top, Drawable end,
            Drawable bottom) {
        setCompoundDrawablesRelative(start, top, end, bottom);
    }

    private Drawable drawable(int id) { return id == 0 ? null : getContext().getDrawable(id); }

    private void replaceDrawable(Drawable oldD, Drawable neu) {
        if (oldD == neu) return;
        if (oldD != null) oldD.setCallback(null);
        if (neu != null) {
            neu.setCallback(this);
            if (neu.isStateful()) neu.setState(getDrawableState());
            if (mDrawableTint != null) neu.setTintList(mDrawableTint);
            if (mDrawableTintMode != null) neu.setTintMode(mDrawableTintMode);
            int w = neu.getIntrinsicWidth();
            int h = neu.getIntrinsicHeight();
            if (w < 0) w = 0;
            if (h < 0) h = 0;
            neu.setBounds(0, 0, w, h);
        }
    }

    private void resolveDrawables() {
        if (mUseRelative) {
            if (isLayoutRtl()) {
                mResolvedLeft = mDrawableEnd;
                mResolvedRight = mDrawableStart;
            } else {
                mResolvedLeft = mDrawableStart;
                mResolvedRight = mDrawableEnd;
            }
        } else {
            mResolvedLeft = mDrawableLeft;
            mResolvedRight = mDrawableRight;
        }
    }

    public Drawable[] getCompoundDrawables() {
        return new Drawable[] { mResolvedLeft, mDrawableTop, mResolvedRight, mDrawableBottom };
    }

    public Drawable[] getCompoundDrawablesRelative() {
        if (mUseRelative) return new Drawable[] { mDrawableStart, mDrawableTop, mDrawableEnd, mDrawableBottom };
        if (isLayoutRtl()) return new Drawable[] { mResolvedRight, mDrawableTop, mResolvedLeft, mDrawableBottom };
        return new Drawable[] { mResolvedLeft, mDrawableTop, mResolvedRight, mDrawableBottom };
    }

    public void setCompoundDrawablePadding(int pad) {
        if (mDrawablePadding != pad) {
            mDrawablePadding = pad;
            relayout();
        }
    }

    public int getCompoundDrawablePadding() { return mDrawablePadding; }

    public void setCompoundDrawableTintList(ColorStateList tint) {
        mDrawableTint = tint;
        applyDrawableTint();
    }

    public ColorStateList getCompoundDrawableTintList() { return mDrawableTint; }

    public void setCompoundDrawableTintMode(android.graphics.PorterDuff.Mode mode) {
        mDrawableTintMode = mode;
        applyDrawableTint();
    }

    public android.graphics.PorterDuff.Mode getCompoundDrawableTintMode() { return mDrawableTintMode; }

    public void setCompoundDrawableTintBlendMode(android.graphics.BlendMode mode) { mDrawableBlendMode = mode; }

    public android.graphics.BlendMode getCompoundDrawableTintBlendMode() { return mDrawableBlendMode; }

    private void applyDrawableTint() {
        tint(mResolvedLeft);
        tint(mDrawableTop);
        tint(mResolvedRight);
        tint(mDrawableBottom);
        invalidate();
    }

    private void tint(Drawable d) {
        if (d == null) return;
        if (mDrawableTint != null) d.setTintList(mDrawableTint);
        if (mDrawableTintMode != null) d.setTintMode(mDrawableTintMode);
    }

    private static int drawableWidth(Drawable d) {
        if (d == null) return 0;
        return Math.max(0, d.getBounds().width());
    }

    private static int drawableHeight(Drawable d) {
        if (d == null) return 0;
        return Math.max(0, d.getBounds().height());
    }

    public int getCompoundPaddingLeft() {
        int pad = getPaddingLeft();
        if (mResolvedLeft != null) pad += drawableWidth(mResolvedLeft) + mDrawablePadding;
        return pad;
    }

    public int getCompoundPaddingRight() {
        int pad = getPaddingRight();
        if (mResolvedRight != null) pad += drawableWidth(mResolvedRight) + mDrawablePadding;
        return pad;
    }

    public int getCompoundPaddingTop() {
        int pad = getPaddingTop();
        if (mDrawableTop != null) pad += drawableHeight(mDrawableTop) + mDrawablePadding;
        return pad;
    }

    public int getCompoundPaddingBottom() {
        int pad = getPaddingBottom();
        if (mDrawableBottom != null) pad += drawableHeight(mDrawableBottom) + mDrawablePadding;
        return pad;
    }

    public int getCompoundPaddingStart() { return isLayoutRtl() ? getCompoundPaddingRight() : getCompoundPaddingLeft(); }

    public int getCompoundPaddingEnd() { return isLayoutRtl() ? getCompoundPaddingLeft() : getCompoundPaddingRight(); }

    public int getExtendedPaddingTop() { return getCompoundPaddingTop(); }

    public int getExtendedPaddingBottom() { return getCompoundPaddingBottom(); }

    public int getTotalPaddingLeft() { return getCompoundPaddingLeft(); }

    public int getTotalPaddingRight() { return getCompoundPaddingRight(); }

    public int getTotalPaddingStart() { return getCompoundPaddingStart(); }

    public int getTotalPaddingEnd() { return getCompoundPaddingEnd(); }

    public int getTotalPaddingTop() { return getExtendedPaddingTop(); }

    public int getTotalPaddingBottom() { return getExtendedPaddingBottom(); }

    // ---- layout and draw ----

    private int maxLines() {
        if (mMaxMode != LINES) return Integer.MAX_VALUE;
        return mMaximum <= 0 ? Integer.MAX_VALUE : mMaximum;
    }

    private Layout.Alignment layoutAlignment() {
        switch (getTextAlignment()) {
            case TEXT_ALIGNMENT_CENTER: return Layout.Alignment.ALIGN_CENTER;
            case TEXT_ALIGNMENT_TEXT_END:
            case TEXT_ALIGNMENT_VIEW_END: return Layout.Alignment.ALIGN_OPPOSITE;
            case TEXT_ALIGNMENT_TEXT_START:
            case TEXT_ALIGNMENT_VIEW_START: return Layout.Alignment.ALIGN_NORMAL;
            case TEXT_ALIGNMENT_GRAVITY:
            default:
                break;
        }
        int relative = mGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK;
        if (relative == Gravity.END) return Layout.Alignment.ALIGN_OPPOSITE;
        if (relative == Gravity.START) return Layout.Alignment.ALIGN_NORMAL;
        switch (mGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
            case Gravity.CENTER_HORIZONTAL: return Layout.Alignment.ALIGN_CENTER;
            case Gravity.RIGHT: return Layout.Alignment.ALIGN_OPPOSITE;
            case Gravity.LEFT:
            default: return Layout.Alignment.ALIGN_NORMAL;
        }
    }

    private void relayout() {
        mLayout = null;
        mHintLayout = null;
        if (mContentWidth >= 0) makeNewLayout(mContentWidth);
        requestLayout();
        invalidate();
    }

    private void makeNewLayout(int contentWidth) {
        if (contentWidth < 0) contentWidth = 0;
        mContentWidth = contentWidth;
        updateTransformed();
        int layoutWidth = contentWidth;
        if (mHorizontallyScrolling) {
            int desired = (int) Math.ceil(Layout.getDesiredWidth(mTransformed, mTextPaint));
            if (desired > layoutWidth) layoutWidth = desired;
        }
        Layout.Alignment align = layoutAlignment();
        TextDirectionHeuristic dir = getTextDirectionHeuristic();
        int maxLines = maxLines();
        if (mText instanceof Spannable) {
            mLayout = android.text.DynamicLayout.Builder.obtain(mText, mTextPaint, layoutWidth)
                    .setDisplayText(mTransformed).setAlignment(align).setTextDirection(dir)
                    .setLineSpacing(mSpacingAdd, mSpacingMult).setIncludePad(mIncludePad)
                    .setUseLineSpacingFromFallbacks(mFallbackLineSpacing).setEllipsize(mEllipsize)
                    .setEllipsizedWidth(contentWidth).setMaxLines(maxLines).setBreakStrategy(mBreakStrategy)
                    .setHyphenationFrequency(mHyphenationFrequency).setJustificationMode(mJustificationMode)
                    .build();
        } else {
            StaticLayout.Builder b = StaticLayout.Builder.obtain(mTransformed, 0, mTransformed.length(), mTextPaint,
                    layoutWidth);
            b.setAlignment(align).setTextDirection(dir).setLineSpacing(mSpacingAdd, mSpacingMult)
                    .setIncludePad(mIncludePad).setUseLineSpacingFromFallbacks(mFallbackLineSpacing)
                    .setEllipsize(mEllipsize).setEllipsizedWidth(contentWidth).setMaxLines(maxLines)
                    .setBreakStrategy(mBreakStrategy).setHyphenationFrequency(mHyphenationFrequency)
                    .setJustificationMode(mJustificationMode);
            if (mUseBoundsForWidth) b.setUseBoundsForWidth(true);
            if (mShiftDrawingOffsetForStartOverhang) b.setShiftDrawingOffsetForStartOverhang(true);
            if (mMinimumFontMetrics != null) b.setMinimumFontMetrics(mMinimumFontMetrics);
            mLayout = b.build();
        }
        if (mHint != null && mHint.length() > 0) {
            mHintLayout = StaticLayout.Builder.obtain(mHint, 0, mHint.length(), mTextPaint, contentWidth)
                    .setAlignment(align).setTextDirection(dir).setLineSpacing(mSpacingAdd, mSpacingMult)
                    .setIncludePad(mIncludePad).setEllipsize(mEllipsize).setEllipsizedWidth(contentWidth)
                    .setMaxLines(maxLines).build();
        } else {
            mHintLayout = null;
        }
    }

    private int limitPx(int value, int mode) {
        if (value == Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (mode == EMS) {
            int em = getLineHeight();
            if (em <= 0) return 0;
            if (value > Integer.MAX_VALUE / em) return Integer.MAX_VALUE;
            return value * em;
        }
        return value;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else {
            CharSequence probe = mTransformed;
            if ((probe == null || probe.length() == 0) && mHint != null) probe = mHint;
            if (probe == null) probe = "";
            int des = (int) Math.ceil(Layout.getDesiredWidth(probe, mTextPaint));
            des = Math.max(des, drawableWidth(mDrawableTop));
            des = Math.max(des, drawableWidth(mDrawableBottom));
            width = des + getCompoundPaddingLeft() + getCompoundPaddingRight();
            width = Math.min(width, limitPx(mMaxWidth, mMaxWidthMode));
            width = Math.max(width, limitPx(mMinWidth, mMinWidthMode));
            width = Math.max(width, getSuggestedMinimumWidth());
            if (widthMode == MeasureSpec.AT_MOST) width = Math.min(width, widthSize);
        }
        if (width < 0) width = 0;
        int contentWidth = width - getCompoundPaddingLeft() - getCompoundPaddingRight();
        if (contentWidth < 0) contentWidth = 0;
        int layoutWidth = contentWidth;
        if (mHorizontallyScrolling && mTransformed != null) {
            int desired = (int) Math.ceil(Layout.getDesiredWidth(mTransformed, mTextPaint));
            if (desired > layoutWidth) layoutWidth = desired;
        }
        if (mLayout == null || mLayout.getWidth() != layoutWidth || mLayout.getText() != displayText()) {
            makeNewLayout(contentWidth);
        }

        int want = mLayout != null ? mLayout.getHeight() : 0;
        if (mText.length() == 0 && mHintLayout != null) want = mHintLayout.getHeight();
        int minH = mMinMode == LINES ? limitPx(mMinimum, EMS) : mMinimum;
        int maxH = mMaxMode == LINES ? limitPx(mMaximum, EMS) : mMaximum;
        if (mMaxMode == LINES && mMaximum == Integer.MAX_VALUE) maxH = Integer.MAX_VALUE;
        want = Math.min(want, maxH);
        want = Math.max(want, minH);
        want += getCompoundPaddingTop() + getCompoundPaddingBottom();
        want = Math.max(want, drawableHeight(mResolvedLeft) + getPaddingTop() + getPaddingBottom());
        want = Math.max(want, drawableHeight(mResolvedRight) + getPaddingTop() + getPaddingBottom());
        want = Math.max(want, getSuggestedMinimumHeight());

        int height;
        if (heightMode == MeasureSpec.EXACTLY) height = heightSize;
        else {
            height = want;
            if (heightMode == MeasureSpec.AT_MOST) height = Math.min(want, heightSize);
        }
        setMeasuredDimension(width, height);
    }

    private CharSequence displayText() {
        return mText.length() == 0 && mHintLayout != null ? mHint : mTransformed;
    }

    private int verticalOffset(boolean forceNormal) {
        Layout l = mLayout;
        if (!forceNormal && mText.length() == 0 && mHintLayout != null) l = mHintLayout;
        if (l == null) return 0;
        int box = getHeight() - getExtendedPaddingTop() - getExtendedPaddingBottom();
        int textht = l.getHeight();
        if (textht >= box) return 0;
        int vg = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        if (vg == Gravity.BOTTOM) return box - textht;
        if (vg == Gravity.CENTER_VERTICAL) return (box - textht) / 2;
        return 0;
    }

    public final Layout getLayout() { return mLayout; }

    public int getLineCount() { return mLayout != null ? mLayout.getLineCount() : 0; }

    public int getLineBounds(int line, Rect bounds) {
        if (mLayout == null) {
            if (bounds != null) bounds.set(0, 0, 0, 0);
            return 0;
        }
        int baseline = mLayout.getLineBounds(line, bounds);
        int voffset = getExtendedPaddingTop() + verticalOffset(true);
        if (bounds != null) bounds.offset(getCompoundPaddingLeft(), voffset);
        return baseline + voffset;
    }

    @Override
    public int getBaseline() {
        if (mLayout == null) return -1;
        return getExtendedPaddingTop() + verticalOffset(true) + mLayout.getLineBaseline(0);
    }

    public int getOffsetForPosition(float x, float y) {
        if (mLayout == null) return -1;
        y -= getTotalPaddingTop();
        y -= verticalOffset(true);
        y += getScrollY();
        if (y < 0) y = 0;
        int line = mLayout.getLineForVertical((int) y);
        x -= getTotalPaddingLeft();
        x += getScrollX();
        return mLayout.getOffsetForHorizontal(line, x);
    }

    public boolean bringPointIntoView(int offset) { return bringPointIntoView(offset, false); }

    public boolean bringPointIntoView(int offset, boolean requestRect) {
        if (mLayout == null) return false;
        int len = mLayout.getText().length();
        if (offset < 0) offset = 0;
        if (offset > len) offset = len;
        int line = mLayout.getLineForOffset(offset);
        int top = mLayout.getLineTop(line);
        int bottom = mLayout.getLineBottom(line);
        int left = (int) Math.floor(mLayout.getPrimaryHorizontal(offset));
        int hs = getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        int vs = getHeight() - getExtendedPaddingTop() - getExtendedPaddingBottom();
        int nx = getScrollX();
        int ny = getScrollY();
        if (left < nx) nx = left;
        else if (left + 2 > nx + hs) nx = left + 2 - hs;
        if (top < ny) ny = top;
        else if (bottom > ny + vs) ny = bottom - vs;
        if (nx < 0) nx = 0;
        if (ny < 0) ny = 0;
        if (nx != getScrollX() || ny != getScrollY()) {
            scrollTo(nx, ny);
            return true;
        }
        return false;
    }

    public boolean moveCursorToVisibleOffset() { return false; }

    @Override
    protected void onDraw(Canvas canvas) {
        drawCompoundDrawables(canvas);
        if (mLayout == null) return;
        boolean hint = mText.length() == 0 && mHintLayout != null;
        Layout layout = hint ? mHintLayout : mLayout;
        mTextPaint.setColor(hint ? mCurHintTextColor : mCurTextColor);
        mTextPaint.drawableState = getDrawableState();
        int padL = getCompoundPaddingLeft();
        int padT = getExtendedPaddingTop();
        int padR = getCompoundPaddingRight();
        int padB = getExtendedPaddingBottom();
        int voffset = verticalOffset(false);
        canvas.save();
        canvas.clipRect(padL + getScrollX(), padT + getScrollY(), getWidth() - padR + getScrollX(),
                getHeight() - padB + getScrollY());
        canvas.translate(padL, padT + voffset);
        Path highlight = null;
        if (!hint && hasSelection()) {
            mHighlightPath.reset();
            layout.getSelectionPath(getSelectionStart(), getSelectionEnd(), mHighlightPath);
            if (mHighlightPaint == null) mHighlightPaint = new Paint();
            mHighlightPaint.setColor(mHighlightColor);
            highlight = mHighlightPath;
        }
        layout.draw(canvas, highlight, mHighlightPaint, 0);
        if (!hint && shouldDrawCursor()) {
            Path cursor = new Path();
            layout.getCursorPath(getSelectionStart(), cursor, mText);
            Paint cp = mHighlightPaint != null ? mHighlightPaint : (mHighlightPaint = new Paint());
            int prev = cp.getColor();
            Paint.Style prevStyle = cp.getStyle();
            float prevWidth = cp.getStrokeWidth();
            cp.setColor(mCurTextColor);
            cp.setStyle(Paint.Style.STROKE);
            cp.setStrokeWidth(Math.max(1f, mTextPaint.getTextSize() / 15f));
            canvas.drawPath(cursor, cp);
            cp.setColor(prev);
            cp.setStyle(prevStyle);
            cp.setStrokeWidth(prevWidth);
        }
        canvas.restore();
    }

    private boolean shouldDrawCursor() {
        if (!mCursorVisible || !isFocused() || mLayout == null) return false;
        if (mBufferType != BufferType.EDITABLE && !mTextIsSelectable) return false;
        int a = getSelectionStart();
        int b = getSelectionEnd();
        return a >= 0 && a == b;
    }

    private void drawCompoundDrawables(Canvas canvas) {
        int sx = getScrollX();
        int sy = getScrollY();
        int vspace = getHeight() - getPaddingTop() - getPaddingBottom();
        int hspace = getWidth() - getPaddingLeft() - getPaddingRight();
        drawDrawable(canvas, mResolvedLeft, sx + getPaddingLeft(), sy + gravityY(drawableHeight(mResolvedLeft), vspace));
        drawDrawable(canvas, mResolvedRight,
                sx + getWidth() - getPaddingRight() - drawableWidth(mResolvedRight),
                sy + gravityY(drawableHeight(mResolvedRight), vspace));
        drawDrawable(canvas, mDrawableTop, sx + gravityX(drawableWidth(mDrawableTop), hspace), sy + getPaddingTop());
        drawDrawable(canvas, mDrawableBottom, sx + gravityX(drawableWidth(mDrawableBottom), hspace),
                sy + getHeight() - getPaddingBottom() - drawableHeight(mDrawableBottom));
    }

    private int gravityY(int h, int space) {
        int vg = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        if (vg == Gravity.BOTTOM) return getPaddingTop() + space - h;
        if (vg == Gravity.CENTER_VERTICAL) return getPaddingTop() + (space - h) / 2;
        return getPaddingTop();
    }

    private int gravityX(int w, int space) {
        int relative = mGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK;
        if (relative == Gravity.CENTER_HORIZONTAL || (mGravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL) {
            return getPaddingLeft() + (space - w) / 2;
        }
        if (relative == Gravity.END || (mGravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT) {
            return getPaddingLeft() + space - w;
        }
        return getPaddingLeft();
    }

    private void drawDrawable(Canvas canvas, Drawable d, int x, int y) {
        if (d == null) return;
        canvas.save();
        canvas.translate(x, y);
        d.draw(canvas);
        canvas.restore();
    }

    public boolean onPreDraw() { return true; }

    // ---- input ----

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mKeyListener != null && mText instanceof Editable
                && mKeyListener.onKeyDown(this, (Editable) mText, keyCode, event)) {
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_ENTER && mSingleLine && mBufferType == BufferType.EDITABLE) {
            int action = mImeOptions & EditorInfo.IME_MASK_ACTION;
            onEditorAction(action == EditorInfo.IME_ACTION_UNSPECIFIED ? EditorInfo.IME_ACTION_DONE : action);
            return true;
        }
        if (mMovement != null && mText instanceof Spannable
                && mMovement.onKeyDown(this, (Spannable) mText, keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mKeyListener != null && mText instanceof Editable
                && mKeyListener.onKeyUp(this, (Editable) mText, keyCode, event)) {
            return true;
        }
        if (mMovement != null && mText instanceof Spannable
                && mMovement.onKeyUp(this, (Spannable) mText, keyCode, event)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        if (mKeyListener != null && mText instanceof Editable && mKeyListener.onKeyOther(this, (Editable) mText, event)) {
            return true;
        }
        return super.onKeyMultiple(keyCode, repeatCount, event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP && onCheckIsTextEditor() && isFocusable() && isFocusableInTouchMode()
                && !isFocused()) {
            requestFocus();
        }
        if (mMovement != null && mText instanceof Spannable
                && mMovement.onTouchEvent(this, (Spannable) mText, event)) {
            return true;
        }
        if (mLinksClickable && event.getActionMasked() == MotionEvent.ACTION_UP && handleClickableSpan(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    private boolean handleClickableSpan(MotionEvent event) {
        if (!(mText instanceof Spanned) || mLayout == null) return false;
        int off = getOffsetForPosition(event.getX(), event.getY());
        if (off < 0) return false;
        ClickableSpan[] links = ((Spanned) mText).getSpans(off, off, ClickableSpan.class);
        if (links.length == 0) return false;
        links[0].onClick(this);
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mMovement != null && mText instanceof Spannable
                && mMovement.onGenericMotionEvent(this, (Spannable) mText, event)) {
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        if (mMovement != null && mText instanceof Spannable
                && mMovement.onTrackballEvent(this, (Spannable) mText, event)) {
            return true;
        }
        return super.onTrackballEvent(event);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (mTransformation != null) {
            mTransformation.onFocusChanged(this, mText, focused, direction, previouslyFocusedRect);
        }
        if (focused && mSelectAllOnFocus && mText instanceof Spannable) Selection.selectAll((Spannable) mText);
        if (mMovement != null && mText instanceof Spannable) mMovement.onTakeFocus(this, (Spannable) mText, direction);
        if (focused && onCheckIsTextEditor() && getShowSoftInputOnFocus()) {
            InputMethodManager.systemInstance().showSoftInput(this, 0);
        }
        invalidate();
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled == isEnabled()) return;
        super.setEnabled(enabled);
        updateTextColors();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mTextColor != null && mTextColor.isStateful() || (mHintColor != null && mHintColor.isStateful())
                || (mLinkColor != null && mLinkColor.isStateful())) {
            updateTextColors();
        }
        int[] state = getDrawableState();
        if (mResolvedLeft != null && mResolvedLeft.isStateful()) mResolvedLeft.setState(state);
        if (mResolvedRight != null && mResolvedRight.isStateful()) mResolvedRight.setState(state);
        if (mDrawableTop != null && mDrawableTop.isStateful()) mDrawableTop.setState(state);
        if (mDrawableBottom != null && mDrawableBottom.isStateful()) mDrawableBottom.setState(state);
    }

    private void updateTextColors() {
        int[] state = getDrawableState();
        int color = mTextColor.getColorForState(state, mTextColor.getDefaultColor());
        int hint;
        if (mHintColor != null) hint = mHintColor.getColorForState(state, mHintColor.getDefaultColor());
        else hint = (color & 0x00FFFFFF) | 0x80000000;
        mCurTextColor = color;
        mCurHintTextColor = hint;
        mTextPaint.setColor(color);
        if (mLinkColor != null) mTextPaint.linkColor = mLinkColor.getColorForState(state, mLinkColor.getDefaultColor());
        invalidate();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || who == mResolvedLeft || who == mResolvedRight || who == mDrawableTop
                || who == mDrawableBottom;
    }

    @Override
    public void invalidateDrawable(Drawable drawable) {
        if (verifyDrawable(drawable)) invalidate();
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mResolvedLeft != null) mResolvedLeft.jumpToCurrentState();
        if (mResolvedRight != null) mResolvedRight.jumpToCurrentState();
        if (mDrawableTop != null) mDrawableTop.jumpToCurrentState();
        if (mDrawableBottom != null) mDrawableBottom.jumpToCurrentState();
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);
        if (mResolvedLeft != null) mResolvedLeft.setHotspot(x, y);
        if (mResolvedRight != null) mResolvedRight.setHotspot(x, y);
        if (mDrawableTop != null) mDrawableTop.setHotspot(x, y);
        if (mDrawableBottom != null) mDrawableBottom.setHotspot(x, y);
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        resolveDrawables();
        relayout();
    }

    @Override
    public boolean hasOverlappingRendering() { return false; }

    @Override
    public CharSequence getAccessibilityClassName() { return TextView.class.getName(); }

    @Override
    public void findViewsWithText(ArrayList<View> outViews, CharSequence searched, int flags) {
        super.findViewsWithText(outViews, searched, flags);
        if ((flags & FIND_VIEWS_WITH_TEXT) != 0 && searched != null && searched.length() > 0 && mText != null
                && mText.length() > 0) {
            if (mText.toString().toLowerCase().contains(searched.toString().toLowerCase()) && !outViews.contains(this)) {
                outViews.add(this);
            }
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        if (!getFreezesText()) return superState;
        SavedState ss = new SavedState(superState);
        ss.text = mText != null ? mText.toString() : "";
        ss.selStart = getSelectionStart();
        ss.selEnd = getSelectionEnd();
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        if (ss.text != null) setText(ss.text);
        if (ss.selStart >= 0 && mText instanceof Spannable) {
            int len = mText.length();
            Selection.setSelection((Spannable) mText, Math.min(ss.selStart, len), Math.min(ss.selEnd, len));
        }
    }

    public void setFreezesText(boolean freezesText) { mFreezesText = freezesText; }

    public boolean getFreezesText() { return mFreezesText; }

    public void getFocusedRect(Rect r) {
        if (mLayout == null) {
            super.getFocusedRect(r);
            return;
        }
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (start < 0) {
            super.getFocusedRect(r);
            return;
        }
        int line = mLayout.getLineForOffset(start);
        r.top = mLayout.getLineTop(line);
        r.bottom = mLayout.getLineBottom(line);
        r.left = (int) mLayout.getPrimaryHorizontal(start);
        r.right = end == start ? r.left + 1 : (int) mLayout.getPrimaryHorizontal(end);
        r.offset(getCompoundPaddingLeft(), getExtendedPaddingTop() + verticalOffset(true));
    }

    public void beginBatchEdit() {}

    public void endBatchEdit() {}

    private static final class EditableInputConnection extends BaseInputConnection {
        private final TextView mTextView;

        EditableInputConnection(TextView textView) {
            super(textView, true);
            mTextView = textView;
        }

        @Override
        public Editable getEditable() { return mTextView.getEditableText(); }

        @Override
        public boolean beginBatchEdit() {
            mTextView.beginBatchEdit();
            return true;
        }

        @Override
        public boolean endBatchEdit() {
            mTextView.endBatchEdit();
            return true;
        }
    }

    public void onBeginBatchEdit() {}

    public void onEndBatchEdit() {}

    public boolean onPrivateIMECommand(String action, Bundle data) { return false; }

    public boolean isSuggestionsEnabled() { return false; }

    public boolean didTouchFocusSelect() { return false; }

    public void debug(int depth) {}

    /** State frozen when {@link #getFreezesText()} is true. */
    public static class SavedState extends BaseSavedState {
        String text;
        int selStart = -1;
        int selEnd = -1;

        SavedState(Parcelable superState) { super(superState); }

        private SavedState(Parcel in) {
            super(in);
            text = in.readString();
            selStart = in.readInt();
            selEnd = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeString(text);
            out.writeInt(selStart);
            out.writeInt(selEnd);
        }

        @Override
        public String toString() { return "TextView.SavedState{" + text + " " + selStart + "," + selEnd + "}"; }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) { return new SavedState(in); }

            public SavedState[] newArray(int size) { return new SavedState[size]; }
        };
    }

    private class ChangeWatcher implements TextWatcher, android.text.SpanWatcher {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            sendBeforeTextChanged(s, start, count, after);
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            sendOnTextChanged(s, start, before, count);
            if (!(mLayout instanceof android.text.DynamicLayout)) {
                mLayout = null;
                mHintLayout = null;
            }
            requestLayout();
            invalidate();
        }

        public void afterTextChanged(Editable s) { sendAfterTextChanged(s); }

        public void onSpanAdded(Spannable s, Object what, int start, int end) { spanChanged(what); }

        public void onSpanRemoved(Spannable s, Object what, int start, int end) { spanChanged(what); }

        public void onSpanChanged(Spannable s, Object what, int ostart, int oend, int nstart, int nend) {
            spanChanged(what);
        }

        private void spanChanged(Object what) {
            if (what == Selection.SELECTION_END) {
                onSelectionChanged(getSelectionStart(), getSelectionEnd());
            }
            if (what instanceof UpdateLayout) {
                mLayout = null;
                requestLayout();
                invalidate();
            } else if (what instanceof UpdateAppearance || what == Selection.SELECTION_START
                    || what == Selection.SELECTION_END) {
                invalidate();
            }
        }
    }

    private static final class DigitsFilter implements InputFilter {
        private final String mDigits;

        DigitsFilter(String digits) { mDigits = digits; }

        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            boolean all = true;
            for (int i = start; i < end; i++) {
                if (mDigits.indexOf(source.charAt(i)) < 0) {
                    all = false;
                    break;
                }
            }
            if (all) return null;
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                if (mDigits.indexOf(c) >= 0) sb.append(c);
            }
            return sb;
        }
    }
}
