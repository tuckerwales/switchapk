package android.view;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Log;
import android.util.Property;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityEventSource;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The basic building block of the UI, ported from AOSP View (software
 * rendering only): measure with caching, layout, drawing with transforms and
 * alpha, drawable state, touch with press/click/long-click, keys, focus and
 * focus search, scrolling and scroll bars, attach/detach, saved state and
 * listeners. Field names that apps or AndroidX read by reflection keep their
 * AOSP names (mListenerInfo, mAttachInfo, mLayoutParams, mMinWidth,
 * mMinHeight, mID, mParent, mPrivateFlags, mViewFlags).
 */
public class View implements Drawable.Callback, KeyEvent.Callback, AccessibilityEventSource {
    public static final int ACCESSIBILITY_DATA_SENSITIVE_AUTO = 0;
    public static final int ACCESSIBILITY_DATA_SENSITIVE_NO = 2;
    public static final int ACCESSIBILITY_DATA_SENSITIVE_YES = 1;
    public static final int ACCESSIBILITY_LIVE_REGION_ASSERTIVE = 2;
    public static final int ACCESSIBILITY_LIVE_REGION_NONE = 0;
    public static final int ACCESSIBILITY_LIVE_REGION_POLITE = 1;
    public static final int AUTOFILL_FLAG_INCLUDE_NOT_IMPORTANT_VIEWS = 1;
    public static final String AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_DATE = "creditCardExpirationDate";
    public static final String AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_DAY = "creditCardExpirationDay";
    public static final String AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_MONTH = "creditCardExpirationMonth";
    public static final String AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_YEAR = "creditCardExpirationYear";
    public static final String AUTOFILL_HINT_CREDIT_CARD_NUMBER = "creditCardNumber";
    public static final String AUTOFILL_HINT_CREDIT_CARD_SECURITY_CODE = "creditCardSecurityCode";
    public static final String AUTOFILL_HINT_EMAIL_ADDRESS = "emailAddress";
    public static final String AUTOFILL_HINT_NAME = "name";
    public static final String AUTOFILL_HINT_PASSWORD = "password";
    public static final String AUTOFILL_HINT_PHONE = "phone";
    public static final String AUTOFILL_HINT_POSTAL_ADDRESS = "postalAddress";
    public static final String AUTOFILL_HINT_POSTAL_CODE = "postalCode";
    public static final String AUTOFILL_HINT_USERNAME = "username";
    public static final int AUTOFILL_TYPE_DATE = 4;
    public static final int AUTOFILL_TYPE_LIST = 3;
    public static final int AUTOFILL_TYPE_NONE = 0;
    public static final int AUTOFILL_TYPE_TEXT = 1;
    public static final int AUTOFILL_TYPE_TOGGLE = 2;
    public static final int CONTENT_SENSITIVITY_AUTO = 0;
    public static final int CONTENT_SENSITIVITY_NOT_SENSITIVE = 2;
    public static final int CONTENT_SENSITIVITY_SENSITIVE = 1;
    public static final int DRAG_FLAG_ACCESSIBILITY_ACTION = 1024;
    public static final int DRAG_FLAG_GLOBAL = 256;
    public static final int DRAG_FLAG_GLOBAL_PERSISTABLE_URI_PERMISSION = 64;
    public static final int DRAG_FLAG_GLOBAL_PREFIX_URI_PERMISSION = 128;
    public static final int DRAG_FLAG_GLOBAL_SAME_APPLICATION = 4096;
    public static final int DRAG_FLAG_GLOBAL_URI_READ = 1;
    public static final int DRAG_FLAG_GLOBAL_URI_WRITE = 2;
    public static final int DRAG_FLAG_OPAQUE = 512;
    public static final int DRAG_FLAG_START_INTENT_SENDER_ON_UNHANDLED_DRAG = 8192;
    public static final int DRAWING_CACHE_QUALITY_AUTO = 0;
    public static final int DRAWING_CACHE_QUALITY_HIGH = 1048576;
    public static final int DRAWING_CACHE_QUALITY_LOW = 524288;
    public static final int FIND_VIEWS_WITH_CONTENT_DESCRIPTION = 2;
    public static final int FIND_VIEWS_WITH_TEXT = 1;
    public static final int FOCUSABLE = 1;
    public static final int FOCUSABLES_ALL = 0;
    public static final int FOCUSABLES_TOUCH_MODE = 1;
    public static final int FOCUSABLE_AUTO = 16;
    public static final int FOCUS_BACKWARD = 1;
    public static final int FOCUS_DOWN = 130;
    public static final int FOCUS_FORWARD = 2;
    public static final int FOCUS_LEFT = 17;
    public static final int FOCUS_RIGHT = 66;
    public static final int FOCUS_UP = 33;
    public static final int GONE = 8;
    public static final int HAPTIC_FEEDBACK_ENABLED = 268435456;
    public static final int IMPORTANT_FOR_ACCESSIBILITY_AUTO = 0;
    public static final int IMPORTANT_FOR_ACCESSIBILITY_NO = 2;
    public static final int IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS = 4;
    public static final int IMPORTANT_FOR_ACCESSIBILITY_YES = 1;
    public static final int IMPORTANT_FOR_AUTOFILL_AUTO = 0;
    public static final int IMPORTANT_FOR_AUTOFILL_NO = 2;
    public static final int IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS = 8;
    public static final int IMPORTANT_FOR_AUTOFILL_YES = 1;
    public static final int IMPORTANT_FOR_AUTOFILL_YES_EXCLUDE_DESCENDANTS = 4;
    public static final int IMPORTANT_FOR_CONTENT_CAPTURE_AUTO = 0;
    public static final int IMPORTANT_FOR_CONTENT_CAPTURE_NO = 2;
    public static final int IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS = 8;
    public static final int IMPORTANT_FOR_CONTENT_CAPTURE_YES = 1;
    public static final int IMPORTANT_FOR_CONTENT_CAPTURE_YES_EXCLUDE_DESCENDANTS = 4;
    public static final int INVISIBLE = 4;
    public static final int KEEP_SCREEN_ON = 67108864;
    public static final int LAYER_TYPE_HARDWARE = 2;
    public static final int LAYER_TYPE_NONE = 0;
    public static final int LAYER_TYPE_SOFTWARE = 1;
    public static final int LAYOUT_DIRECTION_INHERIT = 2;
    public static final int LAYOUT_DIRECTION_LOCALE = 3;
    public static final int LAYOUT_DIRECTION_LTR = 0;
    public static final int LAYOUT_DIRECTION_RTL = 1;
    public static final int MEASURED_HEIGHT_STATE_SHIFT = 16;
    public static final int MEASURED_SIZE_MASK = 16777215;
    public static final int MEASURED_STATE_MASK = -16777216;
    public static final int MEASURED_STATE_TOO_SMALL = 16777216;
    public static final int NOT_FOCUSABLE = 0;
    public static final int NO_ID = -1;
    public static final int OVER_SCROLL_ALWAYS = 0;
    public static final int OVER_SCROLL_IF_CONTENT_SCROLLS = 1;
    public static final int OVER_SCROLL_NEVER = 2;
    public static final float REQUESTED_FRAME_RATE_CATEGORY_DEFAULT = Float.NaN;
    public static final float REQUESTED_FRAME_RATE_CATEGORY_HIGH = -4.0f;
    public static final float REQUESTED_FRAME_RATE_CATEGORY_LOW = -2.0f;
    public static final float REQUESTED_FRAME_RATE_CATEGORY_NORMAL = -3.0f;
    public static final float REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE = -1.0f;
    public static final int SCREEN_STATE_OFF = 0;
    public static final int SCREEN_STATE_ON = 1;
    public static final int SCROLLBARS_INSIDE_INSET = 16777216;
    public static final int SCROLLBARS_INSIDE_OVERLAY = 0;
    public static final int SCROLLBARS_OUTSIDE_INSET = 50331648;
    public static final int SCROLLBARS_OUTSIDE_OVERLAY = 33554432;
    public static final int SCROLLBAR_POSITION_DEFAULT = 0;
    public static final int SCROLLBAR_POSITION_LEFT = 1;
    public static final int SCROLLBAR_POSITION_RIGHT = 2;
    public static final int SCROLL_AXIS_HORIZONTAL = 1;
    public static final int SCROLL_AXIS_NONE = 0;
    public static final int SCROLL_AXIS_VERTICAL = 2;
    public static final int SCROLL_CAPTURE_HINT_AUTO = 0;
    public static final int SCROLL_CAPTURE_HINT_EXCLUDE = 1;
    public static final int SCROLL_CAPTURE_HINT_EXCLUDE_DESCENDANTS = 4;
    public static final int SCROLL_CAPTURE_HINT_INCLUDE = 2;
    public static final int SCROLL_INDICATOR_BOTTOM = 2;
    public static final int SCROLL_INDICATOR_END = 32;
    public static final int SCROLL_INDICATOR_LEFT = 4;
    public static final int SCROLL_INDICATOR_RIGHT = 8;
    public static final int SCROLL_INDICATOR_START = 16;
    public static final int SCROLL_INDICATOR_TOP = 1;
    public static final int SOUND_EFFECTS_ENABLED = 134217728;
    public static final int STATUS_BAR_HIDDEN = 1;
    public static final int STATUS_BAR_VISIBLE = 0;
    public static final int SYSTEM_UI_FLAG_FULLSCREEN = 4;
    public static final int SYSTEM_UI_FLAG_HIDE_NAVIGATION = 2;
    public static final int SYSTEM_UI_FLAG_IMMERSIVE = 2048;
    public static final int SYSTEM_UI_FLAG_IMMERSIVE_STICKY = 4096;
    public static final int SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN = 1024;
    public static final int SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION = 512;
    public static final int SYSTEM_UI_FLAG_LAYOUT_STABLE = 256;
    public static final int SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR = 16;
    public static final int SYSTEM_UI_FLAG_LIGHT_STATUS_BAR = 8192;
    public static final int SYSTEM_UI_FLAG_LOW_PROFILE = 1;
    public static final int SYSTEM_UI_FLAG_VISIBLE = 0;
    public static final int SYSTEM_UI_LAYOUT_FLAGS = 1536;
    public static final int TEXT_ALIGNMENT_CENTER = 4;
    public static final int TEXT_ALIGNMENT_GRAVITY = 1;
    public static final int TEXT_ALIGNMENT_INHERIT = 0;
    public static final int TEXT_ALIGNMENT_TEXT_END = 3;
    public static final int TEXT_ALIGNMENT_TEXT_START = 2;
    public static final int TEXT_ALIGNMENT_VIEW_END = 6;
    public static final int TEXT_ALIGNMENT_VIEW_START = 5;
    public static final int TEXT_DIRECTION_ANY_RTL = 2;
    public static final int TEXT_DIRECTION_FIRST_STRONG = 1;
    public static final int TEXT_DIRECTION_FIRST_STRONG_LTR = 6;
    public static final int TEXT_DIRECTION_FIRST_STRONG_RTL = 7;
    public static final int TEXT_DIRECTION_INHERIT = 0;
    public static final int TEXT_DIRECTION_LOCALE = 5;
    public static final int TEXT_DIRECTION_LTR = 3;
    public static final int TEXT_DIRECTION_RTL = 4;
    protected static final String VIEW_LOG_TAG = "View";
    public static final int VISIBLE = 0;
    protected static final int[] EMPTY_STATE_SET = {};
    protected static final int[] ENABLED_FOCUSED_SELECTED_STATE_SET = {android.R.attr.state_enabled, android.R.attr.state_focused, android.R.attr.state_selected};
    protected static final int[] ENABLED_FOCUSED_SELECTED_WINDOW_FOCUSED_STATE_SET = {android.R.attr.state_enabled, android.R.attr.state_focused, android.R.attr.state_selected, android.R.attr.state_window_focused};
    protected static final int[] ENABLED_FOCUSED_STATE_SET = {android.R.attr.state_enabled, android.R.attr.state_focused};
    protected static final int[] ENABLED_FOCUSED_WINDOW_FOCUSED_STATE_SET = {android.R.attr.state_enabled, android.R.attr.state_focused, android.R.attr.state_window_focused};
    protected static final int[] ENABLED_SELECTED_STATE_SET = {android.R.attr.state_enabled, android.R.attr.state_selected};
    protected static final int[] ENABLED_SELECTED_WINDOW_FOCUSED_STATE_SET = {android.R.attr.state_enabled, android.R.attr.state_selected, android.R.attr.state_window_focused};
    protected static final int[] ENABLED_STATE_SET = {android.R.attr.state_enabled};
    protected static final int[] ENABLED_WINDOW_FOCUSED_STATE_SET = {android.R.attr.state_enabled, android.R.attr.state_window_focused};
    protected static final int[] FOCUSED_SELECTED_STATE_SET = {android.R.attr.state_focused, android.R.attr.state_selected};
    protected static final int[] FOCUSED_SELECTED_WINDOW_FOCUSED_STATE_SET = {android.R.attr.state_focused, android.R.attr.state_selected, android.R.attr.state_window_focused};
    protected static final int[] FOCUSED_STATE_SET = {android.R.attr.state_focused};
    protected static final int[] FOCUSED_WINDOW_FOCUSED_STATE_SET = {android.R.attr.state_focused, android.R.attr.state_window_focused};
    protected static final int[] PRESSED_ENABLED_FOCUSED_SELECTED_STATE_SET = {android.R.attr.state_pressed, android.R.attr.state_enabled, android.R.attr.state_focused, android.R.attr.state_selected};
    protected static final int[] PRESSED_ENABLED_FOCUSED_SELECTED_WINDOW_FOCUSED_STATE_SET = {android.R.attr.state_pressed, android.R.attr.state_enabled, android.R.attr.state_focused, android.R.attr.state_selected, android.R.attr.state_window_focused};
    protected static final int[] PRESSED_ENABLED_FOCUSED_STATE_SET = {android.R.attr.state_pressed, android.R.attr.state_enabled, android.R.attr.state_focused};
    protected static final int[] PRESSED_ENABLED_FOCUSED_WINDOW_FOCUSED_STATE_SET = {android.R.attr.state_pressed, android.R.attr.state_enabled, android.R.attr.state_focused, android.R.attr.state_window_focused};
    protected static final int[] PRESSED_ENABLED_SELECTED_STATE_SET = {android.R.attr.state_pressed, android.R.attr.state_enabled, android.R.attr.state_selected};
    protected static final int[] PRESSED_ENABLED_SELECTED_WINDOW_FOCUSED_STATE_SET = {android.R.attr.state_pressed, android.R.attr.state_enabled, android.R.attr.state_selected, android.R.attr.state_window_focused};
    protected static final int[] PRESSED_ENABLED_STATE_SET = {android.R.attr.state_pressed, android.R.attr.state_enabled};
    protected static final int[] PRESSED_ENABLED_WINDOW_FOCUSED_STATE_SET = {android.R.attr.state_pressed, android.R.attr.state_enabled, android.R.attr.state_window_focused};
    protected static final int[] PRESSED_FOCUSED_SELECTED_STATE_SET = {android.R.attr.state_pressed, android.R.attr.state_focused, android.R.attr.state_selected};
    protected static final int[] PRESSED_FOCUSED_SELECTED_WINDOW_FOCUSED_STATE_SET = {android.R.attr.state_pressed, android.R.attr.state_focused, android.R.attr.state_selected, android.R.attr.state_window_focused};
    protected static final int[] PRESSED_FOCUSED_STATE_SET = {android.R.attr.state_pressed, android.R.attr.state_focused};
    protected static final int[] PRESSED_FOCUSED_WINDOW_FOCUSED_STATE_SET = {android.R.attr.state_pressed, android.R.attr.state_focused, android.R.attr.state_window_focused};
    protected static final int[] PRESSED_SELECTED_STATE_SET = {android.R.attr.state_pressed, android.R.attr.state_selected};
    protected static final int[] PRESSED_SELECTED_WINDOW_FOCUSED_STATE_SET = {android.R.attr.state_pressed, android.R.attr.state_selected, android.R.attr.state_window_focused};
    protected static final int[] PRESSED_STATE_SET = {android.R.attr.state_pressed};
    protected static final int[] PRESSED_WINDOW_FOCUSED_STATE_SET = {android.R.attr.state_pressed, android.R.attr.state_window_focused};
    protected static final int[] SELECTED_STATE_SET = {android.R.attr.state_selected};
    protected static final int[] SELECTED_WINDOW_FOCUSED_STATE_SET = {android.R.attr.state_selected, android.R.attr.state_window_focused};
    protected static final int[] WINDOW_FOCUSED_STATE_SET = {android.R.attr.state_window_focused};


    // ---------------------------------------------------------------- flags (mViewFlags)

    static final int FOCUSABLE_MASK = 0x00000011;
    static final int FITS_SYSTEM_WINDOWS = 0x00000002;
    static final int VISIBILITY_MASK = 0x0000000C;
    static final int ENABLED = 0x00000000;
    static final int DISABLED = 0x00000020;
    static final int ENABLED_MASK = 0x00000020;
    static final int WILL_NOT_DRAW = 0x00000080;
    static final int DRAW_MASK = 0x00000080;
    static final int SCROLLBARS_NONE = 0x00000000;
    static final int SCROLLBARS_HORIZONTAL = 0x00000100;
    static final int SCROLLBARS_VERTICAL = 0x00000200;
    static final int SCROLLBARS_MASK = 0x00000300;
    static final int FILTER_TOUCHES_WHEN_OBSCURED = 0x00000400;
    static final int OPTIONAL_FITS_SYSTEM_WINDOWS = 0x00000800;
    static final int FADING_EDGE_NONE = 0x00000000;
    static final int FADING_EDGE_HORIZONTAL = 0x00001000;
    static final int FADING_EDGE_VERTICAL = 0x00002000;
    static final int FADING_EDGE_MASK = 0x00003000;
    static final int CLICKABLE = 0x00004000;
    static final int DRAWING_CACHE_ENABLED = 0x00008000;
    static final int SAVE_DISABLED = 0x00010000;
    static final int SAVE_DISABLED_MASK = 0x00010000;
    static final int WILL_NOT_CACHE_DRAWING = 0x00020000;
    static final int FOCUSABLE_IN_TOUCH_MODE = 0x00040000;
    static final int LONG_CLICKABLE = 0x00200000;
    static final int DUPLICATE_PARENT_STATE = 0x00400000;
    static final int CONTEXT_CLICKABLE = 0x00800000;
    static final int SCROLLBARS_STYLE_MASK = 0x03000000;
    static final int SCROLLBARS_INSET_MASK = 0x01000000;
    static final int SCROLLBARS_OUTSIDE_MASK = 0x02000000;
    static final int PARENT_SAVE_DISABLED = 0x20000000;
    static final int TOOLTIP = 0x40000000;

    // ---------------------------------------------------------------- private flags (mPrivateFlags)

    static final int PFLAG_WANTS_FOCUS = 0x00000001;
    static final int PFLAG_FOCUSED = 0x00000002;
    static final int PFLAG_SELECTED = 0x00000004;
    static final int PFLAG_IS_ROOT_NAMESPACE = 0x00000008;
    static final int PFLAG_HAS_BOUNDS = 0x00000010;
    static final int PFLAG_DRAWN = 0x00000020;
    static final int PFLAG_DRAW_ANIMATION = 0x00000040;
    static final int PFLAG_SKIP_DRAW = 0x00000080;
    static final int PFLAG_REQUEST_TRANSPARENT_REGIONS = 0x00000200;
    static final int PFLAG_DRAWABLE_STATE_DIRTY = 0x00000400;
    static final int PFLAG_MEASURED_DIMENSION_SET = 0x00000800;
    static final int PFLAG_FORCE_LAYOUT = 0x00001000;
    static final int PFLAG_LAYOUT_REQUIRED = 0x00002000;
    static final int PFLAG_PRESSED = 0x00004000;
    static final int PFLAG_DRAWING_CACHE_VALID = 0x00008000;
    static final int PFLAG_ANIMATION_STARTED = 0x00010000;
    static final int PFLAG_SAVE_STATE_CALLED = 0x00020000;
    static final int PFLAG_ALPHA_SET = 0x00040000;
    static final int PFLAG_SCROLL_CONTAINER = 0x00080000;
    static final int PFLAG_SCROLL_CONTAINER_ADDED = 0x00100000;
    static final int PFLAG_DIRTY = 0x00200000;
    static final int PFLAG_OPAQUE_BACKGROUND = 0x00800000;
    static final int PFLAG_OPAQUE_SCROLLBARS = 0x01000000;
    static final int PFLAG_PREPRESSED = 0x02000000;
    static final int PFLAG_CANCEL_NEXT_UP_EVENT = 0x04000000;
    static final int PFLAG_HOVERED = 0x10000000;
    static final int PFLAG_PIVOT_EXPLICITLY_SET = 0x20000000;
    static final int PFLAG_ACTIVATED = 0x40000000;
    static final int PFLAG_INVALIDATED = 0x80000000;

    // mPrivateFlags3
    static final int PFLAG3_VIEW_IS_ANIMATING_TRANSFORM = 0x1;
    static final int PFLAG3_IS_LAID_OUT = 0x4;
    static final int PFLAG3_MEASURE_NEEDED_BEFORE_LAYOUT = 0x8;
    static final int PFLAG3_CALLED_SUPER = 0x10;
    static final int PFLAG3_NESTED_SCROLLING_ENABLED = 0x80;
    static final int PFLAG3_SCROLL_INDICATOR_SHIFT = 8;
    static final int PFLAG3_SCROLL_INDICATOR_MASK = 0x3f00;
    static final int PFLAG3_ASSIST_BLOCKED = 0x4000;
    static final int PFLAG3_FINGER_DOWN = 0x20000;
    static final int PFLAG3_FOCUSED_BY_DEFAULT = 0x40000;
    static final int PFLAG3_OVERLAPPING_RENDERING_FORCED_VALUE = 0x1000000;
    static final int PFLAG3_HAS_OVERLAPPING_RENDERING_FORCED = 0x2000000;
    static final int PFLAG3_TEMPORARY_DETACH = 0x2000000 << 1;
    static final int PFLAG3_NO_REVEAL_ON_FOCUS = 0x8000000;
    static final int PFLAG3_CLUSTER = 0x10000000;
    static final int PFLAG3_IS_AUTOFILLED = 0x20000000;

    static final int UNDEFINED_PADDING = Integer.MIN_VALUE;
    private static final int DEFAULT_FOCUS_DIRECTION = FOCUS_FORWARD;

    private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);
    private static final int[] VISIBILITY_FLAGS = {VISIBLE, INVISIBLE, GONE};

    // ---------------------------------------------------------------- properties

    public static final Property<View, Float> ALPHA = new FloatProperty<View>("alpha") {
        @Override
        public void setValue(View object, float value) { object.setAlpha(value); }

        @Override
        public Float get(View object) { return object.getAlpha(); }
    };

    public static final Property<View, Float> TRANSLATION_X = new FloatProperty<View>("translationX") {
        @Override
        public void setValue(View object, float value) { object.setTranslationX(value); }

        @Override
        public Float get(View object) { return object.getTranslationX(); }
    };

    public static final Property<View, Float> TRANSLATION_Y = new FloatProperty<View>("translationY") {
        @Override
        public void setValue(View object, float value) { object.setTranslationY(value); }

        @Override
        public Float get(View object) { return object.getTranslationY(); }
    };

    public static final Property<View, Float> TRANSLATION_Z = new FloatProperty<View>("translationZ") {
        @Override
        public void setValue(View object, float value) { object.setTranslationZ(value); }

        @Override
        public Float get(View object) { return object.getTranslationZ(); }
    };

    public static final Property<View, Float> X = new FloatProperty<View>("x") {
        @Override
        public void setValue(View object, float value) { object.setX(value); }

        @Override
        public Float get(View object) { return object.getX(); }
    };

    public static final Property<View, Float> Y = new FloatProperty<View>("y") {
        @Override
        public void setValue(View object, float value) { object.setY(value); }

        @Override
        public Float get(View object) { return object.getY(); }
    };

    public static final Property<View, Float> Z = new FloatProperty<View>("z") {
        @Override
        public void setValue(View object, float value) { object.setZ(value); }

        @Override
        public Float get(View object) { return object.getZ(); }
    };

    public static final Property<View, Float> ROTATION = new FloatProperty<View>("rotation") {
        @Override
        public void setValue(View object, float value) { object.setRotation(value); }

        @Override
        public Float get(View object) { return object.getRotation(); }
    };

    public static final Property<View, Float> ROTATION_X = new FloatProperty<View>("rotationX") {
        @Override
        public void setValue(View object, float value) { object.setRotationX(value); }

        @Override
        public Float get(View object) { return object.getRotationX(); }
    };

    public static final Property<View, Float> ROTATION_Y = new FloatProperty<View>("rotationY") {
        @Override
        public void setValue(View object, float value) { object.setRotationY(value); }

        @Override
        public Float get(View object) { return object.getRotationY(); }
    };

    public static final Property<View, Float> SCALE_X = new FloatProperty<View>("scaleX") {
        @Override
        public void setValue(View object, float value) { object.setScaleX(value); }

        @Override
        public Float get(View object) { return object.getScaleX(); }
    };

    public static final Property<View, Float> SCALE_Y = new FloatProperty<View>("scaleY") {
        @Override
        public void setValue(View object, float value) { object.setScaleY(value); }

        @Override
        public Float get(View object) { return object.getScaleY(); }
    };

    // ---------------------------------------------------------------- state

    protected Context mContext;
    private final Resources mResources;
    /** read by reflection in some libraries (AOSP name) */
    int mID = NO_ID;
    Object mTag;
    private SparseArray<Object> mKeyedTags;
    /** AOSP name; AndroidX reads it by reflection */
    protected ViewGroup.LayoutParams mLayoutParams;
    protected ViewParent mParent;
    /** AOSP name; AndroidX reads it by reflection */
    AttachInfo mAttachInfo;
    int mPrivateFlags;
    int mPrivateFlags2;
    int mPrivateFlags3;
    int mViewFlags;
    int mWindowAttachCount;

    protected int mLeft;
    protected int mRight;
    protected int mTop;
    protected int mBottom;
    protected int mScrollX;
    protected int mScrollY;
    protected int mPaddingLeft;
    protected int mPaddingRight;
    protected int mPaddingTop;
    protected int mPaddingBottom;
    int mUserPaddingLeft;
    int mUserPaddingRight;
    int mUserPaddingBottom;
    int mUserPaddingStart = UNDEFINED_PADDING;
    int mUserPaddingEnd = UNDEFINED_PADDING;
    int mUserPaddingLeftInitial;
    int mUserPaddingRightInitial;
    boolean mLeftPaddingDefined;
    boolean mRightPaddingDefined;

    int mMeasuredWidth;
    int mMeasuredHeight;
    int mOldWidthMeasureSpec = Integer.MIN_VALUE;
    int mOldHeightMeasureSpec = Integer.MIN_VALUE;
    private HashMap<Long, Long> mMeasureCache;
    /** AOSP names; ViewCompat reads them by reflection on old versions */
    private int mMinWidth;
    private int mMinHeight;

    private Drawable mBackground;
    private ColorStateList mBackgroundTintList;
    private PorterDuff.Mode mBackgroundTintMode;
    private boolean mBackgroundSizeChanged;
    private Drawable mForeground;
    private int mForegroundGravity = Gravity.FILL;
    private boolean mForegroundInPadding = true;
    private ColorStateList mForegroundTintList;
    private PorterDuff.Mode mForegroundTintMode;
    private boolean mForegroundBoundsChanged;
    private int[] mDrawableState;

    /** AOSP name; read by reflection (e.g. hasOnClickListeners compat paths) */
    ListenerInfo mListenerInfo;
    private TouchDelegate mTouchDelegate;
    private boolean mHasPerformedLongPress;
    private boolean mInContextButtonPress;
    private boolean mIgnoreNextUpEvent;
    private CheckForLongPress mPendingCheckForLongPress;
    private CheckForTap mPendingCheckForTap;
    private PerformClick mPerformClick;
    private UnsetPressedState mUnsetPressedState;
    private int mTouchSlop;
    private HandlerActionQueue mRunQueue;
    private CharSequence mContentDescription;
    private CharSequence mTooltipText;
    private CharSequence mStateDescription;
    private int mNextFocusLeftId = NO_ID;
    private int mNextFocusRightId = NO_ID;
    private int mNextFocusUpId = NO_ID;
    private int mNextFocusDownId = NO_ID;
    int mNextFocusForwardId = NO_ID;
    private int mNextClusterForwardId = NO_ID;
    private int mLabelForId = NO_ID;
    private int mAccessibilityTraversalBeforeId = NO_ID;
    private int mAccessibilityTraversalAfterId = NO_ID;
    private int mImportantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_AUTO;
    private int mAccessibilityLiveRegion;
    private CharSequence mAccessibilityPaneTitle;
    private AccessibilityDelegate mAccessibilityDelegate;
    private int mLayoutDirection = LAYOUT_DIRECTION_INHERIT;
    private int mTextDirection = TEXT_DIRECTION_INHERIT;
    private int mTextAlignment = TEXT_ALIGNMENT_INHERIT;
    private int mOverScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS;
    private int mVerticalScrollbarPosition;
    private int mLayerType = LAYER_TYPE_NONE;
    private Paint mLayerPaint;
    private int mDrawingCacheBackgroundColor;
    private Bitmap mDrawingCache;
    private int mSystemUiVisibility;
    private String mTransitionName;
    private Rect mClipBounds;
    private ViewOutlineProvider mOutlineProvider = ViewOutlineProvider.BACKGROUND;
    private boolean mClipToOutline;
    private int mOutlineAmbientShadowColor = 0xff000000;
    private int mOutlineSpotShadowColor = 0xff000000;
    private ViewTreeObserver mFloatingTreeObserver;
    private int mTransientStateCount;
    private ScrollabilityCache mScrollCache;
    private int mImportantForAutofill;
    private String[] mAutofillHints;
    private boolean mDefaultFocusHighlightEnabled = true;
    private boolean mForceDarkAllowed = true;
    private boolean mAllowClickWhenDisabled;
    private List<Rect> mSystemGestureExclusionRects;
    private float mRequestedFrameRate;
    private int mSourceLayoutId;

    TransformationInfo mTransformationInfo;


    private static final String TAG = "View";
    private static Handler sMainHandler;

    // ---------------------------------------------------------------- nested types

    public interface OnClickListener {
        void onClick(View v);
    }

    public interface OnLongClickListener {
        boolean onLongClick(View v);

        default boolean onLongClickUseDefaultHapticFeedback(View v) { return true; }
    }

    public interface OnContextClickListener {
        boolean onContextClick(View v);
    }

    public interface OnKeyListener {
        boolean onKey(View v, int keyCode, KeyEvent event);
    }

    public interface OnTouchListener {
        boolean onTouch(View v, MotionEvent event);
    }

    public interface OnHoverListener {
        boolean onHover(View v, MotionEvent event);
    }

    public interface OnGenericMotionListener {
        boolean onGenericMotion(View v, MotionEvent event);
    }

    public interface OnFocusChangeListener {
        void onFocusChange(View v, boolean hasFocus);
    }

    public interface OnCreateContextMenuListener {
        void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo);
    }

    public interface OnAttachStateChangeListener {
        void onViewAttachedToWindow(View v);
        void onViewDetachedFromWindow(View v);
    }

    public interface OnLayoutChangeListener {
        void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight,
                int oldBottom);
    }

    public interface OnScrollChangeListener {
        void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY);
    }

    public interface OnSystemUiVisibilityChangeListener {
        void onSystemUiVisibilityChange(int visibility);
    }

    public interface OnUnhandledKeyEventListener {
        boolean onUnhandledKeyEvent(View v, KeyEvent event);
    }

    public interface OnApplyWindowInsetsListener {
        WindowInsets onApplyWindowInsets(View v, WindowInsets insets);
    }

    public interface OnCapturedPointerListener {
        boolean onCapturedPointer(View view, MotionEvent event);
    }

    public interface OnDragListener {
        boolean onDrag(View v, DragEvent event);
    }

    static class ListenerInfo {
        OnFocusChangeListener mOnFocusChangeListener;
        ArrayList<OnLayoutChangeListener> mOnLayoutChangeListeners;
        OnScrollChangeListener mOnScrollChangeListener;
        CopyOnWriteArrayList<OnAttachStateChangeListener> mOnAttachStateChangeListeners;
        public OnClickListener mOnClickListener;
        OnLongClickListener mOnLongClickListener;
        OnContextClickListener mOnContextClickListener;
        OnCreateContextMenuListener mOnCreateContextMenuListener;
        OnKeyListener mOnKeyListener;
        OnTouchListener mOnTouchListener;
        OnHoverListener mOnHoverListener;
        OnGenericMotionListener mOnGenericMotionListener;
        OnDragListener mOnDragListener;
        OnSystemUiVisibilityChangeListener mOnSystemUiVisibilityChangeListener;
        OnApplyWindowInsetsListener mOnApplyWindowInsetsListener;
        OnCapturedPointerListener mOnCapturedPointerListener;
        ArrayList<OnUnhandledKeyEventListener> mUnhandledKeyListeners;
    }

    ListenerInfo getListenerInfo() {
        if (mListenerInfo == null) mListenerInfo = new ListenerInfo();
        return mListenerInfo;
    }

    static class TransformationInfo {
        Matrix mMatrix;
        Matrix mInverseMatrix;
        boolean mMatrixDirty;
        boolean mInverseMatrixDirty = true;
        float mTranslationX, mTranslationY, mTranslationZ;
        float mRotation, mRotationX, mRotationY;
        float mScaleX = 1f, mScaleY = 1f;
        float mPivotX, mPivotY;
        float mElevation;
        float mCameraDistance;
        float mAlpha = 1f;
        float mTransitionAlpha = 1f;
        Matrix mAnimationMatrix;
        Camera mCamera;
    }

    private TransformationInfo transformInfo() {
        if (mTransformationInfo == null) mTransformationInfo = new TransformationInfo();
        return mTransformationInfo;
    }

    /** framework-internal. Per-window data shared by all views of a hierarchy (AOSP View.AttachInfo). */
    static final class AttachInfo {
        final ViewRootImpl mViewRootImpl;
        final Handler mHandler;
        final IBinder mWindowToken;
        View mRootView;
        int mWindowVisibility = GONE;
        boolean mHasWindowFocus;
        boolean mInTouchMode = true;
        boolean mHardwareAccelerated;
        boolean mKeepScreenOn;
        long mDrawingTime;
        int mWindowLeft;
        int mWindowTop;
        float mApplicationScale = 1f;
        int mSystemUiVisibility;
        final ViewTreeObserver mTreeObserver;
        final KeyEvent.DispatcherState mKeyDispatchState = new KeyEvent.DispatcherState();
        final ArrayList<View> mScrollContainers = new ArrayList<View>();
        boolean mViewScrollChanged;
        boolean mViewVisibilityChanged;
        final Rect mTmpInvalRect = new Rect();
        final RectF mTmpTransformRect = new RectF();
        final Matrix mTmpMatrix = new Matrix();
        final int[] mTmpLocation = new int[2];
        final float[] mTmpTransformLocation = new float[2];
        /** read by reflection by AndroidX WindowInsetsCompat */
        final Rect mStableInsets = new Rect();
        /** read by reflection by AndroidX WindowInsetsCompat */
        final Rect mContentInsets = new Rect();
        final Rect mVisibleInsets = new Rect();
        Display mDisplay;

        AttachInfo(ViewRootImpl viewRootImpl, Handler handler, IBinder token, Context context) {
            mViewRootImpl = viewRootImpl;
            mHandler = handler;
            mWindowToken = token;
            mTreeObserver = new ViewTreeObserver(context);
            mDisplay = Display.defaultDisplay();
        }
    }

    /** Scroll bar fading state (AOSP ScrollabilityCache, simplified). */
    static class ScrollabilityCache {
        int scrollBarSize;
        int fadingEdgeLength;
        int scrollBarDefaultDelayBeforeFade;
        int scrollBarFadeDuration;
        boolean fadeScrollBars = true;
        Drawable verticalThumb;
        Drawable horizontalThumb;
        Drawable verticalTrack;
        Drawable horizontalTrack;
        long fadeStartTime;
        int state = OFF;
        static final int OFF = 0;
        static final int ON = 1;
        static final int FADING = 2;
    }

    public static class MeasureSpec {
        private static final int MODE_SHIFT = 30;
        private static final int MODE_MASK = 0x3 << MODE_SHIFT;
        public static final int UNSPECIFIED = 0;
        public static final int EXACTLY = 1 << MODE_SHIFT;
        public static final int AT_MOST = 2 << MODE_SHIFT;

        public MeasureSpec() {}

        public static int makeMeasureSpec(int size, int mode) { return (size & ~MODE_MASK) | (mode & MODE_MASK); }

        /** framework-internal (hidden in AOSP). */
        public static int makeSafeMeasureSpec(int size, int mode) {
            if (mode == UNSPECIFIED) return 0;
            return makeMeasureSpec(size, mode);
        }

        public static int getMode(int measureSpec) { return (measureSpec & MODE_MASK); }

        public static int getSize(int measureSpec) { return (measureSpec & ~MODE_MASK); }

        static int adjust(int measureSpec, int delta) {
            final int mode = getMode(measureSpec);
            int size = getSize(measureSpec);
            if (mode == UNSPECIFIED) return makeMeasureSpec(size, UNSPECIFIED);
            size += delta;
            if (size < 0) size = 0;
            return makeMeasureSpec(size, mode);
        }

        public static String toString(int measureSpec) {
            int mode = getMode(measureSpec);
            int size = getSize(measureSpec);
            StringBuilder sb = new StringBuilder("MeasureSpec: ");
            if (mode == UNSPECIFIED) sb.append("UNSPECIFIED ");
            else if (mode == EXACTLY) sb.append("EXACTLY ");
            else if (mode == AT_MOST) sb.append("AT_MOST ");
            else sb.append(mode).append(" ");
            sb.append(size);
            return sb.toString();
        }
    }

    public static class BaseSavedState extends AbsSavedState {
        static final int START_ACTIVITY_REQUESTED_WHO_SAVED = 0b1;
        static final int IS_AUTOFILLED = 0b10;
        static final int AUTOFILL_ID = 0b100;
        int mSavedData;
        String mStartActivityRequestWhoSaved;
        boolean mIsAutofilled;

        public BaseSavedState(Parcel source) { this(source, null); }

        public BaseSavedState(Parcel source, ClassLoader loader) {
            super(source, loader);
            mSavedData = source.readInt();
            mStartActivityRequestWhoSaved = source.readString();
            mIsAutofilled = source.readBoolean();
        }

        public BaseSavedState(Parcelable superState) { super(superState); }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(mSavedData);
            out.writeString(mStartActivityRequestWhoSaved);
            out.writeBoolean(mIsAutofilled);
        }

        public static final Parcelable.Creator<BaseSavedState> CREATOR =
                new Parcelable.ClassLoaderCreator<BaseSavedState>() {
                    public BaseSavedState createFromParcel(Parcel in) { return new BaseSavedState(in); }

                    public BaseSavedState createFromParcel(Parcel in, ClassLoader loader) {
                        return new BaseSavedState(in, loader);
                    }

                    public BaseSavedState[] newArray(int size) { return new BaseSavedState[size]; }
                };
    }

    public static class AccessibilityDelegate {
        public AccessibilityDelegate() {}

        public void sendAccessibilityEvent(View host, int eventType) { host.sendAccessibilityEventInternal(eventType); }

        public boolean performAccessibilityAction(View host, int action, Bundle args) {
            return host.performAccessibilityActionInternal(action, args);
        }

        public void sendAccessibilityEventUnchecked(View host, AccessibilityEvent event) {
            host.sendAccessibilityEventUncheckedInternal(event);
        }

        public boolean dispatchPopulateAccessibilityEvent(View host, AccessibilityEvent event) {
            return host.dispatchPopulateAccessibilityEventInternal(event);
        }

        public void onPopulateAccessibilityEvent(View host, AccessibilityEvent event) {
            host.onPopulateAccessibilityEventInternal(event);
        }

        public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
            host.onInitializeAccessibilityEventInternal(event);
        }

        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
            host.onInitializeAccessibilityNodeInfoInternal(info);
        }

        public void addExtraDataToAccessibilityNodeInfo(View host, AccessibilityNodeInfo info, String extraDataKey,
                Bundle arguments) {
            host.addExtraDataToAccessibilityNodeInfo(info, extraDataKey, arguments);
        }

        public boolean onRequestSendAccessibilityEvent(ViewGroup host, View child, AccessibilityEvent event) {
            return host.onRequestSendAccessibilityEventInternal(child, event);
        }

        public AccessibilityNodeProvider getAccessibilityNodeProvider(View host) { return null; }

        /** framework-internal (hidden in AOSP). */
        public AccessibilityNodeInfo createAccessibilityNodeInfo(View host) {
            return host.createAccessibilityNodeInfoInternal();
        }
    }

    public static class DragShadowBuilder {
        private final java.lang.ref.WeakReference<View> mView;

        public DragShadowBuilder(View view) { mView = new java.lang.ref.WeakReference<View>(view); }

        public DragShadowBuilder() { mView = new java.lang.ref.WeakReference<View>(null); }

        public final View getView() { return mView.get(); }

        public void onProvideShadowMetrics(Point outShadowSize, Point outShadowTouchPoint) {
            final View view = mView.get();
            if (view != null) {
                outShadowSize.set(view.getWidth(), view.getHeight());
                outShadowTouchPoint.set(outShadowSize.x / 2, outShadowSize.y / 2);
            }
        }

        public void onDrawShadow(Canvas canvas) {
            final View view = mView.get();
            if (view != null) view.draw(canvas);
        }
    }

    /** Runnables posted before the view is attached (AOSP HandlerActionQueue). */
    static class HandlerActionQueue {
        private final ArrayList<Object[]> mActions = new ArrayList<Object[]>();

        void post(Runnable action) { postDelayed(action, 0); }

        void postDelayed(Runnable action, long delayMillis) { mActions.add(new Object[] {action, delayMillis}); }

        void removeCallbacks(Runnable action) {
            for (int i = mActions.size() - 1; i >= 0; i--) {
                if (mActions.get(i)[0] == action) mActions.remove(i);
            }
        }

        void executeActions(Handler handler) {
            for (Object[] a : mActions) handler.postDelayed((Runnable) a[0], (Long) a[1]);
            mActions.clear();
        }
    }

    // ---------------------------------------------------------------- construction

    /** framework-internal: View attributes read by the constructor. */
    private static final int[] VIEW_ATTRS = {
        android.R.attr.background, android.R.attr.padding, android.R.attr.paddingLeft, android.R.attr.paddingTop,
        android.R.attr.paddingRight, android.R.attr.paddingBottom, android.R.attr.paddingStart,
        android.R.attr.paddingEnd, android.R.attr.paddingHorizontal, android.R.attr.paddingVertical,
        android.R.attr.id, android.R.attr.tag, android.R.attr.scrollX, android.R.attr.scrollY, android.R.attr.alpha,
        android.R.attr.transformPivotX, android.R.attr.transformPivotY, android.R.attr.translationX,
        android.R.attr.translationY, android.R.attr.translationZ, android.R.attr.elevation, android.R.attr.rotation,
        android.R.attr.rotationX, android.R.attr.rotationY, android.R.attr.scaleX, android.R.attr.scaleY,
        android.R.attr.fitsSystemWindows, android.R.attr.focusable, android.R.attr.focusableInTouchMode,
        android.R.attr.clickable, android.R.attr.longClickable, android.R.attr.contextClickable,
        android.R.attr.saveEnabled, android.R.attr.duplicateParentState, android.R.attr.visibility,
        android.R.attr.layoutDirection, android.R.attr.drawingCacheQuality, android.R.attr.contentDescription,
        android.R.attr.tooltipText, android.R.attr.soundEffectsEnabled, android.R.attr.hapticFeedbackEnabled,
        android.R.attr.scrollbars, android.R.attr.fadingEdge, android.R.attr.requiresFadingEdge,
        android.R.attr.fadingEdgeLength, android.R.attr.scrollbarStyle, android.R.attr.isScrollContainer,
        android.R.attr.keepScreenOn, android.R.attr.filterTouchesWhenObscured, android.R.attr.nextFocusLeft,
        android.R.attr.nextFocusRight, android.R.attr.nextFocusUp, android.R.attr.nextFocusDown,
        android.R.attr.nextFocusForward, android.R.attr.minWidth, android.R.attr.minHeight, android.R.attr.onClick,
        android.R.attr.overScrollMode, android.R.attr.verticalScrollbarPosition, android.R.attr.layerType,
        android.R.attr.textDirection, android.R.attr.textAlignment, android.R.attr.importantForAccessibility,
        android.R.attr.foreground, android.R.attr.foregroundGravity, android.R.attr.foregroundTint,
        android.R.attr.foregroundTintMode, android.R.attr.backgroundTint, android.R.attr.backgroundTintMode,
        android.R.attr.outlineProvider, android.R.attr.focusedByDefault,
        android.R.attr.defaultFocusHighlightEnabled, android.R.attr.enabled,
        android.R.attr.forceHasOverlappingRendering, android.R.attr.scrollIndicators,
        android.R.attr.keyboardNavigationCluster, android.R.attr.accessibilityHeading,
        android.R.attr.screenReaderFocusable, android.R.attr.transitionName, android.R.attr.labelFor,
        android.R.attr.fadeScrollbars, android.R.attr.scrollbarSize, android.R.attr.scrollbarThumbVertical,
        android.R.attr.scrollbarThumbHorizontal, android.R.attr.scrollbarTrackVertical,
        android.R.attr.scrollbarTrackHorizontal, android.R.attr.scrollbarFadeDuration,
        android.R.attr.scrollbarDefaultDelayBeforeFade, android.R.attr.accessibilityLiveRegion,
        android.R.attr.accessibilityTraversalBefore, android.R.attr.accessibilityTraversalAfter,
        android.R.attr.nextClusterForward, android.R.attr.importantForAutofill, android.R.attr.autofillHints,
        android.R.attr.outlineSpotShadowColor, android.R.attr.outlineAmbientShadowColor,
        android.R.attr.forceDarkAllowed, android.R.attr.accessibilityPaneTitle,
    };

    public View(Context context) {
        mContext = context;
        mResources = context != null ? context.getResources() : null;
        mViewFlags = SOUND_EFFECTS_ENABLED | HAPTIC_FEEDBACK_ENABLED | FOCUSABLE_AUTO;
        mTouchSlop = context != null ? ViewConfiguration.get(context).getScaledTouchSlop() : 8;
        mUserPaddingStart = UNDEFINED_PADDING;
        mUserPaddingEnd = UNDEFINED_PADDING;
    }

    public View(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public View(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public View(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        this(context);
        if (attrs == null && defStyleAttr == 0 && defStyleRes == 0) {
            computeOpaqueFlags();
            return;
        }
        if (attrs instanceof android.content.res.XmlResourceParser) {
            mSourceLayoutId = Resources.getAttributeSetSourceResId(attrs);
        }
        final TypedArray a = context.obtainStyledAttributes(attrs, VIEW_ATTRS, defStyleAttr, defStyleRes);
        Drawable background = null;
        int leftPadding = -1, topPadding = -1, rightPadding = -1, bottomPadding = -1;
        int startPadding = UNDEFINED_PADDING, endPadding = UNDEFINED_PADDING;
        int padding = -1, paddingHorizontal = -1, paddingVertical = -1;
        int viewFlagValues = 0;
        int viewFlagMasks = 0;
        boolean setScrollContainer = false;
        int x = 0, y = 0;
        float tx = 0, ty = 0, tz = 0, elevation = 0, rotation = 0, rotationX = 0, rotationY = 0;
        float sx = 1f, sy = 1f;
        boolean transformSet = false;
        int scrollbarStyle = SCROLLBARS_INSIDE_OVERLAY;
        int overScrollMode = mOverScrollMode;
        boolean initializeScrollbars = false;
        boolean leftPaddingDefined = false, rightPaddingDefined = false;
        boolean startPaddingDefined = false, endPaddingDefined = false;
        int scrollbarSize = -1, fadeDuration = -1, fadeDelay = -1;
        Boolean fadeScrollbars = null;
        Drawable vThumb = null, hThumb = null, vTrack = null, hTrack = null;

        final int n = a.getIndexCount();
        for (int i = 0; i < n; i++) {
            int index = a.getIndex(i);
            int attr = VIEW_ATTRS[index];
            switch (attr) {
                case android.R.attr.background:
                    background = a.getDrawable(index);
                    break;
                case android.R.attr.padding:
                    padding = a.getDimensionPixelSize(index, -1);
                    break;
                case android.R.attr.paddingHorizontal:
                    paddingHorizontal = a.getDimensionPixelSize(index, -1);
                    break;
                case android.R.attr.paddingVertical:
                    paddingVertical = a.getDimensionPixelSize(index, -1);
                    break;
                case android.R.attr.paddingLeft:
                    leftPadding = a.getDimensionPixelSize(index, -1);
                    break;
                case android.R.attr.paddingTop:
                    topPadding = a.getDimensionPixelSize(index, -1);
                    break;
                case android.R.attr.paddingRight:
                    rightPadding = a.getDimensionPixelSize(index, -1);
                    break;
                case android.R.attr.paddingBottom:
                    bottomPadding = a.getDimensionPixelSize(index, -1);
                    break;
                case android.R.attr.paddingStart:
                    startPadding = a.getDimensionPixelSize(index, UNDEFINED_PADDING);
                    startPaddingDefined = startPadding != UNDEFINED_PADDING;
                    break;
                case android.R.attr.paddingEnd:
                    endPadding = a.getDimensionPixelSize(index, UNDEFINED_PADDING);
                    endPaddingDefined = endPadding != UNDEFINED_PADDING;
                    break;
                case android.R.attr.scrollX:
                    x = a.getDimensionPixelOffset(index, 0);
                    break;
                case android.R.attr.scrollY:
                    y = a.getDimensionPixelOffset(index, 0);
                    break;
                case android.R.attr.alpha:
                    setAlpha(a.getFloat(index, 1f));
                    break;
                case android.R.attr.transformPivotX:
                    setPivotX(a.getDimension(index, 0));
                    break;
                case android.R.attr.transformPivotY:
                    setPivotY(a.getDimension(index, 0));
                    break;
                case android.R.attr.translationX:
                    tx = a.getDimension(index, 0);
                    transformSet = true;
                    break;
                case android.R.attr.translationY:
                    ty = a.getDimension(index, 0);
                    transformSet = true;
                    break;
                case android.R.attr.translationZ:
                    tz = a.getDimension(index, 0);
                    transformSet = true;
                    break;
                case android.R.attr.elevation:
                    elevation = a.getDimension(index, 0);
                    transformSet = true;
                    break;
                case android.R.attr.rotation:
                    rotation = a.getFloat(index, 0);
                    transformSet = true;
                    break;
                case android.R.attr.rotationX:
                    rotationX = a.getFloat(index, 0);
                    transformSet = true;
                    break;
                case android.R.attr.rotationY:
                    rotationY = a.getFloat(index, 0);
                    transformSet = true;
                    break;
                case android.R.attr.scaleX:
                    sx = a.getFloat(index, 1f);
                    transformSet = true;
                    break;
                case android.R.attr.scaleY:
                    sy = a.getFloat(index, 1f);
                    transformSet = true;
                    break;
                case android.R.attr.id:
                    mID = a.getResourceId(index, NO_ID);
                    break;
                case android.R.attr.tag:
                    mTag = a.getText(index);
                    break;
                case android.R.attr.fitsSystemWindows:
                    if (a.getBoolean(index, false)) {
                        viewFlagValues |= FITS_SYSTEM_WINDOWS;
                        viewFlagMasks |= FITS_SYSTEM_WINDOWS;
                    }
                    break;
                case android.R.attr.focusable: {
                    int f = getFocusableAttribute(a, index);
                    viewFlagValues = (viewFlagValues & ~FOCUSABLE_MASK) | f;
                    if ((viewFlagValues & FOCUSABLE_AUTO) == 0) viewFlagMasks |= FOCUSABLE_MASK;
                    break;
                }
                case android.R.attr.focusableInTouchMode:
                    if (a.getBoolean(index, false)) {
                        viewFlagValues &= ~FOCUSABLE_AUTO;
                        viewFlagValues |= FOCUSABLE_IN_TOUCH_MODE | FOCUSABLE;
                        viewFlagMasks |= FOCUSABLE_IN_TOUCH_MODE | FOCUSABLE_MASK;
                    }
                    break;
                case android.R.attr.clickable:
                    if (a.getBoolean(index, false)) {
                        viewFlagValues |= CLICKABLE;
                        viewFlagMasks |= CLICKABLE;
                    }
                    break;
                case android.R.attr.longClickable:
                    if (a.getBoolean(index, false)) {
                        viewFlagValues |= LONG_CLICKABLE;
                        viewFlagMasks |= LONG_CLICKABLE;
                    }
                    break;
                case android.R.attr.contextClickable:
                    if (a.getBoolean(index, false)) {
                        viewFlagValues |= CONTEXT_CLICKABLE;
                        viewFlagMasks |= CONTEXT_CLICKABLE;
                    }
                    break;
                case android.R.attr.saveEnabled:
                    if (!a.getBoolean(index, true)) {
                        viewFlagValues |= SAVE_DISABLED;
                        viewFlagMasks |= SAVE_DISABLED_MASK;
                    }
                    break;
                case android.R.attr.duplicateParentState:
                    if (a.getBoolean(index, false)) {
                        viewFlagValues |= DUPLICATE_PARENT_STATE;
                        viewFlagMasks |= DUPLICATE_PARENT_STATE;
                    }
                    break;
                case android.R.attr.visibility: {
                    final int visibility = a.getInt(index, 0);
                    if (visibility != 0) {
                        viewFlagValues |= VISIBILITY_FLAGS[visibility];
                        viewFlagMasks |= VISIBILITY_MASK;
                    }
                    break;
                }
                case android.R.attr.layoutDirection: {
                    int layoutDirection = a.getInt(index, -1);
                    if (layoutDirection >= 0) mLayoutDirection = layoutDirection;
                    break;
                }
                case android.R.attr.drawingCacheQuality:
                    break;
                case android.R.attr.contentDescription:
                    mContentDescription = a.getText(index);
                    break;
                case android.R.attr.tooltipText:
                    mTooltipText = a.getText(index);
                    break;
                case android.R.attr.accessibilityPaneTitle:
                    mAccessibilityPaneTitle = a.getText(index);
                    break;
                case android.R.attr.soundEffectsEnabled:
                    if (!a.getBoolean(index, true)) {
                        viewFlagValues &= ~SOUND_EFFECTS_ENABLED;
                        viewFlagMasks |= SOUND_EFFECTS_ENABLED;
                    }
                    break;
                case android.R.attr.hapticFeedbackEnabled:
                    if (!a.getBoolean(index, true)) {
                        viewFlagValues &= ~HAPTIC_FEEDBACK_ENABLED;
                        viewFlagMasks |= HAPTIC_FEEDBACK_ENABLED;
                    }
                    break;
                case android.R.attr.scrollbars: {
                    final int scrollbars = a.getInt(index, 0);
                    int flags = 0;
                    if ((scrollbars & 0x100) != 0) flags |= SCROLLBARS_HORIZONTAL;
                    if ((scrollbars & 0x200) != 0) flags |= SCROLLBARS_VERTICAL;
                    if (flags != 0) {
                        viewFlagValues |= flags;
                        viewFlagMasks |= SCROLLBARS_MASK;
                        initializeScrollbars = true;
                    }
                    break;
                }
                case android.R.attr.fadingEdge:
                case android.R.attr.requiresFadingEdge: {
                    final int fadingEdge = a.getInt(index, 0);
                    int flags = 0;
                    if ((fadingEdge & 0x1000) != 0) flags |= FADING_EDGE_HORIZONTAL;
                    if ((fadingEdge & 0x2000) != 0) flags |= FADING_EDGE_VERTICAL;
                    if (flags != 0) {
                        viewFlagValues |= flags;
                        viewFlagMasks |= FADING_EDGE_MASK;
                    }
                    break;
                }
                case android.R.attr.scrollbarStyle:
                    scrollbarStyle = a.getInt(index, SCROLLBARS_INSIDE_OVERLAY);
                    if (scrollbarStyle != SCROLLBARS_INSIDE_OVERLAY) {
                        viewFlagValues |= scrollbarStyle & SCROLLBARS_STYLE_MASK;
                        viewFlagMasks |= SCROLLBARS_STYLE_MASK;
                    }
                    break;
                case android.R.attr.isScrollContainer:
                    setScrollContainer = true;
                    if (a.getBoolean(index, false)) setScrollContainer(true);
                    break;
                case android.R.attr.keepScreenOn:
                    if (a.getBoolean(index, false)) {
                        viewFlagValues |= KEEP_SCREEN_ON;
                        viewFlagMasks |= KEEP_SCREEN_ON;
                    }
                    break;
                case android.R.attr.filterTouchesWhenObscured:
                    if (a.getBoolean(index, false)) {
                        viewFlagValues |= FILTER_TOUCHES_WHEN_OBSCURED;
                        viewFlagMasks |= FILTER_TOUCHES_WHEN_OBSCURED;
                    }
                    break;
                case android.R.attr.nextFocusLeft:
                    mNextFocusLeftId = a.getResourceId(index, NO_ID);
                    break;
                case android.R.attr.nextFocusRight:
                    mNextFocusRightId = a.getResourceId(index, NO_ID);
                    break;
                case android.R.attr.nextFocusUp:
                    mNextFocusUpId = a.getResourceId(index, NO_ID);
                    break;
                case android.R.attr.nextFocusDown:
                    mNextFocusDownId = a.getResourceId(index, NO_ID);
                    break;
                case android.R.attr.nextFocusForward:
                    mNextFocusForwardId = a.getResourceId(index, NO_ID);
                    break;
                case android.R.attr.nextClusterForward:
                    mNextClusterForwardId = a.getResourceId(index, NO_ID);
                    break;
                case android.R.attr.minWidth:
                    mMinWidth = a.getDimensionPixelSize(index, 0);
                    break;
                case android.R.attr.minHeight:
                    mMinHeight = a.getDimensionPixelSize(index, 0);
                    break;
                case android.R.attr.onClick: {
                    final String handlerName = a.getString(index);
                    if (handlerName != null) setOnClickListener(new DeclaredOnClickListener(this, handlerName));
                    break;
                }
                case android.R.attr.overScrollMode:
                    overScrollMode = a.getInt(index, OVER_SCROLL_IF_CONTENT_SCROLLS);
                    break;
                case android.R.attr.verticalScrollbarPosition:
                    mVerticalScrollbarPosition = a.getInt(index, SCROLLBAR_POSITION_DEFAULT);
                    break;
                case android.R.attr.layerType:
                    mLayerType = a.getInt(index, LAYER_TYPE_NONE);
                    break;
                case android.R.attr.textDirection:
                    mTextDirection = a.getInt(index, TEXT_DIRECTION_INHERIT);
                    break;
                case android.R.attr.textAlignment:
                    mTextAlignment = a.getInt(index, TEXT_ALIGNMENT_INHERIT);
                    break;
                case android.R.attr.importantForAccessibility:
                    mImportantForAccessibility = a.getInt(index, IMPORTANT_FOR_ACCESSIBILITY_AUTO);
                    break;
                case android.R.attr.accessibilityLiveRegion:
                    mAccessibilityLiveRegion = a.getInt(index, 0);
                    break;
                case android.R.attr.accessibilityTraversalBefore:
                    mAccessibilityTraversalBeforeId = a.getResourceId(index, NO_ID);
                    break;
                case android.R.attr.accessibilityTraversalAfter:
                    mAccessibilityTraversalAfterId = a.getResourceId(index, NO_ID);
                    break;
                case android.R.attr.labelFor:
                    mLabelForId = a.getResourceId(index, NO_ID);
                    break;
                case android.R.attr.foreground:
                    setForeground(a.getDrawable(index));
                    break;
                case android.R.attr.foregroundGravity:
                    mForegroundGravity = a.getInt(index, Gravity.FILL);
                    break;
                case android.R.attr.foregroundTint:
                    mForegroundTintList = a.getColorStateList(index);
                    break;
                case android.R.attr.foregroundTintMode:
                    mForegroundTintMode = Drawable.parseTintMode(a.getInt(index, -1), null);
                    break;
                case android.R.attr.backgroundTint:
                    mBackgroundTintList = a.getColorStateList(index);
                    break;
                case android.R.attr.backgroundTintMode:
                    mBackgroundTintMode = Drawable.parseTintMode(a.getInt(index, -1), null);
                    break;
                case android.R.attr.outlineProvider:
                    setOutlineProviderFromAttribute(a.getInt(index, 0));
                    break;
                case android.R.attr.focusedByDefault:
                    setFocusedByDefault(a.getBoolean(index, false));
                    break;
                case android.R.attr.defaultFocusHighlightEnabled:
                    mDefaultFocusHighlightEnabled = a.getBoolean(index, true);
                    break;
                case android.R.attr.enabled:
                    if (!a.getBoolean(index, true)) {
                        viewFlagValues |= DISABLED;
                        viewFlagMasks |= ENABLED_MASK;
                    }
                    break;
                case android.R.attr.forceHasOverlappingRendering:
                    forceHasOverlappingRendering(a.getBoolean(index, true));
                    break;
                case android.R.attr.scrollIndicators: {
                    final int scrollIndicators = (a.getInt(index, 0) << PFLAG3_SCROLL_INDICATOR_SHIFT)
                            & PFLAG3_SCROLL_INDICATOR_MASK;
                    if (scrollIndicators != 0) mPrivateFlags3 |= scrollIndicators;
                    break;
                }
                case android.R.attr.keyboardNavigationCluster:
                    setKeyboardNavigationCluster(a.getBoolean(index, false));
                    break;
                case android.R.attr.transitionName:
                    mTransitionName = a.getString(index);
                    break;
                case android.R.attr.fadeScrollbars:
                    fadeScrollbars = a.getBoolean(index, true);
                    break;
                case android.R.attr.scrollbarSize:
                    scrollbarSize = a.getDimensionPixelSize(index, -1);
                    break;
                case android.R.attr.scrollbarThumbVertical:
                    vThumb = a.getDrawable(index);
                    break;
                case android.R.attr.scrollbarThumbHorizontal:
                    hThumb = a.getDrawable(index);
                    break;
                case android.R.attr.scrollbarTrackVertical:
                    vTrack = a.getDrawable(index);
                    break;
                case android.R.attr.scrollbarTrackHorizontal:
                    hTrack = a.getDrawable(index);
                    break;
                case android.R.attr.scrollbarFadeDuration:
                    fadeDuration = a.getInt(index, -1);
                    break;
                case android.R.attr.scrollbarDefaultDelayBeforeFade:
                    fadeDelay = a.getInt(index, -1);
                    break;
                case android.R.attr.importantForAutofill:
                    mImportantForAutofill = a.getInt(index, IMPORTANT_FOR_AUTOFILL_AUTO);
                    break;
                case android.R.attr.autofillHints: {
                    CharSequence hints = a.getText(index);
                    if (hints != null) mAutofillHints = hints.toString().split(",");
                    break;
                }
                case android.R.attr.outlineSpotShadowColor:
                    mOutlineSpotShadowColor = a.getColor(index, 0xff000000);
                    break;
                case android.R.attr.outlineAmbientShadowColor:
                    mOutlineAmbientShadowColor = a.getColor(index, 0xff000000);
                    break;
                case android.R.attr.forceDarkAllowed:
                    mForceDarkAllowed = a.getBoolean(index, true);
                    break;
                default:
                    break;
            }
        }

        setOverScrollMode(overScrollMode);

        // AOSP precedence: padding > paddingHorizontal/Vertical > paddingLeft/Top/Right/Bottom
        if (padding >= 0) {
            leftPadding = topPadding = rightPadding = bottomPadding = padding;
            startPaddingDefined = false;
            endPaddingDefined = false;
        } else {
            if (paddingHorizontal >= 0) {
                leftPadding = paddingHorizontal;
                rightPadding = paddingHorizontal;
            }
            if (paddingVertical >= 0) {
                topPadding = paddingVertical;
                bottomPadding = paddingVertical;
            }
        }
        leftPaddingDefined = leftPadding >= 0;
        rightPaddingDefined = rightPadding >= 0;
        mUserPaddingStart = startPaddingDefined ? startPadding : UNDEFINED_PADDING;
        mUserPaddingEnd = endPaddingDefined ? endPadding : UNDEFINED_PADDING;

        if (background != null) setBackground(background);

        // setBackground may have changed the padding; explicit attributes win
        mLeftPaddingDefined = leftPaddingDefined;
        mRightPaddingDefined = rightPaddingDefined;
        mUserPaddingLeftInitial = leftPaddingDefined ? leftPadding : mPaddingLeft;
        mUserPaddingRightInitial = rightPaddingDefined ? rightPadding : mPaddingRight;
        internalSetPadding(mUserPaddingLeftInitial, topPadding >= 0 ? topPadding : mPaddingTop,
                mUserPaddingRightInitial, bottomPadding >= 0 ? bottomPadding : mPaddingBottom);
        resolvePaddingInternal();

        if (viewFlagMasks != 0) setFlags(viewFlagValues, viewFlagMasks);

        if (initializeScrollbars || scrollbarSize >= 0 || vThumb != null) {
            ScrollabilityCache sc = getScrollCache();
            if (scrollbarSize >= 0) sc.scrollBarSize = scrollbarSize;
            if (fadeDuration >= 0) sc.scrollBarFadeDuration = fadeDuration;
            if (fadeDelay >= 0) sc.scrollBarDefaultDelayBeforeFade = fadeDelay;
            if (fadeScrollbars != null) sc.fadeScrollBars = fadeScrollbars;
            if (vThumb != null) sc.verticalThumb = vThumb;
            if (hThumb != null) sc.horizontalThumb = hThumb;
            if (vTrack != null) sc.verticalTrack = vTrack;
            if (hTrack != null) sc.horizontalTrack = hTrack;
        }

        a.recycle();

        if (scrollbarStyle != SCROLLBARS_INSIDE_OVERLAY) recomputePadding();
        if (x != 0 || y != 0) scrollTo(x, y);
        if (transformSet) {
            setTranslationX(tx);
            setTranslationY(ty);
            setTranslationZ(tz);
            setElevation(elevation);
            setRotation(rotation);
            setRotationX(rotationX);
            setRotationY(rotationY);
            setScaleX(sx);
            setScaleY(sy);
        }
        if (!setScrollContainer && (viewFlagValues & SCROLLBARS_VERTICAL) != 0) setScrollContainer(true);
        if (mBackgroundTintList != null || mBackgroundTintMode != null) applyBackgroundTint();
        if (mForegroundTintList != null || mForegroundTintMode != null) applyForegroundTint();
        computeOpaqueFlags();
    }

    private static int getFocusableAttribute(TypedArray attributes, int index) {
        android.util.TypedValue val = new android.util.TypedValue();
        if (attributes.getValue(index, val)) {
            if (val.type == android.util.TypedValue.TYPE_INT_BOOLEAN) {
                return val.data == 0 ? NOT_FOCUSABLE : FOCUSABLE;
            }
            return val.data;
        }
        return FOCUSABLE_AUTO;
    }

    private void setOutlineProviderFromAttribute(int providerInt) {
        switch (providerInt) {
            case 0: setOutlineProvider(ViewOutlineProvider.BACKGROUND); break;
            case 1: setOutlineProvider(null); break;
            case 2: setOutlineProvider(ViewOutlineProvider.BOUNDS); break;
            case 3: setOutlineProvider(ViewOutlineProvider.PADDED_BOUNDS); break;
        }
    }

    /** android:onClick handler resolved on the context (AOSP DeclaredOnClickListener). */
    private static class DeclaredOnClickListener implements OnClickListener {
        private final View mHostView;
        private final String mMethodName;
        private Method mResolvedMethod;
        private Context mResolvedContext;

        DeclaredOnClickListener(View hostView, String methodName) {
            mHostView = hostView;
            mMethodName = methodName;
        }

        public void onClick(View v) {
            if (mResolvedMethod == null) resolveMethod(mHostView.getContext());
            try {
                mResolvedMethod.invoke(mResolvedContext, v);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Could not execute non-public method for android:onClick", e);
            } catch (InvocationTargetException e) {
                throw new IllegalStateException("Could not execute method for android:onClick", e);
            }
        }

        private void resolveMethod(Context context) {
            while (context != null) {
                try {
                    if (!context.isRestricted()) {
                        final Method method = context.getClass().getMethod(mMethodName, View.class);
                        if (method != null) {
                            mResolvedMethod = method;
                            mResolvedContext = context;
                            return;
                        }
                    }
                } catch (NoSuchMethodException e) {
                }
                context = context instanceof ContextWrapper ? ((ContextWrapper) context).getBaseContext() : null;
            }
            final int id = mHostView.getId();
            final String idText = id == NO_ID ? "" : " with id '"
                    + mHostView.getContext().getResources().getResourceEntryName(id) + "'";
            throw new IllegalStateException("Could not find method " + mMethodName
                    + "(View) in a parent or ancestor Context for android:onClick "
                    + "attribute defined on view " + mHostView.getClass() + idText);
        }
    }

    // ---------------------------------------------------------------- basic properties

    public final Context getContext() { return mContext; }

    public Resources getResources() { return mResources; }

    public void setId(int id) { mID = id; }

    public int getId() { return mID; }

    public static int generateViewId() {
        for (;;) {
            final int result = sNextGeneratedId.get();
            int newValue = result + 1;
            if (newValue > 0x00FFFFFF) newValue = 1;
            if (sNextGeneratedId.compareAndSet(result, newValue)) return result;
        }
    }

    public Object getTag() { return mTag; }

    public void setTag(Object tag) { mTag = tag; }

    public Object getTag(int key) {
        if (mKeyedTags != null) return mKeyedTags.get(key);
        return null;
    }

    public void setTag(int key, Object tag) {
        if ((key >>> 24) < 2) {
            throw new IllegalArgumentException("The key must be an application-specific resource id.");
        }
        setKeyedTag(key, tag);
    }

    /** framework-internal (hidden in AOSP). Allows framework ids as keys. */
    public void setTagInternal(int key, Object tag) {
        if ((key >>> 24) != 0x1) {
            throw new IllegalArgumentException("The key must be a framework-specific resource id.");
        }
        setKeyedTag(key, tag);
    }

    private void setKeyedTag(int key, Object tag) {
        if (mKeyedTags == null) mKeyedTags = new SparseArray<Object>(2);
        mKeyedTags.put(key, tag);
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder(128);
        out.append(getClass().getName());
        out.append('{');
        out.append(Integer.toHexString(System.identityHashCode(this)));
        out.append(' ');
        switch (mViewFlags & VISIBILITY_MASK) {
            case VISIBLE: out.append('V'); break;
            case INVISIBLE: out.append('I'); break;
            case GONE: out.append('G'); break;
            default: out.append('.'); break;
        }
        out.append((mViewFlags & FOCUSABLE) == FOCUSABLE ? 'F' : '.');
        out.append((mViewFlags & ENABLED_MASK) == ENABLED ? 'E' : '.');
        out.append((mViewFlags & DRAW_MASK) == WILL_NOT_DRAW ? '.' : 'D');
        out.append((mViewFlags & SCROLLBARS_HORIZONTAL) != 0 ? 'H' : '.');
        out.append((mViewFlags & SCROLLBARS_VERTICAL) != 0 ? 'V' : '.');
        out.append((mViewFlags & CLICKABLE) != 0 ? 'C' : '.');
        out.append((mViewFlags & LONG_CLICKABLE) != 0 ? 'L' : '.');
        out.append((mViewFlags & CONTEXT_CLICKABLE) != 0 ? 'X' : '.');
        out.append(' ');
        out.append((mPrivateFlags & PFLAG_IS_ROOT_NAMESPACE) != 0 ? 'R' : '.');
        out.append((mPrivateFlags & PFLAG_FOCUSED) != 0 ? 'F' : '.');
        out.append((mPrivateFlags & PFLAG_SELECTED) != 0 ? 'S' : '.');
        if ((mPrivateFlags & PFLAG_PREPRESSED) != 0) out.append('p');
        else out.append((mPrivateFlags & PFLAG_PRESSED) != 0 ? 'P' : '.');
        out.append((mPrivateFlags & PFLAG_HOVERED) != 0 ? 'H' : '.');
        out.append((mPrivateFlags & PFLAG_ACTIVATED) != 0 ? 'A' : '.');
        out.append((mPrivateFlags & PFLAG_INVALIDATED) != 0 ? 'I' : '.');
        out.append((mPrivateFlags & PFLAG_DIRTY) != 0 ? 'D' : '.');
        out.append(' ');
        out.append(mLeft).append(',').append(mTop).append('-').append(mRight).append(',').append(mBottom);
        final int id = getId();
        if (id != NO_ID) {
            out.append(" #").append(Integer.toHexString(id));
            final Resources r = mResources;
            if (id > 0 && Resources.resourceHasPackage(id) && r != null) {
                try {
                    out.append(' ').append(r.getResourcePackageName(id)).append(':')
                            .append(r.getResourceTypeName(id)).append('/').append(r.getResourceEntryName(id));
                } catch (Resources.NotFoundException e) {
                }
            }
        }
        out.append('}');
        return out.toString();
    }

    // ---------------------------------------------------------------- flags

    void setFlags(int flags, int mask) {
        int old = mViewFlags;
        mViewFlags = (mViewFlags & ~mask) | (flags & mask);
        int changed = mViewFlags ^ old;
        if (changed == 0) return;
        int privateFlags = mPrivateFlags;
        boolean shouldNotifyFocusableAvailable = false;

        // FOCUSABLE_AUTO: focusable follows clickable
        if ((mViewFlags & FOCUSABLE_AUTO) != 0 && (changed & (FOCUSABLE_MASK | CLICKABLE)) != 0) {
            final int newFocus = (mViewFlags & CLICKABLE) != 0 ? FOCUSABLE : NOT_FOCUSABLE;
            mViewFlags = (mViewFlags & ~FOCUSABLE) | newFocus;
            changed = (changed & ~FOCUSABLE) | ((old & FOCUSABLE) ^ (mViewFlags & FOCUSABLE));
        }

        if ((changed & FOCUSABLE) != 0 && (privateFlags & PFLAG_HAS_BOUNDS) != 0) {
            if ((old & FOCUSABLE) == FOCUSABLE && (privateFlags & PFLAG_FOCUSED) != 0) {
                clearFocus();
                if (mParent instanceof ViewGroup) ((ViewGroup) mParent).clearFocusedInCluster();
            } else if ((old & FOCUSABLE) == NOT_FOCUSABLE && (privateFlags & PFLAG_FOCUSED) == 0) {
                if (mParent != null) shouldNotifyFocusableAvailable = canTakeFocus();
            }
        }

        final int newVisibility = flags & VISIBILITY_MASK;
        if (newVisibility == VISIBLE && (changed & VISIBILITY_MASK) != 0) {
            mPrivateFlags |= PFLAG_DRAWN;
            invalidate(true);
            shouldNotifyFocusableAvailable = hasSize() && canTakeFocus();
        }

        if ((changed & ENABLED_MASK) != 0) {
            if ((mViewFlags & ENABLED_MASK) == ENABLED) {
                shouldNotifyFocusableAvailable = canTakeFocus();
            } else if (isFocused()) {
                clearFocus();
            }
        }

        if (shouldNotifyFocusableAvailable && mParent != null) mParent.focusableViewAvailable(this);

        if ((changed & GONE) != 0) {
            requestLayout();
            if ((mViewFlags & VISIBILITY_MASK) == GONE) {
                if (hasFocus()) {
                    clearFocus();
                    if (mParent instanceof ViewGroup) ((ViewGroup) mParent).clearFocusedInCluster();
                }
                if (mParent instanceof View) ((View) mParent).invalidate(true);
                mPrivateFlags |= PFLAG_DRAWN;
            }
            if (mAttachInfo != null) mAttachInfo.mViewVisibilityChanged = true;
        }

        if ((changed & INVISIBLE) != 0) {
            mPrivateFlags |= PFLAG_DRAWN;
            if ((mViewFlags & VISIBILITY_MASK) == INVISIBLE) {
                if (getRootView() != this && hasFocus()) clearFocus();
            }
            if (mParent instanceof View) ((View) mParent).invalidate(true);
            if (mAttachInfo != null) mAttachInfo.mViewVisibilityChanged = true;
        }

        if ((changed & VISIBILITY_MASK) != 0) {
            if (newVisibility != VISIBLE && mAttachInfo != null) cleanupDraw();
            if (mParent instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) mParent;
                parent.onChildVisibilityChanged(this, (changed & VISIBILITY_MASK), newVisibility);
                parent.invalidate(true);
            } else if (mParent != null) {
                mParent.invalidateChild(this, null);
            }
            if (mAttachInfo != null) {
                dispatchVisibilityChanged(this, newVisibility);
                if (mParent != null && getWindowVisibility() == VISIBLE
                        && (!(mParent instanceof ViewGroup) || ((ViewGroup) mParent).isShown())) {
                    dispatchVisibilityAggregated(newVisibility == VISIBLE);
                }
            }
        }

        if ((changed & WILL_NOT_CACHE_DRAWING) != 0) destroyDrawingCache();
        if ((changed & DRAWING_CACHE_ENABLED) != 0) destroyDrawingCache();

        if ((changed & DRAW_MASK) != 0) {
            if ((mViewFlags & WILL_NOT_DRAW) != 0) {
                if (mBackground != null || mForeground != null) mPrivateFlags &= ~PFLAG_SKIP_DRAW;
                else mPrivateFlags |= PFLAG_SKIP_DRAW;
            } else {
                mPrivateFlags &= ~PFLAG_SKIP_DRAW;
            }
            requestLayout();
            invalidate(true);
        }

        if ((changed & KEEP_SCREEN_ON) != 0 && mParent != null && mAttachInfo != null) {
            mParent.recomputeViewAttributes(this);
        }

        if ((changed & (CLICKABLE | LONG_CLICKABLE | CONTEXT_CLICKABLE | ENABLED_MASK)) != 0) {
            refreshDrawableState();
        }
    }

    private void cleanupDraw() {
        removeLongPressCallback();
        removeTapCallback();
        removeUnsetPressCallback();
        if ((mPrivateFlags & PFLAG_PRESSED) != 0) setPressed(false);
    }

    private boolean hasSize() { return (mBottom > mTop) && (mRight > mLeft); }

    private boolean canTakeFocus() {
        return ((mViewFlags & VISIBILITY_MASK) == VISIBLE) && ((mViewFlags & FOCUSABLE) == FOCUSABLE)
                && ((mViewFlags & ENABLED_MASK) == ENABLED);
    }

    public int getVisibility() { return mViewFlags & VISIBILITY_MASK; }

    public void setVisibility(int visibility) { setFlags(visibility, VISIBILITY_MASK); }

    /** framework-internal (hidden in AOSP; public since API 29 as setTransitionVisibility). */
    public void setTransitionVisibility(int visibility) {
        mViewFlags = (mViewFlags & ~VISIBILITY_MASK) | visibility;
    }

    public boolean isEnabled() { return (mViewFlags & ENABLED_MASK) == ENABLED; }

    public void setEnabled(boolean enabled) {
        if (enabled == isEnabled()) return;
        setFlags(enabled ? ENABLED : DISABLED, ENABLED_MASK);
        refreshDrawableState();
        invalidate(true);
        if (!enabled) cancelPendingInputEvents();
    }

    public void setFocusable(boolean focusable) { setFocusable(focusable ? FOCUSABLE : NOT_FOCUSABLE); }

    public void setFocusable(int focusable) {
        if ((focusable & (FOCUSABLE_AUTO | FOCUSABLE)) == 0) setFlags(0, FOCUSABLE_IN_TOUCH_MODE);
        setFlags(focusable, FOCUSABLE_MASK);
    }

    public void setFocusableInTouchMode(boolean focusableInTouchMode) {
        setFlags(focusableInTouchMode ? FOCUSABLE_IN_TOUCH_MODE : 0, FOCUSABLE_IN_TOUCH_MODE);
        if (focusableInTouchMode) setFlags(FOCUSABLE, FOCUSABLE_MASK);
    }

    public final boolean isFocusable() { return FOCUSABLE == (mViewFlags & FOCUSABLE); }

    public int getFocusable() { return (mViewFlags & FOCUSABLE_AUTO) > 0 ? FOCUSABLE_AUTO : mViewFlags & FOCUSABLE; }

    public final boolean isFocusableInTouchMode() { return FOCUSABLE_IN_TOUCH_MODE == (mViewFlags & FOCUSABLE_IN_TOUCH_MODE); }

    public void setClickable(boolean clickable) { setFlags(clickable ? CLICKABLE : 0, CLICKABLE); }

    public boolean isClickable() { return (mViewFlags & CLICKABLE) == CLICKABLE; }

    public void setLongClickable(boolean longClickable) { setFlags(longClickable ? LONG_CLICKABLE : 0, LONG_CLICKABLE); }

    public boolean isLongClickable() { return (mViewFlags & LONG_CLICKABLE) == LONG_CLICKABLE; }

    public void setContextClickable(boolean contextClickable) {
        setFlags(contextClickable ? CONTEXT_CLICKABLE : 0, CONTEXT_CLICKABLE);
    }

    public boolean isContextClickable() { return (mViewFlags & CONTEXT_CLICKABLE) == CONTEXT_CLICKABLE; }

    public void setAllowClickWhenDisabled(boolean clickableWhenDisabled) { mAllowClickWhenDisabled = clickableWhenDisabled; }

    public void setSoundEffectsEnabled(boolean soundEffectsEnabled) {
        setFlags(soundEffectsEnabled ? SOUND_EFFECTS_ENABLED : 0, SOUND_EFFECTS_ENABLED);
    }

    public boolean isSoundEffectsEnabled() { return SOUND_EFFECTS_ENABLED == (mViewFlags & SOUND_EFFECTS_ENABLED); }

    public void setHapticFeedbackEnabled(boolean hapticFeedbackEnabled) {
        setFlags(hapticFeedbackEnabled ? HAPTIC_FEEDBACK_ENABLED : 0, HAPTIC_FEEDBACK_ENABLED);
    }

    public boolean isHapticFeedbackEnabled() { return HAPTIC_FEEDBACK_ENABLED == (mViewFlags & HAPTIC_FEEDBACK_ENABLED); }

    public void setKeepScreenOn(boolean keepScreenOn) { setFlags(keepScreenOn ? KEEP_SCREEN_ON : 0, KEEP_SCREEN_ON); }

    public boolean getKeepScreenOn() { return (mViewFlags & KEEP_SCREEN_ON) != 0; }

    public void setDuplicateParentStateEnabled(boolean enabled) {
        setFlags(enabled ? DUPLICATE_PARENT_STATE : 0, DUPLICATE_PARENT_STATE);
    }

    public boolean isDuplicateParentStateEnabled() { return (mViewFlags & DUPLICATE_PARENT_STATE) == DUPLICATE_PARENT_STATE; }

    public void setSaveEnabled(boolean enabled) { setFlags(enabled ? 0 : SAVE_DISABLED, SAVE_DISABLED_MASK); }

    public boolean isSaveEnabled() { return (mViewFlags & SAVE_DISABLED_MASK) != SAVE_DISABLED; }

    public void setSaveFromParentEnabled(boolean enabled) { setFlags(enabled ? 0 : PARENT_SAVE_DISABLED, PARENT_SAVE_DISABLED); }

    public boolean isSaveFromParentEnabled() { return (mViewFlags & PARENT_SAVE_DISABLED) != PARENT_SAVE_DISABLED; }

    public void setFilterTouchesWhenObscured(boolean enabled) {
        setFlags(enabled ? FILTER_TOUCHES_WHEN_OBSCURED : 0, FILTER_TOUCHES_WHEN_OBSCURED);
    }

    public boolean getFilterTouchesWhenObscured() { return (mViewFlags & FILTER_TOUCHES_WHEN_OBSCURED) != 0; }

    public void setWillNotDraw(boolean willNotDraw) { setFlags(willNotDraw ? WILL_NOT_DRAW : 0, DRAW_MASK); }

    public boolean willNotDraw() { return (mViewFlags & DRAW_MASK) == WILL_NOT_DRAW; }

    public void setWillNotCacheDrawing(boolean willNotCacheDrawing) {
        setFlags(willNotCacheDrawing ? WILL_NOT_CACHE_DRAWING : 0, WILL_NOT_CACHE_DRAWING);
    }

    public boolean willNotCacheDrawing() { return (mViewFlags & WILL_NOT_CACHE_DRAWING) == WILL_NOT_CACHE_DRAWING; }

    public void setFitsSystemWindows(boolean fitSystemWindows) {
        setFlags(fitSystemWindows ? FITS_SYSTEM_WINDOWS : 0, FITS_SYSTEM_WINDOWS);
    }

    public boolean getFitsSystemWindows() { return (mViewFlags & FITS_SYSTEM_WINDOWS) == FITS_SYSTEM_WINDOWS; }

    /** framework-internal (hidden in AOSP). */
    public boolean fitsSystemWindows() { return getFitsSystemWindows(); }

    public void setScrollContainer(boolean isScrollContainer) {
        if (isScrollContainer) {
            if (mAttachInfo != null && (mPrivateFlags & PFLAG_SCROLL_CONTAINER_ADDED) == 0) {
                mAttachInfo.mScrollContainers.add(this);
                mPrivateFlags |= PFLAG_SCROLL_CONTAINER_ADDED;
            }
            mPrivateFlags |= PFLAG_SCROLL_CONTAINER;
        } else {
            if ((mPrivateFlags & PFLAG_SCROLL_CONTAINER_ADDED) != 0 && mAttachInfo != null) {
                mAttachInfo.mScrollContainers.remove(this);
            }
            mPrivateFlags &= ~(PFLAG_SCROLL_CONTAINER | PFLAG_SCROLL_CONTAINER_ADDED);
        }
    }

    public boolean isScrollContainer() { return (mPrivateFlags & PFLAG_SCROLL_CONTAINER_ADDED) != 0; }

    public void setDrawingCacheQuality(int quality) {}

    public int getDrawingCacheQuality() { return DRAWING_CACHE_QUALITY_AUTO; }

    public void setMinimumWidth(int minWidth) {
        mMinWidth = minWidth;
        requestLayout();
    }

    public void setMinimumHeight(int minHeight) {
        mMinHeight = minHeight;
        requestLayout();
    }

    public int getMinimumWidth() { return mMinWidth; }

    public int getMinimumHeight() { return mMinHeight; }

    // ---------------------------------------------------------------- padding

    public void setPadding(int left, int top, int right, int bottom) {
        mUserPaddingStart = UNDEFINED_PADDING;
        mUserPaddingEnd = UNDEFINED_PADDING;
        mUserPaddingLeftInitial = left;
        mUserPaddingRightInitial = right;
        mLeftPaddingDefined = true;
        mRightPaddingDefined = true;
        internalSetPadding(left, top, right, bottom);
    }

    public void setPaddingRelative(int start, int top, int end, int bottom) {
        mUserPaddingStart = start;
        mUserPaddingEnd = end;
        mLeftPaddingDefined = true;
        mRightPaddingDefined = true;
        if (isLayoutRtl()) {
            mUserPaddingLeftInitial = end;
            mUserPaddingRightInitial = start;
            internalSetPadding(end, top, start, bottom);
        } else {
            mUserPaddingLeftInitial = start;
            mUserPaddingRightInitial = end;
            internalSetPadding(start, top, end, bottom);
        }
    }

    /** framework-internal (hidden in AOSP). */
    protected void internalSetPadding(int left, int top, int right, int bottom) {
        mUserPaddingLeft = left;
        mUserPaddingRight = right;
        mUserPaddingBottom = bottom;
        final int viewFlags = mViewFlags;
        boolean changed = false;
        if ((viewFlags & (SCROLLBARS_VERTICAL | SCROLLBARS_HORIZONTAL)) != 0) {
            if ((viewFlags & SCROLLBARS_VERTICAL) != 0) {
                final int offset = (viewFlags & SCROLLBARS_INSET_MASK) == 0 ? 0 : getVerticalScrollbarWidth();
                if (mVerticalScrollbarPosition == SCROLLBAR_POSITION_LEFT) left += offset;
                else right += offset;
            }
            if ((viewFlags & SCROLLBARS_HORIZONTAL) != 0) {
                bottom += (viewFlags & SCROLLBARS_INSET_MASK) == 0 ? 0 : getHorizontalScrollbarHeight();
            }
        }
        if (mPaddingLeft != left) {
            changed = true;
            mPaddingLeft = left;
        }
        if (mPaddingTop != top) {
            changed = true;
            mPaddingTop = top;
        }
        if (mPaddingRight != right) {
            changed = true;
            mPaddingRight = right;
        }
        if (mPaddingBottom != bottom) {
            changed = true;
            mPaddingBottom = bottom;
        }
        if (changed) {
            requestLayout();
            invalidateOutline();
        }
    }

    private void recomputePadding() { internalSetPadding(mUserPaddingLeft, mPaddingTop, mUserPaddingRight, mUserPaddingBottom); }

    /** Resolves start/end padding against the layout direction. */
    void resolvePaddingInternal() {
        int left = mUserPaddingLeftInitial;
        int right = mUserPaddingRightInitial;
        if (isLayoutRtl()) {
            if (mUserPaddingStart != UNDEFINED_PADDING) right = mUserPaddingStart;
            if (mUserPaddingEnd != UNDEFINED_PADDING) left = mUserPaddingEnd;
        } else {
            if (mUserPaddingStart != UNDEFINED_PADDING) left = mUserPaddingStart;
            if (mUserPaddingEnd != UNDEFINED_PADDING) right = mUserPaddingEnd;
        }
        if (mUserPaddingStart != UNDEFINED_PADDING || mUserPaddingEnd != UNDEFINED_PADDING) {
            internalSetPadding(left, mPaddingTop, right, mUserPaddingBottom);
        }
    }

    public int getPaddingTop() { return mPaddingTop; }

    public int getPaddingBottom() { return mPaddingBottom; }

    public int getPaddingLeft() { return mPaddingLeft; }

    public int getPaddingRight() { return mPaddingRight; }

    public int getPaddingStart() { return isLayoutRtl() ? mPaddingRight : mPaddingLeft; }

    public int getPaddingEnd() { return isLayoutRtl() ? mPaddingLeft : mPaddingRight; }

    public boolean isPaddingRelative() {
        return mUserPaddingStart != UNDEFINED_PADDING || mUserPaddingEnd != UNDEFINED_PADDING;
    }

    protected boolean isPaddingOffsetRequired() { return false; }

    protected int getLeftPaddingOffset() { return 0; }

    protected int getRightPaddingOffset() { return 0; }

    protected int getTopPaddingOffset() { return 0; }

    protected int getBottomPaddingOffset() { return 0; }

    /** framework-internal (hidden in AOSP). */
    public Insets getOpticalInsets() { return Insets.NONE; }

    // ---------------------------------------------------------------- layout direction

    public void setLayoutDirection(int layoutDirection) {
        if (mLayoutDirection != layoutDirection) {
            mLayoutDirection = layoutDirection;
            resolvePaddingInternal();
            requestLayout();
            invalidate(true);
            onRtlPropertiesChanged(getLayoutDirection());
        }
    }

    /** framework-internal (hidden in AOSP). The raw (unresolved) layout direction. */
    public int getRawLayoutDirection() { return mLayoutDirection; }

    public int getLayoutDirection() {
        switch (mLayoutDirection) {
            case LAYOUT_DIRECTION_RTL:
                return LAYOUT_DIRECTION_RTL;
            case LAYOUT_DIRECTION_INHERIT:
                return mParent != null && mParent.canResolveLayoutDirection() ? mParent.getLayoutDirection()
                        : LAYOUT_DIRECTION_LTR;
            default:
                return LAYOUT_DIRECTION_LTR;
        }
    }

    /** framework-internal (hidden in AOSP). */
    public boolean isLayoutRtl() { return getLayoutDirection() == LAYOUT_DIRECTION_RTL; }

    public boolean canResolveLayoutDirection() { return true; }

    public boolean isLayoutDirectionResolved() { return true; }

    public void onRtlPropertiesChanged(int layoutDirection) {}

    public void setTextDirection(int textDirection) {
        if (mTextDirection != textDirection) {
            mTextDirection = textDirection;
            requestLayout();
            invalidate(true);
        }
    }

    public int getTextDirection() {
        if (mTextDirection == TEXT_DIRECTION_INHERIT) {
            return mParent != null && mParent.canResolveTextDirection() ? mParent.getTextDirection()
                    : TEXT_DIRECTION_FIRST_STRONG;
        }
        return mTextDirection;
    }

    /** framework-internal (hidden in AOSP). */
    public int getRawTextDirection() { return mTextDirection; }

    public boolean canResolveTextDirection() { return true; }

    public boolean isTextDirectionResolved() { return true; }

    public void setTextAlignment(int textAlignment) {
        if (mTextAlignment != textAlignment) {
            mTextAlignment = textAlignment;
            requestLayout();
            invalidate(true);
        }
    }

    public int getTextAlignment() {
        if (mTextAlignment == TEXT_ALIGNMENT_INHERIT) {
            return mParent != null && mParent.canResolveTextAlignment() ? mParent.getTextAlignment()
                    : TEXT_ALIGNMENT_GRAVITY;
        }
        return mTextAlignment;
    }

    /** framework-internal (hidden in AOSP). */
    public int getRawTextAlignment() { return mTextAlignment; }

    public boolean canResolveTextAlignment() { return true; }

    public boolean isTextAlignmentResolved() { return true; }

    // ---------------------------------------------------------------- layout params and geometry

    public ViewGroup.LayoutParams getLayoutParams() { return mLayoutParams; }

    public void setLayoutParams(ViewGroup.LayoutParams params) {
        if (params == null) throw new NullPointerException("Layout parameters cannot be null");
        mLayoutParams = params;
        if (mParent instanceof ViewGroup) ((ViewGroup) mParent).onSetLayoutParams(this, params);
        requestLayout();
    }

    public final int getLeft() { return mLeft; }
    public final int getTop() { return mTop; }
    public final int getRight() { return mRight; }
    public final int getBottom() { return mBottom; }
    public final int getWidth() { return mRight - mLeft; }
    public final int getHeight() { return mBottom - mTop; }

    public final void setLeft(int left) {
        if (left != mLeft) {
            invalidate(true);
            int oldWidth = mRight - mLeft;
            int height = mBottom - mTop;
            mLeft = left;
            sizeChange(mRight - mLeft, height, oldWidth, height);
            invalidate(true);
        }
    }

    public final void setTop(int top) {
        if (top != mTop) {
            invalidate(true);
            int width = mRight - mLeft;
            int oldHeight = mBottom - mTop;
            mTop = top;
            sizeChange(width, mBottom - mTop, width, oldHeight);
            invalidate(true);
        }
    }

    public final void setRight(int right) {
        if (right != mRight) {
            invalidate(true);
            int oldWidth = mRight - mLeft;
            int height = mBottom - mTop;
            mRight = right;
            sizeChange(mRight - mLeft, height, oldWidth, height);
            invalidate(true);
        }
    }

    public final void setBottom(int bottom) {
        if (bottom != mBottom) {
            invalidate(true);
            int width = mRight - mLeft;
            int oldHeight = mBottom - mTop;
            mBottom = bottom;
            sizeChange(width, mBottom - mTop, width, oldHeight);
            invalidate(true);
        }
    }

    public final void setLeftTopRightBottom(int left, int top, int right, int bottom) { setFrame(left, top, right, bottom); }

    public void offsetTopAndBottom(int offset) {
        if (offset != 0) {
            invalidate(true);
            mTop += offset;
            mBottom += offset;
            invalidate(true);
            invalidateParentIfNeeded();
        }
    }

    public void offsetLeftAndRight(int offset) {
        if (offset != 0) {
            invalidate(true);
            mLeft += offset;
            mRight += offset;
            invalidate(true);
            invalidateParentIfNeeded();
        }
    }

    private void invalidateParentIfNeeded() {
        if (mParent instanceof View) ((View) mParent).invalidate(true);
    }

    public final int getScrollX() { return mScrollX; }
    public final int getScrollY() { return mScrollY; }

    public void setScrollX(int value) { scrollTo(value, mScrollY); }

    public void setScrollY(int value) { scrollTo(mScrollX, value); }

    public void getDrawingRect(Rect outRect) {
        outRect.left = mScrollX;
        outRect.top = mScrollY;
        outRect.right = mScrollX + (mRight - mLeft);
        outRect.bottom = mScrollY + (mBottom - mTop);
    }

    public final int getMeasuredWidth() { return mMeasuredWidth & MEASURED_SIZE_MASK; }
    public final int getMeasuredWidthAndState() { return mMeasuredWidth; }
    public final int getMeasuredHeight() { return mMeasuredHeight & MEASURED_SIZE_MASK; }
    public final int getMeasuredHeightAndState() { return mMeasuredHeight; }

    public final int getMeasuredState() {
        return (mMeasuredWidth & MEASURED_STATE_MASK)
                | ((mMeasuredHeight >> MEASURED_HEIGHT_STATE_SHIFT) & (MEASURED_STATE_MASK >> MEASURED_HEIGHT_STATE_SHIFT));
    }

    // ---------------------------------------------------------------- measure

    public final void measure(int widthMeasureSpec, int heightMeasureSpec) {
        long key = (long) widthMeasureSpec << 32 | (long) heightMeasureSpec & 0xffffffffL;
        if (mMeasureCache == null) mMeasureCache = new HashMap<Long, Long>();
        final boolean forceLayout = (mPrivateFlags & PFLAG_FORCE_LAYOUT) == PFLAG_FORCE_LAYOUT;
        final boolean specChanged = widthMeasureSpec != mOldWidthMeasureSpec
                || heightMeasureSpec != mOldHeightMeasureSpec;
        final boolean isSpecExactly = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
                && MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY;
        final boolean matchesSpecSize = getMeasuredWidth() == MeasureSpec.getSize(widthMeasureSpec)
                && getMeasuredHeight() == MeasureSpec.getSize(heightMeasureSpec);
        final boolean needsLayout = specChanged && (!isSpecExactly || !matchesSpecSize);
        if (forceLayout || needsLayout) {
            mPrivateFlags &= ~PFLAG_MEASURED_DIMENSION_SET;
            Long cached = forceLayout ? null : mMeasureCache.get(key);
            if (cached == null) {
                onMeasure(widthMeasureSpec, heightMeasureSpec);
                mPrivateFlags3 &= ~PFLAG3_MEASURE_NEEDED_BEFORE_LAYOUT;
            } else {
                long value = cached;
                setMeasuredDimensionRaw((int) (value >> 32), (int) value);
                mPrivateFlags3 |= PFLAG3_MEASURE_NEEDED_BEFORE_LAYOUT;
            }
            if ((mPrivateFlags & PFLAG_MEASURED_DIMENSION_SET) != PFLAG_MEASURED_DIMENSION_SET) {
                throw new IllegalStateException("View with id " + getId() + ": " + getClass().getName()
                        + "#onMeasure() did not set the measured dimension by calling setMeasuredDimension()");
            }
            mPrivateFlags |= PFLAG_LAYOUT_REQUIRED;
        }
        mOldWidthMeasureSpec = widthMeasureSpec;
        mOldHeightMeasureSpec = heightMeasureSpec;
        mMeasureCache.put(key, ((long) mMeasuredWidth) << 32 | (long) mMeasuredHeight & 0xffffffffL);
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
    }

    protected final void setMeasuredDimension(int measuredWidth, int measuredHeight) {
        setMeasuredDimensionRaw(measuredWidth, measuredHeight);
    }

    private void setMeasuredDimensionRaw(int measuredWidth, int measuredHeight) {
        mMeasuredWidth = measuredWidth;
        mMeasuredHeight = measuredHeight;
        mPrivateFlags |= PFLAG_MEASURED_DIMENSION_SET;
    }

    public static int combineMeasuredStates(int curState, int newState) { return curState | newState; }

    public static int resolveSize(int size, int measureSpec) { return resolveSizeAndState(size, measureSpec, 0) & MEASURED_SIZE_MASK; }

    public static int resolveSizeAndState(int size, int measureSpec, int childMeasuredState) {
        final int specMode = MeasureSpec.getMode(measureSpec);
        final int specSize = MeasureSpec.getSize(measureSpec);
        final int result;
        switch (specMode) {
            case MeasureSpec.AT_MOST:
                if (specSize < size) result = specSize | MEASURED_STATE_TOO_SMALL;
                else result = size;
                break;
            case MeasureSpec.EXACTLY:
                result = specSize;
                break;
            case MeasureSpec.UNSPECIFIED:
            default:
                result = size;
        }
        return result | (childMeasuredState & MEASURED_STATE_MASK);
    }

    public static int getDefaultSize(int size, int measureSpec) {
        int result = size;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
                result = size;
                break;
            case MeasureSpec.AT_MOST:
            case MeasureSpec.EXACTLY:
                result = specSize;
                break;
        }
        return result;
    }

    protected int getSuggestedMinimumHeight() {
        return (mBackground == null) ? mMinHeight : Math.max(mMinHeight, mBackground.getMinimumHeight());
    }

    protected int getSuggestedMinimumWidth() {
        return (mBackground == null) ? mMinWidth : Math.max(mMinWidth, mBackground.getMinimumWidth());
    }

    public void forceLayout() {
        if (mMeasureCache != null) mMeasureCache.clear();
        mPrivateFlags |= PFLAG_FORCE_LAYOUT;
        mPrivateFlags |= PFLAG_INVALIDATED;
    }

    public void requestLayout() {
        if (mMeasureCache != null) mMeasureCache.clear();
        if (mAttachInfo != null && mAttachInfo.mViewRootImpl.isInLayout()) {
            if (!mAttachInfo.mViewRootImpl.requestLayoutDuringLayout(this)) return;
        }
        mPrivateFlags |= PFLAG_FORCE_LAYOUT;
        mPrivateFlags |= PFLAG_INVALIDATED;
        if (mParent != null && !mParent.isLayoutRequested()) mParent.requestLayout();
    }

    public boolean isLayoutRequested() { return (mPrivateFlags & PFLAG_FORCE_LAYOUT) == PFLAG_FORCE_LAYOUT; }

    public boolean isInLayout() {
        return mAttachInfo != null && mAttachInfo.mViewRootImpl.isInLayout();
    }

    public boolean isLaidOut() { return (mPrivateFlags3 & PFLAG3_IS_LAID_OUT) == PFLAG3_IS_LAID_OUT; }

    // ---------------------------------------------------------------- layout

    public void layout(int l, int t, int r, int b) {
        if ((mPrivateFlags3 & PFLAG3_MEASURE_NEEDED_BEFORE_LAYOUT) != 0) {
            onMeasure(mOldWidthMeasureSpec, mOldHeightMeasureSpec);
            mPrivateFlags3 &= ~PFLAG3_MEASURE_NEEDED_BEFORE_LAYOUT;
        }
        int oldL = mLeft;
        int oldT = mTop;
        int oldB = mBottom;
        int oldR = mRight;
        boolean changed = setFrame(l, t, r, b);
        if (changed || (mPrivateFlags & PFLAG_LAYOUT_REQUIRED) == PFLAG_LAYOUT_REQUIRED) {
            onLayout(changed, l, t, r, b);
            mPrivateFlags &= ~PFLAG_LAYOUT_REQUIRED;
            ListenerInfo li = mListenerInfo;
            if (li != null && li.mOnLayoutChangeListeners != null) {
                ArrayList<OnLayoutChangeListener> listenersCopy =
                        new ArrayList<OnLayoutChangeListener>(li.mOnLayoutChangeListeners);
                for (int i = 0; i < listenersCopy.size(); ++i) {
                    listenersCopy.get(i).onLayoutChange(this, l, t, r, b, oldL, oldT, oldR, oldB);
                }
            }
        }
        mPrivateFlags &= ~PFLAG_FORCE_LAYOUT;
        mPrivateFlags3 |= PFLAG3_IS_LAID_OUT;
    }

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {}

    /** framework-internal (hidden in AOSP, protected). */
    protected boolean setFrame(int left, int top, int right, int bottom) {
        boolean changed = false;
        if (mLeft != left || mRight != right || mTop != top || mBottom != bottom) {
            changed = true;
            int drawn = mPrivateFlags & PFLAG_DRAWN;
            int oldWidth = mRight - mLeft;
            int oldHeight = mBottom - mTop;
            int newWidth = right - left;
            int newHeight = bottom - top;
            boolean sizeChanged = (newWidth != oldWidth) || (newHeight != oldHeight);
            invalidate(sizeChanged);
            mLeft = left;
            mTop = top;
            mRight = right;
            mBottom = bottom;
            mPrivateFlags |= PFLAG_HAS_BOUNDS;
            if (sizeChanged) sizeChange(newWidth, newHeight, oldWidth, oldHeight);
            if ((mViewFlags & VISIBILITY_MASK) == VISIBLE) {
                mPrivateFlags |= PFLAG_DRAWN;
                invalidate(sizeChanged);
            }
            mPrivateFlags |= drawn;
            mBackgroundSizeChanged = true;
            mForegroundBoundsChanged = true;
        }
        return changed;
    }

    private void sizeChange(int newWidth, int newHeight, int oldWidth, int oldHeight) {
        if (mTransformationInfo != null && (mPrivateFlags & PFLAG_PIVOT_EXPLICITLY_SET) == 0) {
            mTransformationInfo.mMatrixDirty = true;
        }
        onSizeChanged(newWidth, newHeight, oldWidth, oldHeight);
        mBackgroundSizeChanged = true;
        mForegroundBoundsChanged = true;
        invalidateOutline();
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {}

    protected void onFinishInflate() {}


    public int getBaseline() { return -1; }

    // ---------------------------------------------------------------- drawing

    /**
     * framework-internal (AOSP package-private). Draws this child of parent: applies position,
     * scroll, transform, alpha and clipping, then draw() or dispatchDraw() when SKIP_DRAW.
     */
    boolean draw(Canvas canvas, ViewGroup parent, long drawingTime) {
        if ((mViewFlags & VISIBILITY_MASK) != VISIBLE && !isAnimatingTransitionHack()) return false;
        final boolean identity = hasIdentityMatrix();
        final boolean clip = parent != null && parent.getClipChildren();
        if (identity && clip && mClipBounds == null
                && canvas.quickReject(mLeft, mTop, mRight, mBottom)) {
            mPrivateFlags &= ~PFLAG_DIRTY;
            return false;
        }
        computeScroll();
        final int sx = mScrollX;
        final int sy = mScrollY;
        float alpha = getAlpha() * getTransitionAlpha();
        if (alpha <= 0f) {
            mPrivateFlags &= ~(PFLAG_DIRTY | PFLAG_INVALIDATED);
            return false;
        }
        final int restoreTo = canvas.save();
        if (identity) {
            canvas.translate(mLeft - sx, mTop - sy);
        } else {
            canvas.translate(mLeft, mTop);
            canvas.concat(getMatrix());
            canvas.translate(-sx, -sy);
        }
        final int w = mRight - mLeft;
        final int h = mBottom - mTop;
        if (clip) canvas.clipRect(sx, sy, sx + w, sy + h);
        if (mClipBounds != null) canvas.clipRect(mClipBounds);
        if (alpha < 1f) {
            int multipliedAlpha = (int) (255 * alpha);
            if (!onSetAlpha(multipliedAlpha)) {
                canvas.saveLayerAlpha(sx, sy, sx + w, sy + h, multipliedAlpha);
            }
        } else if ((mPrivateFlags & PFLAG_ALPHA_SET) == PFLAG_ALPHA_SET) {
            onSetAlpha(255);
            mPrivateFlags &= ~PFLAG_ALPHA_SET;
        }
        if (mLayerType != LAYER_TYPE_NONE && mLayerPaint != null) {
            canvas.saveLayer(sx, sy, sx + w, sy + h, mLayerPaint);
        }
        mPrivateFlags |= PFLAG_DRAWN;
        if ((mPrivateFlags & PFLAG_SKIP_DRAW) == PFLAG_SKIP_DRAW) {
            mPrivateFlags &= ~(PFLAG_DIRTY | PFLAG_INVALIDATED);
            dispatchDraw(canvas);
            drawAutofilledHighlight(canvas);
            if (mOverlay != null && !mOverlay.isEmpty()) mOverlay.getOverlayView().draw(canvas);
        } else {
            draw(canvas);
        }
        canvas.restoreToCount(restoreTo);
        return false;
    }

    private boolean isAnimatingTransitionHack() { return false; }

    private void drawAutofilledHighlight(Canvas canvas) {}

    public void draw(Canvas canvas) {
        mPrivateFlags = (mPrivateFlags & ~PFLAG_DIRTY) | PFLAG_DRAWN;
        mPrivateFlags &= ~PFLAG_INVALIDATED;
        drawBackground(canvas);
        onDraw(canvas);
        dispatchDraw(canvas);
        if (mOverlay != null && !mOverlay.isEmpty()) mOverlay.getOverlayView().dispatchDraw(canvas);
        onDrawForeground(canvas);
        drawDefaultFocusHighlight(canvas);
    }

    private void drawBackground(Canvas canvas) {
        final Drawable background = mBackground;
        if (background == null) return;
        if (mBackgroundSizeChanged) {
            background.setBounds(0, 0, mRight - mLeft, mBottom - mTop);
            mBackgroundSizeChanged = false;
        }
        final int scrollX = mScrollX;
        final int scrollY = mScrollY;
        if ((scrollX | scrollY) == 0) {
            background.draw(canvas);
        } else {
            canvas.translate(scrollX, scrollY);
            background.draw(canvas);
            canvas.translate(-scrollX, -scrollY);
        }
    }

    protected void onDraw(Canvas canvas) {}

    protected void dispatchDraw(Canvas canvas) {}

    public void onDrawForeground(Canvas canvas) {
        onDrawScrollIndicators(canvas);
        onDrawScrollBars(canvas);
        final Drawable foreground = mForeground;
        if (foreground != null) {
            if (mForegroundBoundsChanged) {
                mForegroundBoundsChanged = false;
                final Rect selfBounds = new Rect();
                final Rect overlayBounds = new Rect();
                final int w = mRight - mLeft;
                final int h = mBottom - mTop;
                if (mForegroundInPadding) selfBounds.set(0, 0, w, h);
                else selfBounds.set(getPaddingLeft(), getPaddingTop(), w - getPaddingRight(), h - getPaddingBottom());
                Gravity.apply(mForegroundGravity, foreground.getIntrinsicWidth(), foreground.getIntrinsicHeight(),
                        selfBounds, overlayBounds, getLayoutDirection());
                foreground.setBounds(overlayBounds);
            }
            if ((mScrollX | mScrollY) == 0) {
                foreground.draw(canvas);
            } else {
                canvas.translate(mScrollX, mScrollY);
                foreground.draw(canvas);
                canvas.translate(-mScrollX, -mScrollY);
            }
        }
    }

    private void onDrawScrollIndicators(Canvas c) {}

    // default focus highlight, drawn on focused views without a focused state in their drawables
    private Drawable mDefaultFocusHighlight;
    private boolean mDefaultFocusHighlightSizeChanged = true;

    private boolean isDefaultFocusHighlightNeeded() {
        if (!mDefaultFocusHighlightEnabled || mAttachInfo == null || isInTouchMode() || !isFocused()) return false;
        boolean lackFocusState = (mBackground == null || !mBackground.isStateful()
                || !mBackground.hasFocusStateSpecified())
                && (mForeground == null || !mForeground.isStateful() || !mForeground.hasFocusStateSpecified());
        return lackFocusState;
    }

    private void drawDefaultFocusHighlight(Canvas canvas) {
        if (!isDefaultFocusHighlightNeeded()) return;
        if (mDefaultFocusHighlight == null) {
            TypedArray ta = mContext.obtainStyledAttributes(new int[] {android.R.attr.selectableItemBackground});
            Drawable d = ta.getDrawable(0);
            ta.recycle();
            if (d == null) d = new ColorDrawable(0x33ffffff);
            mDefaultFocusHighlight = d;
            d.setCallback(this);
            mDefaultFocusHighlightSizeChanged = true;
        }
        Drawable d = mDefaultFocusHighlight;
        if (d.isStateful()) d.setState(getDrawableState());
        int l = mScrollX;
        int t = mScrollY;
        d.setBounds(l, t, l + mRight - mLeft, t + mBottom - mTop);
        d.draw(canvas);
    }

    public void setDefaultFocusHighlightEnabled(boolean defaultFocusHighlightEnabled) {
        mDefaultFocusHighlightEnabled = defaultFocusHighlightEnabled;
    }

    public final boolean getDefaultFocusHighlightEnabled() { return mDefaultFocusHighlightEnabled; }

    public long getDrawingTime() { return mAttachInfo != null ? mAttachInfo.mDrawingTime : 0; }

    public boolean isDirty() { return (mPrivateFlags & PFLAG_DIRTY) != 0; }

    public boolean isOpaque() {
        return (mPrivateFlags & PFLAG_OPAQUE_BACKGROUND) == PFLAG_OPAQUE_BACKGROUND && getFinalAlpha() >= 1.0f;
    }

    private float getFinalAlpha() {
        return mTransformationInfo != null ? mTransformationInfo.mAlpha * mTransformationInfo.mTransitionAlpha : 1;
    }

    /** framework-internal (hidden in AOSP, protected). */
    protected void computeOpaqueFlags() {
        if (mBackground != null && mBackground.getOpacity() == android.graphics.PixelFormat.OPAQUE) {
            mPrivateFlags |= PFLAG_OPAQUE_BACKGROUND;
        } else {
            mPrivateFlags &= ~PFLAG_OPAQUE_BACKGROUND;
        }
    }

    public boolean hasOverlappingRendering() { return true; }

    public void forceHasOverlappingRendering(boolean hasOverlappingRendering) {
        mPrivateFlags3 |= PFLAG3_HAS_OVERLAPPING_RENDERING_FORCED;
        if (hasOverlappingRendering) mPrivateFlags3 |= PFLAG3_OVERLAPPING_RENDERING_FORCED_VALUE;
        else mPrivateFlags3 &= ~PFLAG3_OVERLAPPING_RENDERING_FORCED_VALUE;
    }

    public final boolean getHasOverlappingRendering() {
        return (mPrivateFlags3 & PFLAG3_HAS_OVERLAPPING_RENDERING_FORCED) != 0
                ? (mPrivateFlags3 & PFLAG3_OVERLAPPING_RENDERING_FORCED_VALUE) != 0 : hasOverlappingRendering();
    }

    protected boolean onSetAlpha(int alpha) { return false; }

    public boolean gatherTransparentRegion(Region region) { return true; }

    public int getSolidColor() { return 0; }

    // ---------------------------------------------------------------- invalidation

    public void invalidate() { invalidate(true); }

    /** framework-internal (hidden in AOSP). */
    public void invalidate(boolean invalidateCache) {
        invalidateInternal(0, 0, mRight - mLeft, mBottom - mTop, invalidateCache, true);
    }

    @Deprecated
    public void invalidate(Rect dirty) {
        final int scrollX = mScrollX;
        final int scrollY = mScrollY;
        invalidateInternal(dirty.left - scrollX, dirty.top - scrollY, dirty.right - scrollX, dirty.bottom - scrollY,
                true, false);
    }

    @Deprecated
    public void invalidate(int l, int t, int r, int b) {
        final int scrollX = mScrollX;
        final int scrollY = mScrollY;
        invalidateInternal(l - scrollX, t - scrollY, r - scrollX, b - scrollY, true, false);
    }

    private boolean skipInvalidate() {
        return (mViewFlags & VISIBILITY_MASK) != VISIBLE
                && (!(mParent instanceof ViewGroup) || !((ViewGroup) mParent).isViewTransitioning(this));
    }

    void invalidateInternal(int l, int t, int r, int b, boolean invalidateCache, boolean fullInvalidate) {
        if (skipInvalidate()) return;
        mPrivateFlags |= PFLAG_DIRTY;
        if (invalidateCache) {
            mPrivateFlags |= PFLAG_INVALIDATED;
            mPrivateFlags &= ~PFLAG_DRAWING_CACHE_VALID;
        }
        final AttachInfo ai = mAttachInfo;
        final ViewParent p = mParent;
        if (p != null && ai != null && l < r && t < b) {
            final Rect damage = new Rect(l, t, r, b);
            p.invalidateChild(this, damage);
        }
    }

    /** framework-internal (hidden in AOSP). Invalidates the area this view covers in its parent. */
    void invalidateViewProperty() {
        if (mParent instanceof View) {
            invalidateInternal(0, 0, mRight - mLeft, mBottom - mTop, false, true);
            ((View) mParent).mPrivateFlags |= PFLAG_DIRTY;
        }
    }

    public void postInvalidate() { postInvalidateDelayed(0); }

    public void postInvalidate(int left, int top, int right, int bottom) {
        postInvalidateDelayed(0, left, top, right, bottom);
    }

    public void postInvalidateDelayed(long delayMilliseconds) {
        Handler h = getHandler();
        if (h != null) {
            h.postDelayed(new Runnable() {
                public void run() { invalidate(); }
            }, delayMilliseconds);
        }
    }

    public void postInvalidateDelayed(long delayMilliseconds, final int left, final int top, final int right,
            final int bottom) {
        Handler h = getHandler();
        if (h != null) {
            h.postDelayed(new Runnable() {
                public void run() { invalidate(left, top, right, bottom); }
            }, delayMilliseconds);
        }
    }

    public void postInvalidateOnAnimation() {
        if (mAttachInfo != null) mAttachInfo.mViewRootImpl.invalidateOnAnimation(this);
    }

    public void postInvalidateOnAnimation(int left, int top, int right, int bottom) { postInvalidateOnAnimation(); }

    public void invalidateDrawable(Drawable drawable) {
        if (verifyDrawable(drawable)) {
            final Rect dirty = drawable.getDirtyBounds();
            final int scrollX = mScrollX;
            final int scrollY = mScrollY;
            invalidate(dirty.left + scrollX, dirty.top + scrollY, dirty.right + scrollX, dirty.bottom + scrollY);
        }
    }

    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        if (verifyDrawable(who) && what != null) {
            final long delay = when - SystemClock.uptimeMillis();
            if (mAttachInfo != null) mAttachInfo.mHandler.postAtTime(what, who, when);
            else getRunQueue().postDelayed(what, delay);
        }
    }

    public void unscheduleDrawable(Drawable who, Runnable what) {
        if (verifyDrawable(who) && what != null) {
            if (mAttachInfo != null) mAttachInfo.mHandler.removeCallbacks(what, who);
            getRunQueue().removeCallbacks(what);
        }
    }

    public void unscheduleDrawable(Drawable who) {
        if (mAttachInfo != null && who != null) mAttachInfo.mHandler.removeCallbacksAndMessages(who);
    }

    protected boolean verifyDrawable(Drawable who) {
        return who == mBackground || who == mForeground || who == mDefaultFocusHighlight
                || (mScrollCache != null && (who == mScrollCache.verticalThumb || who == mScrollCache.horizontalThumb));
    }

    // ---------------------------------------------------------------- background / foreground

    private int mBackgroundResource;

    public void setBackgroundColor(int color) {
        if (mBackground instanceof ColorDrawable) {
            ((ColorDrawable) mBackground.mutate()).setColor(color);
            computeOpaqueFlags();
            mBackgroundResource = 0;
            invalidate(true);
        } else {
            setBackground(new ColorDrawable(color));
        }
    }

    public void setBackgroundResource(int resid) {
        if (resid != 0 && resid == mBackgroundResource) return;
        Drawable d = null;
        if (resid != 0) d = mContext.getDrawable(resid);
        setBackground(d);
        mBackgroundResource = resid;
    }

    public void setBackground(Drawable background) { setBackgroundDrawable(background); }

    @Deprecated
    public void setBackgroundDrawable(Drawable background) {
        computeOpaqueFlags();
        if (background == mBackground) return;
        boolean requestLayout = false;
        mBackgroundResource = 0;
        if (mBackground != null) {
            if (isAttachedToWindow()) mBackground.setVisible(false, false);
            mBackground.setCallback(null);
            unscheduleDrawable(mBackground);
        }
        if (background != null) {
            Rect padding = new Rect();
            background.setLayoutDirection(getLayoutDirection());
            if (background.getPadding(padding)) {
                if (background.getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
                    mUserPaddingLeftInitial = padding.right;
                    mUserPaddingRightInitial = padding.left;
                    internalSetPadding(padding.right, padding.top, padding.left, padding.bottom);
                } else {
                    mUserPaddingLeftInitial = padding.left;
                    mUserPaddingRightInitial = padding.right;
                    internalSetPadding(padding.left, padding.top, padding.right, padding.bottom);
                }
                mLeftPaddingDefined = false;
                mRightPaddingDefined = false;
            }
            if (mBackground == null || mBackground.getMinimumHeight() != background.getMinimumHeight()
                    || mBackground.getMinimumWidth() != background.getMinimumWidth()) {
                requestLayout = true;
            }
            mBackground = background;
            if (background.isStateful()) background.setState(getDrawableState());
            if (isAttachedToWindow()) background.setVisible(getWindowVisibility() == VISIBLE && isShown(), false);
            applyBackgroundTint();
            background.setCallback(this);
            if ((mPrivateFlags & PFLAG_SKIP_DRAW) != 0) {
                mPrivateFlags &= ~PFLAG_SKIP_DRAW;
                requestLayout = true;
            }
        } else {
            mBackground = null;
            if ((mViewFlags & WILL_NOT_DRAW) != 0 && mForeground == null) mPrivateFlags |= PFLAG_SKIP_DRAW;
            requestLayout = true;
        }
        computeOpaqueFlags();
        if (requestLayout) requestLayout();
        mBackgroundSizeChanged = true;
        invalidate(true);
        invalidateOutline();
    }

    public Drawable getBackground() { return mBackground; }

    public void setBackgroundTintList(ColorStateList tint) {
        mBackgroundTintList = tint;
        applyBackgroundTint();
    }

    public ColorStateList getBackgroundTintList() { return mBackgroundTintList; }

    public void setBackgroundTintMode(PorterDuff.Mode tintMode) {
        mBackgroundTintMode = tintMode;
        applyBackgroundTint();
    }

    public void setBackgroundTintBlendMode(BlendMode blendMode) {
        setBackgroundTintMode(blendMode != null ? blendToPorterDuff(blendMode) : null);
    }

    public PorterDuff.Mode getBackgroundTintMode() { return mBackgroundTintMode; }

    public BlendMode getBackgroundTintBlendMode() {
        return mBackgroundTintMode != null ? porterDuffToBlend(mBackgroundTintMode) : null;
    }

    private static PorterDuff.Mode blendToPorterDuff(BlendMode mode) {
        try {
            return PorterDuff.Mode.valueOf(mode.name());
        } catch (IllegalArgumentException e) {
            return PorterDuff.Mode.SRC_IN;
        }
    }

    private static BlendMode porterDuffToBlend(PorterDuff.Mode mode) {
        try {
            return BlendMode.valueOf(mode.name());
        } catch (IllegalArgumentException e) {
            return BlendMode.SRC_IN;
        }
    }

    private void applyBackgroundTint() {
        if (mBackground != null && (mBackgroundTintList != null || mBackgroundTintMode != null)) {
            mBackground = mBackground.mutate();
            if (mBackgroundTintList != null) mBackground.setTintList(mBackgroundTintList);
            if (mBackgroundTintMode != null) mBackground.setTintMode(mBackgroundTintMode);
            if (mBackground.isStateful()) mBackground.setState(getDrawableState());
        }
    }

    public Drawable getForeground() { return mForeground; }

    public void setForeground(Drawable foreground) {
        if (mForeground == foreground) return;
        if (mForeground != null) {
            if (isAttachedToWindow()) mForeground.setVisible(false, false);
            mForeground.setCallback(null);
            unscheduleDrawable(mForeground);
        }
        mForeground = foreground;
        if (foreground != null) {
            if ((mPrivateFlags & PFLAG_SKIP_DRAW) != 0) mPrivateFlags &= ~PFLAG_SKIP_DRAW;
            foreground.setLayoutDirection(getLayoutDirection());
            if (foreground.isStateful()) foreground.setState(getDrawableState());
            applyForegroundTint();
            if (isAttachedToWindow()) foreground.setVisible(getWindowVisibility() == VISIBLE && isShown(), false);
            foreground.setCallback(this);
        } else if ((mViewFlags & WILL_NOT_DRAW) != 0 && mBackground == null) {
            mPrivateFlags |= PFLAG_SKIP_DRAW;
        }
        mForegroundBoundsChanged = true;
        requestLayout();
        invalidate();
    }

    /** framework-internal (hidden in AOSP). */
    public boolean isForegroundInsidePadding() { return mForegroundInPadding; }

    public int getForegroundGravity() { return mForegroundGravity; }

    public void setForegroundGravity(int gravity) {
        if (mForegroundGravity != gravity) {
            if ((gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0) gravity |= Gravity.START;
            if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == 0) gravity |= Gravity.TOP;
            mForegroundGravity = gravity;
            mForegroundBoundsChanged = true;
            requestLayout();
        }
    }

    public void setForegroundTintList(ColorStateList tint) {
        mForegroundTintList = tint;
        applyForegroundTint();
    }

    public ColorStateList getForegroundTintList() { return mForegroundTintList; }

    public void setForegroundTintMode(PorterDuff.Mode tintMode) {
        mForegroundTintMode = tintMode;
        applyForegroundTint();
    }

    public void setForegroundTintBlendMode(BlendMode blendMode) {
        setForegroundTintMode(blendMode != null ? blendToPorterDuff(blendMode) : null);
    }

    public PorterDuff.Mode getForegroundTintMode() { return mForegroundTintMode; }

    public BlendMode getForegroundTintBlendMode() {
        return mForegroundTintMode != null ? porterDuffToBlend(mForegroundTintMode) : null;
    }

    private void applyForegroundTint() {
        if (mForeground != null && (mForegroundTintList != null || mForegroundTintMode != null)) {
            mForeground = mForeground.mutate();
            if (mForegroundTintList != null) mForeground.setTintList(mForegroundTintList);
            if (mForegroundTintMode != null) mForeground.setTintMode(mForegroundTintMode);
            if (mForeground.isStateful()) mForeground.setState(getDrawableState());
        }
    }

    // ---------------------------------------------------------------- drawable state

    protected void drawableStateChanged() {
        final int[] state = getDrawableState();
        boolean changed = false;
        final Drawable bg = mBackground;
        if (bg != null && bg.isStateful()) changed |= bg.setState(state);
        final Drawable fg = mForeground;
        if (fg != null && fg.isStateful()) changed |= fg.setState(state);
        final Drawable hl = mDefaultFocusHighlight;
        if (hl != null && hl.isStateful()) changed |= hl.setState(state);
        if (mScrollCache != null) {
            if (mScrollCache.verticalThumb != null && mScrollCache.verticalThumb.isStateful()) {
                changed |= mScrollCache.verticalThumb.setState(state);
            }
        }
        if (changed) invalidate();
    }

    public void drawableHotspotChanged(float x, float y) {
        if (mBackground != null) mBackground.setHotspot(x, y);
        if (mDefaultFocusHighlight != null) mDefaultFocusHighlight.setHotspot(x, y);
        if (mForeground != null) mForeground.setHotspot(x, y);
        dispatchDrawableHotspotChanged(x, y);
    }

    public void dispatchDrawableHotspotChanged(float x, float y) {}

    public void refreshDrawableState() {
        mPrivateFlags |= PFLAG_DRAWABLE_STATE_DIRTY;
        drawableStateChanged();
        ViewParent parent = mParent;
        if (parent != null) parent.childDrawableStateChanged(this);
    }

    public final int[] getDrawableState() {
        if ((mDrawableState != null) && ((mPrivateFlags & PFLAG_DRAWABLE_STATE_DIRTY) == 0)) return mDrawableState;
        mDrawableState = onCreateDrawableState(0);
        mPrivateFlags &= ~PFLAG_DRAWABLE_STATE_DIRTY;
        return mDrawableState;
    }

    protected int[] onCreateDrawableState(int extraSpace) {
        if ((mViewFlags & DUPLICATE_PARENT_STATE) == DUPLICATE_PARENT_STATE && mParent instanceof View) {
            return ((View) mParent).onCreateDrawableState(extraSpace);
        }
        final int privateFlags = mPrivateFlags;
        int[] tmp = new int[8];
        int n = 0;
        if ((privateFlags & PFLAG_PRESSED) != 0) tmp[n++] = android.R.attr.state_pressed;
        if ((mViewFlags & ENABLED_MASK) == ENABLED) tmp[n++] = android.R.attr.state_enabled;
        if (isFocused()) tmp[n++] = android.R.attr.state_focused;
        if ((privateFlags & PFLAG_SELECTED) != 0) tmp[n++] = android.R.attr.state_selected;
        if (hasWindowFocus()) tmp[n++] = android.R.attr.state_window_focused;
        if ((privateFlags & PFLAG_ACTIVATED) != 0) tmp[n++] = android.R.attr.state_activated;
        if ((privateFlags & PFLAG_HOVERED) != 0) tmp[n++] = android.R.attr.state_hovered;
        int[] drawableState = new int[n + extraSpace];
        System.arraycopy(tmp, 0, drawableState, 0, n);
        return drawableState;
    }

    protected static int[] mergeDrawableStates(int[] baseState, int[] additionalState) {
        final int N = baseState.length;
        int i = N - 1;
        while (i >= 0 && baseState[i] == 0) i--;
        System.arraycopy(additionalState, 0, baseState, i + 1, additionalState.length);
        return baseState;
    }

    public void jumpDrawablesToCurrentState() {
        if (mBackground != null) mBackground.jumpToCurrentState();
        if (mDefaultFocusHighlight != null) mDefaultFocusHighlight.jumpToCurrentState();
        if (mForeground != null) mForeground.jumpToCurrentState();
    }

    public void setPressed(boolean pressed) {
        final boolean needsRefresh = pressed != ((mPrivateFlags & PFLAG_PRESSED) == PFLAG_PRESSED);
        if (pressed) mPrivateFlags |= PFLAG_PRESSED;
        else mPrivateFlags &= ~PFLAG_PRESSED;
        if (needsRefresh) refreshDrawableState();
        dispatchSetPressed(pressed);
    }

    private void setPressed(boolean pressed, float x, float y) {
        if (pressed) drawableHotspotChanged(x, y);
        setPressed(pressed);
    }

    protected void dispatchSetPressed(boolean pressed) {}

    public boolean isPressed() { return (mPrivateFlags & PFLAG_PRESSED) == PFLAG_PRESSED; }

    public void setSelected(boolean selected) {
        if (((mPrivateFlags & PFLAG_SELECTED) != 0) != selected) {
            mPrivateFlags = (mPrivateFlags & ~PFLAG_SELECTED) | (selected ? PFLAG_SELECTED : 0);
            if (!selected) resetPressedState();
            invalidate(true);
            refreshDrawableState();
            dispatchSetSelected(selected);
        }
    }

    protected void dispatchSetSelected(boolean selected) {}

    public boolean isSelected() { return (mPrivateFlags & PFLAG_SELECTED) != 0; }

    public void setActivated(boolean activated) {
        if (((mPrivateFlags & PFLAG_ACTIVATED) != 0) != activated) {
            mPrivateFlags = (mPrivateFlags & ~PFLAG_ACTIVATED) | (activated ? PFLAG_ACTIVATED : 0);
            invalidate(true);
            refreshDrawableState();
            dispatchSetActivated(activated);
        }
    }

    protected void dispatchSetActivated(boolean activated) {}

    public boolean isActivated() { return (mPrivateFlags & PFLAG_ACTIVATED) != 0; }

    public void setHovered(boolean hovered) {
        if (hovered) {
            if ((mPrivateFlags & PFLAG_HOVERED) == 0) {
                mPrivateFlags |= PFLAG_HOVERED;
                refreshDrawableState();
                onHoverChanged(true);
            }
        } else if ((mPrivateFlags & PFLAG_HOVERED) != 0) {
            mPrivateFlags &= ~PFLAG_HOVERED;
            refreshDrawableState();
            onHoverChanged(false);
        }
    }

    public boolean isHovered() { return (mPrivateFlags & PFLAG_HOVERED) != 0; }

    public void onHoverChanged(boolean hovered) {}

    private void resetPressedState() {
        if ((mViewFlags & ENABLED_MASK) == DISABLED) return;
        if (isPressed()) {
            setPressed(false);
            if (!mHasPerformedLongPress) removeLongPressCallback();
        }
    }

    // ---------------------------------------------------------------- transforms

    public Matrix getMatrix() {
        ensureTransformationInfo();
        final Matrix matrix = mTransformationInfo.mMatrix;
        updateMatrix();
        return matrix;
    }

    /** framework-internal (hidden in AOSP). */
    public final boolean hasIdentityMatrix() {
        if (mTransformationInfo == null) return true;
        updateMatrix();
        return mTransformationInfo.mMatrix.isIdentity();
    }

    void ensureTransformationInfo() {
        TransformationInfo ti = transformInfo();
        if (ti.mMatrix == null) {
            ti.mMatrix = new Matrix();
            ti.mMatrixDirty = true;
        }
    }

    private void updateMatrix() {
        final TransformationInfo ti = mTransformationInfo;
        if (ti == null) return;
        if (ti.mMatrix == null) {
            ti.mMatrix = new Matrix();
            ti.mMatrixDirty = true;
        }
        if (!ti.mMatrixDirty) return;
        final float pivotX = (mPrivateFlags & PFLAG_PIVOT_EXPLICITLY_SET) != 0 ? ti.mPivotX : (mRight - mLeft) / 2f;
        final float pivotY = (mPrivateFlags & PFLAG_PIVOT_EXPLICITLY_SET) != 0 ? ti.mPivotY : (mBottom - mTop) / 2f;
        final Matrix m = ti.mMatrix;
        if (ti.mRotationX == 0 && ti.mRotationY == 0) {
            m.setTranslate(ti.mTranslationX, ti.mTranslationY);
            m.preRotate(ti.mRotation, pivotX, pivotY);
            m.preScale(ti.mScaleX, ti.mScaleY, pivotX, pivotY);
        } else {
            if (ti.mCamera == null) ti.mCamera = new Camera();
            Camera camera = ti.mCamera;
            Matrix tmp = new Matrix();
            camera.save();
            if (ti.mCameraDistance != 0) camera.setLocation(0, 0, ti.mCameraDistance);
            camera.rotateX(ti.mRotationX);
            camera.rotateY(ti.mRotationY);
            camera.rotateZ(-ti.mRotation);
            camera.getMatrix(tmp);
            camera.restore();
            tmp.preTranslate(-pivotX, -pivotY);
            tmp.postTranslate(pivotX + ti.mTranslationX, pivotY + ti.mTranslationY);
            m.set(tmp);
            m.preScale(ti.mScaleX, ti.mScaleY, pivotX, pivotY);
        }
        if (ti.mAnimationMatrix != null) m.preConcat(ti.mAnimationMatrix);
        ti.mMatrixDirty = false;
        ti.mInverseMatrixDirty = true;
    }

    /** framework-internal (hidden in AOSP). */
    public final Matrix getInverseMatrix() {
        ensureTransformationInfo();
        final TransformationInfo ti = mTransformationInfo;
        updateMatrix();
        if (ti.mInverseMatrix == null) ti.mInverseMatrix = new Matrix();
        if (ti.mInverseMatrixDirty) {
            ti.mMatrix.invert(ti.mInverseMatrix);
            ti.mInverseMatrixDirty = false;
        }
        return ti.mInverseMatrix;
    }

    private void transformChanged() {
        if (mTransformationInfo != null) mTransformationInfo.mMatrixDirty = true;
    }

    private void beforeTransformChange() { invalidateViewProperty(); }

    private void afterTransformChange() {
        transformChanged();
        invalidateViewProperty();
    }

    public float getCameraDistance() {
        final float dpi = mResources != null ? mResources.getDisplayMetrics().densityDpi : 160;
        float z = mTransformationInfo != null && mTransformationInfo.mCameraDistance != 0
                ? mTransformationInfo.mCameraDistance : -8f;
        return -(z * dpi);
    }

    public void setCameraDistance(float distance) {
        final float dpi = mResources != null ? mResources.getDisplayMetrics().densityDpi : 160;
        beforeTransformChange();
        transformInfo().mCameraDistance = -Math.abs(distance) / dpi;
        afterTransformChange();
    }

    public float getRotation() { return mTransformationInfo != null ? mTransformationInfo.mRotation : 0; }

    public void setRotation(float rotation) {
        if (rotation != getRotation()) {
            beforeTransformChange();
            transformInfo().mRotation = rotation;
            afterTransformChange();
        }
    }

    public float getRotationY() { return mTransformationInfo != null ? mTransformationInfo.mRotationY : 0; }

    public void setRotationY(float rotationY) {
        if (rotationY != getRotationY()) {
            beforeTransformChange();
            transformInfo().mRotationY = rotationY;
            afterTransformChange();
        }
    }

    public float getRotationX() { return mTransformationInfo != null ? mTransformationInfo.mRotationX : 0; }

    public void setRotationX(float rotationX) {
        if (rotationX != getRotationX()) {
            beforeTransformChange();
            transformInfo().mRotationX = rotationX;
            afterTransformChange();
        }
    }

    public float getScaleX() { return mTransformationInfo != null ? mTransformationInfo.mScaleX : 1; }

    public void setScaleX(float scaleX) {
        if (scaleX != getScaleX()) {
            beforeTransformChange();
            transformInfo().mScaleX = scaleX;
            afterTransformChange();
        }
    }

    public float getScaleY() { return mTransformationInfo != null ? mTransformationInfo.mScaleY : 1; }

    public void setScaleY(float scaleY) {
        if (scaleY != getScaleY()) {
            beforeTransformChange();
            transformInfo().mScaleY = scaleY;
            afterTransformChange();
        }
    }

    public float getPivotX() {
        if ((mPrivateFlags & PFLAG_PIVOT_EXPLICITLY_SET) != 0 && mTransformationInfo != null) {
            return mTransformationInfo.mPivotX;
        }
        return (mRight - mLeft) / 2f;
    }

    public void setPivotX(float pivotX) {
        beforeTransformChange();
        transformInfo().mPivotX = pivotX;
        mPrivateFlags |= PFLAG_PIVOT_EXPLICITLY_SET;
        afterTransformChange();
    }

    public float getPivotY() {
        if ((mPrivateFlags & PFLAG_PIVOT_EXPLICITLY_SET) != 0 && mTransformationInfo != null) {
            return mTransformationInfo.mPivotY;
        }
        return (mBottom - mTop) / 2f;
    }

    public void setPivotY(float pivotY) {
        beforeTransformChange();
        transformInfo().mPivotY = pivotY;
        mPrivateFlags |= PFLAG_PIVOT_EXPLICITLY_SET;
        afterTransformChange();
    }

    public boolean isPivotSet() { return (mPrivateFlags & PFLAG_PIVOT_EXPLICITLY_SET) != 0; }

    public void resetPivot() {
        if ((mPrivateFlags & PFLAG_PIVOT_EXPLICITLY_SET) != 0) {
            beforeTransformChange();
            mPrivateFlags &= ~PFLAG_PIVOT_EXPLICITLY_SET;
            afterTransformChange();
        }
    }

    public float getAlpha() { return mTransformationInfo != null ? mTransformationInfo.mAlpha : 1; }

    public void setAlpha(float alpha) {
        ensureTransformationInfo();
        if (mTransformationInfo.mAlpha != alpha) {
            mTransformationInfo.mAlpha = alpha;
            if (onSetAlpha((int) (alpha * 255))) {
                mPrivateFlags |= PFLAG_ALPHA_SET;
            } else {
                mPrivateFlags &= ~PFLAG_ALPHA_SET;
            }
            invalidateViewProperty();
            invalidate(true);
        }
    }

    public void setTransitionAlpha(float alpha) {
        ensureTransformationInfo();
        if (mTransformationInfo.mTransitionAlpha != alpha) {
            mTransformationInfo.mTransitionAlpha = alpha;
            invalidate(true);
        }
    }

    public float getTransitionAlpha() { return mTransformationInfo != null ? mTransformationInfo.mTransitionAlpha : 1; }

    public float getX() { return mLeft + getTranslationX(); }

    public void setX(float x) { setTranslationX(x - mLeft); }

    public float getY() { return mTop + getTranslationY(); }

    public void setY(float y) { setTranslationY(y - mTop); }

    public float getZ() { return getElevation() + getTranslationZ(); }

    public void setZ(float z) { setTranslationZ(z - getElevation()); }

    public float getElevation() { return mTransformationInfo != null ? mTransformationInfo.mElevation : 0; }

    public void setElevation(float elevation) {
        if (elevation != getElevation()) {
            transformInfo().mElevation = elevation;
            invalidateViewProperty();
        }
    }

    public float getTranslationX() { return mTransformationInfo != null ? mTransformationInfo.mTranslationX : 0; }

    public void setTranslationX(float translationX) {
        if (translationX != getTranslationX()) {
            beforeTransformChange();
            transformInfo().mTranslationX = translationX;
            afterTransformChange();
        }
    }

    public float getTranslationY() { return mTransformationInfo != null ? mTransformationInfo.mTranslationY : 0; }

    public void setTranslationY(float translationY) {
        if (translationY != getTranslationY()) {
            beforeTransformChange();
            transformInfo().mTranslationY = translationY;
            afterTransformChange();
        }
    }

    public float getTranslationZ() { return mTransformationInfo != null ? mTransformationInfo.mTranslationZ : 0; }

    public void setTranslationZ(float translationZ) {
        if (translationZ != getTranslationZ()) {
            transformInfo().mTranslationZ = translationZ;
            invalidateViewProperty();
        }
    }

    public void setAnimationMatrix(Matrix matrix) {
        beforeTransformChange();
        transformInfo().mAnimationMatrix = matrix != null ? new Matrix(matrix) : null;
        afterTransformChange();
    }

    public Matrix getAnimationMatrix() { return mTransformationInfo != null ? mTransformationInfo.mAnimationMatrix : null; }

    public void transformMatrixToGlobal(Matrix matrix) {
        final ViewParent parent = mParent;
        if (parent instanceof View) {
            final View vp = (View) parent;
            vp.transformMatrixToGlobal(matrix);
            matrix.preTranslate(-vp.mScrollX, -vp.mScrollY);
        }
        matrix.preTranslate(mLeft, mTop);
        if (!hasIdentityMatrix()) matrix.preConcat(getMatrix());
    }

    public void transformMatrixToLocal(Matrix matrix) {
        final ViewParent parent = mParent;
        if (parent instanceof View) {
            final View vp = (View) parent;
            vp.transformMatrixToLocal(matrix);
            matrix.postTranslate(vp.mScrollX, vp.mScrollY);
        }
        matrix.postTranslate(-mLeft, -mTop);
        if (!hasIdentityMatrix()) matrix.postConcat(getInverseMatrix());
    }

    public void setClipBounds(Rect clipBounds) {
        if (clipBounds == mClipBounds || (clipBounds != null && clipBounds.equals(mClipBounds))) return;
        mClipBounds = clipBounds != null ? new Rect(clipBounds) : null;
        invalidate();
    }

    public Rect getClipBounds() { return mClipBounds != null ? new Rect(mClipBounds) : null; }

    public boolean getClipBounds(Rect outRect) {
        if (mClipBounds != null) {
            outRect.set(mClipBounds);
            return true;
        }
        return false;
    }

    public void setOutlineProvider(ViewOutlineProvider provider) {
        mOutlineProvider = provider;
        invalidateOutline();
    }

    public ViewOutlineProvider getOutlineProvider() { return mOutlineProvider; }

    public void invalidateOutline() {}

    public final boolean getClipToOutline() { return mClipToOutline; }

    public void setClipToOutline(boolean clipToOutline) {
        mClipToOutline = clipToOutline;
        invalidate();
    }

    public void setOutlineSpotShadowColor(int color) { mOutlineSpotShadowColor = color; }

    public int getOutlineSpotShadowColor() { return mOutlineSpotShadowColor; }

    public void setOutlineAmbientShadowColor(int color) { mOutlineAmbientShadowColor = color; }

    public int getOutlineAmbientShadowColor() { return mOutlineAmbientShadowColor; }

    public void setLayerType(int layerType, Paint paint) {
        if (layerType < LAYER_TYPE_NONE || layerType > LAYER_TYPE_HARDWARE) {
            throw new IllegalArgumentException("Layer type can only be one of: LAYER_TYPE_NONE, "
                    + "LAYER_TYPE_SOFTWARE or LAYER_TYPE_HARDWARE");
        }
        mLayerType = layerType;
        mLayerPaint = layerType == LAYER_TYPE_NONE ? null : paint;
        invalidate(true);
    }

    public void setLayerPaint(Paint paint) {
        if (mLayerType != LAYER_TYPE_NONE) {
            mLayerPaint = paint;
            invalidate(true);
        }
    }

    public int getLayerType() { return mLayerType; }

    public void buildLayer() {}

    public boolean isHardwareAccelerated() { return false; }

    public void setForceDarkAllowed(boolean allow) { mForceDarkAllowed = allow; }

    public boolean isForceDarkAllowed() { return mForceDarkAllowed; }

    // ---------------------------------------------------------------- drawing cache

    @Deprecated
    public void setDrawingCacheEnabled(boolean enabled) {
        setFlags(enabled ? DRAWING_CACHE_ENABLED : 0, DRAWING_CACHE_ENABLED);
    }

    @Deprecated
    public boolean isDrawingCacheEnabled() { return (mViewFlags & DRAWING_CACHE_ENABLED) == DRAWING_CACHE_ENABLED; }

    @Deprecated
    public Bitmap getDrawingCache() { return getDrawingCache(false); }

    @Deprecated
    public Bitmap getDrawingCache(boolean autoScale) {
        if ((mViewFlags & WILL_NOT_CACHE_DRAWING) == WILL_NOT_CACHE_DRAWING) return null;
        if ((mPrivateFlags & PFLAG_DRAWING_CACHE_VALID) == 0 || mDrawingCache == null) buildDrawingCache(autoScale);
        return mDrawingCache;
    }

    @Deprecated
    public void destroyDrawingCache() { mDrawingCache = null; }

    @Deprecated
    public void setDrawingCacheBackgroundColor(int color) {
        if (color != mDrawingCacheBackgroundColor) {
            mDrawingCacheBackgroundColor = color;
            mPrivateFlags &= ~PFLAG_DRAWING_CACHE_VALID;
        }
    }

    @Deprecated
    public int getDrawingCacheBackgroundColor() { return mDrawingCacheBackgroundColor; }

    @Deprecated
    public void buildDrawingCache() { buildDrawingCache(false); }

    @Deprecated
    public void buildDrawingCache(boolean autoScale) {
        int width = mRight - mLeft;
        int height = mBottom - mTop;
        if (width <= 0 || height <= 0) {
            mDrawingCache = null;
            return;
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        if (mDrawingCacheBackgroundColor != 0) bitmap.eraseColor(mDrawingCacheBackgroundColor);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(-mScrollX, -mScrollY);
        if ((mPrivateFlags & PFLAG_SKIP_DRAW) == PFLAG_SKIP_DRAW) dispatchDraw(canvas);
        else draw(canvas);
        mDrawingCache = bitmap;
        mPrivateFlags |= PFLAG_DRAWING_CACHE_VALID;
    }

    // ---------------------------------------------------------------- scrolling

    public void scrollTo(int x, int y) {
        if (mScrollX != x || mScrollY != y) {
            int oldX = mScrollX;
            int oldY = mScrollY;
            mScrollX = x;
            mScrollY = y;
            onScrollChanged(mScrollX, mScrollY, oldX, oldY);
            if (!awakenScrollBars()) invalidate(true);
        }
    }

    public void scrollBy(int x, int y) { scrollTo(mScrollX + x, mScrollY + y); }

    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        final AttachInfo ai = mAttachInfo;
        if (ai != null) {
            ai.mViewScrollChanged = true;
            ai.mViewRootImpl.scheduleTraversals();
        }
        if (mListenerInfo != null && mListenerInfo.mOnScrollChangeListener != null) {
            mListenerInfo.mOnScrollChangeListener.onScrollChange(this, l, t, oldl, oldt);
        }
    }

    public void setOnScrollChangeListener(OnScrollChangeListener l) { getListenerInfo().mOnScrollChangeListener = l; }

    public void computeScroll() {}

    protected int computeHorizontalScrollRange() { return getWidth(); }

    protected int computeHorizontalScrollOffset() { return mScrollX; }

    protected int computeHorizontalScrollExtent() { return getWidth(); }

    protected int computeVerticalScrollRange() { return getHeight(); }

    protected int computeVerticalScrollOffset() { return mScrollY; }

    protected int computeVerticalScrollExtent() { return getHeight(); }

    public boolean canScrollHorizontally(int direction) {
        final int offset = computeHorizontalScrollOffset();
        final int range = computeHorizontalScrollRange() - computeHorizontalScrollExtent();
        if (range == 0) return false;
        if (direction < 0) return offset > 0;
        return offset < range - 1;
    }

    public boolean canScrollVertically(int direction) {
        final int offset = computeVerticalScrollOffset();
        final int range = computeVerticalScrollRange() - computeVerticalScrollExtent();
        if (range == 0) return false;
        if (direction < 0) return offset > 0;
        return offset < range - 1;
    }

    protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY, int scrollRangeX,
            int scrollRangeY, int maxOverScrollX, int maxOverScrollY, boolean isTouchEvent) {
        final int overScrollMode = mOverScrollMode;
        final boolean canScrollHorizontal = computeHorizontalScrollRange() > computeHorizontalScrollExtent();
        final boolean canScrollVertical = computeVerticalScrollRange() > computeVerticalScrollExtent();
        final boolean overScrollHorizontal = overScrollMode == OVER_SCROLL_ALWAYS
                || (overScrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && canScrollHorizontal);
        final boolean overScrollVertical = overScrollMode == OVER_SCROLL_ALWAYS
                || (overScrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && canScrollVertical);
        int newScrollX = scrollX + deltaX;
        if (!overScrollHorizontal) maxOverScrollX = 0;
        int newScrollY = scrollY + deltaY;
        if (!overScrollVertical) maxOverScrollY = 0;
        final int left = -maxOverScrollX;
        final int right = maxOverScrollX + scrollRangeX;
        final int top = -maxOverScrollY;
        final int bottom = maxOverScrollY + scrollRangeY;
        boolean clampedX = false;
        if (newScrollX > right) {
            newScrollX = right;
            clampedX = true;
        } else if (newScrollX < left) {
            newScrollX = left;
            clampedX = true;
        }
        boolean clampedY = false;
        if (newScrollY > bottom) {
            newScrollY = bottom;
            clampedY = true;
        } else if (newScrollY < top) {
            newScrollY = top;
            clampedY = true;
        }
        onOverScrolled(newScrollX, newScrollY, clampedX, clampedY);
        return clampedX || clampedY;
    }

    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {}

    public int getOverScrollMode() { return mOverScrollMode; }

    public void setOverScrollMode(int overScrollMode) {
        if (overScrollMode != OVER_SCROLL_ALWAYS && overScrollMode != OVER_SCROLL_IF_CONTENT_SCROLLS
                && overScrollMode != OVER_SCROLL_NEVER) {
            throw new IllegalArgumentException("Invalid overscroll mode " + overScrollMode);
        }
        mOverScrollMode = overScrollMode;
    }

    // scroll bars

    private ScrollabilityCache getScrollCache() {
        if (mScrollCache == null) {
            ScrollabilityCache sc = new ScrollabilityCache();
            ViewConfiguration vc = mContext != null ? ViewConfiguration.get(mContext) : new ViewConfiguration();
            sc.scrollBarSize = vc.getScaledScrollBarSize();
            sc.fadingEdgeLength = vc.getScaledFadingEdgeLength();
            sc.scrollBarDefaultDelayBeforeFade = ViewConfiguration.getScrollDefaultDelay();
            sc.scrollBarFadeDuration = ViewConfiguration.getScrollBarFadeDuration();
            if (mContext != null) {
                TypedArray a = mContext.obtainStyledAttributes(new int[] {android.R.attr.scrollbarThumbVertical,
                        android.R.attr.scrollbarThumbHorizontal, android.R.attr.scrollbarSize});
                sc.verticalThumb = a.getDrawable(0);
                sc.horizontalThumb = a.getDrawable(1);
                int size = a.getDimensionPixelSize(2, -1);
                if (size > 0) sc.scrollBarSize = size;
                a.recycle();
            }
            mScrollCache = sc;
        }
        return mScrollCache;
    }

    public boolean isHorizontalScrollBarEnabled() { return (mViewFlags & SCROLLBARS_HORIZONTAL) == SCROLLBARS_HORIZONTAL; }

    public void setHorizontalScrollBarEnabled(boolean horizontalScrollBarEnabled) {
        if (isHorizontalScrollBarEnabled() != horizontalScrollBarEnabled) {
            mViewFlags ^= SCROLLBARS_HORIZONTAL;
            computeOpaqueFlags();
            recomputePadding();
        }
    }

    public boolean isVerticalScrollBarEnabled() { return (mViewFlags & SCROLLBARS_VERTICAL) == SCROLLBARS_VERTICAL; }

    public void setVerticalScrollBarEnabled(boolean verticalScrollBarEnabled) {
        if (isVerticalScrollBarEnabled() != verticalScrollBarEnabled) {
            mViewFlags ^= SCROLLBARS_VERTICAL;
            computeOpaqueFlags();
            recomputePadding();
        }
    }

    public void setScrollbarFadingEnabled(boolean fadeScrollbars) {
        getScrollCache().fadeScrollBars = fadeScrollbars;
        if (!fadeScrollbars) mScrollCache.state = ScrollabilityCache.ON;
    }

    public boolean isScrollbarFadingEnabled() { return mScrollCache == null || mScrollCache.fadeScrollBars; }

    public int getScrollBarDefaultDelayBeforeFade() {
        return mScrollCache == null ? ViewConfiguration.getScrollDefaultDelay()
                : mScrollCache.scrollBarDefaultDelayBeforeFade;
    }

    public void setScrollBarDefaultDelayBeforeFade(int scrollBarDefaultDelayBeforeFade) {
        getScrollCache().scrollBarDefaultDelayBeforeFade = scrollBarDefaultDelayBeforeFade;
    }

    public int getScrollBarFadeDuration() {
        return mScrollCache == null ? ViewConfiguration.getScrollBarFadeDuration() : mScrollCache.scrollBarFadeDuration;
    }

    public void setScrollBarFadeDuration(int scrollBarFadeDuration) {
        getScrollCache().scrollBarFadeDuration = scrollBarFadeDuration;
    }

    public int getScrollBarSize() {
        return mScrollCache == null ? (mContext != null ? ViewConfiguration.get(mContext).getScaledScrollBarSize() : 4)
                : mScrollCache.scrollBarSize;
    }

    public void setScrollBarSize(int scrollBarSize) { getScrollCache().scrollBarSize = scrollBarSize; }

    public void setScrollBarStyle(int style) {
        if (style != (mViewFlags & SCROLLBARS_STYLE_MASK)) {
            mViewFlags = (mViewFlags & ~SCROLLBARS_STYLE_MASK) | (style & SCROLLBARS_STYLE_MASK);
            computeOpaqueFlags();
            recomputePadding();
        }
    }

    public int getScrollBarStyle() { return mViewFlags & SCROLLBARS_STYLE_MASK; }

    public void setVerticalScrollbarPosition(int position) {
        if (mVerticalScrollbarPosition != position) {
            mVerticalScrollbarPosition = position;
            computeOpaqueFlags();
            recomputePadding();
        }
    }

    public int getVerticalScrollbarPosition() { return mVerticalScrollbarPosition; }

    public int getVerticalScrollbarWidth() {
        if (!isVerticalScrollBarEnabled()) return 0;
        return getScrollBarSize();
    }

    protected int getHorizontalScrollbarHeight() {
        if (!isHorizontalScrollBarEnabled()) return 0;
        return getScrollBarSize();
    }

    public void setVerticalScrollbarThumbDrawable(Drawable drawable) { getScrollCache().verticalThumb = drawable; }

    public void setVerticalScrollbarTrackDrawable(Drawable drawable) { getScrollCache().verticalTrack = drawable; }

    public void setHorizontalScrollbarThumbDrawable(Drawable drawable) { getScrollCache().horizontalThumb = drawable; }

    public void setHorizontalScrollbarTrackDrawable(Drawable drawable) { getScrollCache().horizontalTrack = drawable; }

    public Drawable getVerticalScrollbarThumbDrawable() { return mScrollCache != null ? mScrollCache.verticalThumb : null; }

    public Drawable getVerticalScrollbarTrackDrawable() { return mScrollCache != null ? mScrollCache.verticalTrack : null; }

    public Drawable getHorizontalScrollbarThumbDrawable() {
        return mScrollCache != null ? mScrollCache.horizontalThumb : null;
    }

    public Drawable getHorizontalScrollbarTrackDrawable() {
        return mScrollCache != null ? mScrollCache.horizontalTrack : null;
    }

    public void setScrollIndicators(int indicators) { setScrollIndicators(indicators, PFLAG3_SCROLL_INDICATOR_MASK >>> PFLAG3_SCROLL_INDICATOR_SHIFT); }

    public void setScrollIndicators(int indicators, int mask) {
        mask = (mask << PFLAG3_SCROLL_INDICATOR_SHIFT) & PFLAG3_SCROLL_INDICATOR_MASK;
        indicators = (indicators << PFLAG3_SCROLL_INDICATOR_SHIFT) & mask;
        mPrivateFlags3 = (mPrivateFlags3 & ~mask) | indicators;
    }

    public int getScrollIndicators() {
        return (mPrivateFlags3 & PFLAG3_SCROLL_INDICATOR_MASK) >>> PFLAG3_SCROLL_INDICATOR_SHIFT;
    }

    protected boolean awakenScrollBars() {
        return mScrollCache != null && awakenScrollBars(mScrollCache.scrollBarDefaultDelayBeforeFade, true);
    }

    protected boolean awakenScrollBars(int startDelay) { return awakenScrollBars(startDelay, true); }

    protected boolean awakenScrollBars(int startDelay, boolean invalidate) {
        final ScrollabilityCache scrollCache = mScrollCache;
        if (scrollCache == null || !scrollCache.fadeScrollBars && scrollCache.state == ScrollabilityCache.ON) {
            if (scrollCache != null && invalidate) invalidate(true);
            return scrollCache != null && isAnyScrollBarEnabled();
        }
        if (isAnyScrollBarEnabled()) {
            if (invalidate) invalidate(true);
            if (scrollCache.state == ScrollabilityCache.OFF) startDelay = Math.max(750, startDelay);
            scrollCache.state = ScrollabilityCache.ON;
            scrollCache.fadeStartTime = SystemClock.uptimeMillis() + startDelay;
            if (scrollCache.fadeScrollBars && mAttachInfo != null) {
                mAttachInfo.mHandler.removeCallbacks(mScrollBarFade);
                mAttachInfo.mHandler.postAtTime(mScrollBarFade, scrollCache.fadeStartTime);
            }
            return true;
        }
        return false;
    }

    private boolean isAnyScrollBarEnabled() { return isVerticalScrollBarEnabled() || isHorizontalScrollBarEnabled(); }

    private final Runnable mScrollBarFade = new Runnable() {
        public void run() {
            if (mScrollCache != null && mScrollCache.state == ScrollabilityCache.ON) {
                mScrollCache.state = ScrollabilityCache.FADING;
                invalidate(true);
            }
        }
    };

    /** framework-internal (hidden in AOSP). */
    protected boolean isVerticalScrollBarHidden() { return false; }

    protected final void onDrawScrollBars(Canvas canvas) {
        final ScrollabilityCache cache = mScrollCache;
        if (cache == null || cache.state == ScrollabilityCache.OFF) return;
        int alpha = 255;
        if (cache.state == ScrollabilityCache.FADING) {
            long now = SystemClock.uptimeMillis();
            long fadeEnd = cache.fadeStartTime + cache.scrollBarFadeDuration;
            if (now >= fadeEnd) {
                cache.state = ScrollabilityCache.OFF;
                return;
            }
            alpha = (int) (255 * (fadeEnd - now) / Math.max(1, cache.scrollBarFadeDuration));
            postInvalidateOnAnimation();
        }
        final int width = mRight - mLeft;
        final int height = mBottom - mTop;
        final int size = getScrollBarSize();
        final boolean drawH = isHorizontalScrollBarEnabled();
        final boolean drawV = isVerticalScrollBarEnabled() && !isVerticalScrollBarHidden();
        final boolean inset = (mViewFlags & SCROLLBARS_OUTSIDE_MASK) == 0;
        if (drawV) {
            int range = computeVerticalScrollRange();
            int offset = computeVerticalScrollOffset();
            int extent = computeVerticalScrollExtent();
            if (extent < range && range > 0) {
                int top = mScrollY + (inset ? 0 : 0);
                int trackLength = height - (drawH ? size : 0);
                int thumbLength = Math.max(Math.round((float) trackLength * extent / range), size * 2);
                if (thumbLength > trackLength) thumbLength = trackLength;
                int thumbOffset = Math.round((float) (trackLength - thumbLength) * offset / Math.max(1, range - extent));
                thumbOffset = Math.max(0, Math.min(trackLength - thumbLength, thumbOffset));
                int left = mVerticalScrollbarPosition == SCROLLBAR_POSITION_LEFT ? mScrollX
                        : mScrollX + width - size;
                drawThumb(canvas, cache.verticalThumb, left, top + thumbOffset, left + size,
                        top + thumbOffset + thumbLength, alpha);
            }
        }
        if (drawH) {
            int range = computeHorizontalScrollRange();
            int offset = computeHorizontalScrollOffset();
            int extent = computeHorizontalScrollExtent();
            if (extent < range && range > 0) {
                int trackLength = width - (drawV ? size : 0);
                int thumbLength = Math.max(Math.round((float) trackLength * extent / range), size * 2);
                if (thumbLength > trackLength) thumbLength = trackLength;
                int thumbOffset = Math.round((float) (trackLength - thumbLength) * offset / Math.max(1, range - extent));
                thumbOffset = Math.max(0, Math.min(trackLength - thumbLength, thumbOffset));
                int top = mScrollY + height - size;
                drawThumb(canvas, cache.horizontalThumb, mScrollX + thumbOffset, top,
                        mScrollX + thumbOffset + thumbLength, top + size, alpha);
            }
        }
    }

    private static Paint sScrollBarPaint;

    private void drawThumb(Canvas canvas, Drawable thumb, int l, int t, int r, int b, int alpha) {
        if (thumb != null) {
            thumb.setBounds(l, t, r, b);
            thumb.setAlpha(alpha);
            thumb.draw(canvas);
        } else {
            if (sScrollBarPaint == null) sScrollBarPaint = new Paint();
            sScrollBarPaint.setColor((alpha * 0x60 / 255) << 24 | 0x808080);
            canvas.drawRect(l, t, r, b, sScrollBarPaint);
        }
    }

    public boolean isHorizontalFadingEdgeEnabled() { return (mViewFlags & FADING_EDGE_HORIZONTAL) == FADING_EDGE_HORIZONTAL; }

    public void setHorizontalFadingEdgeEnabled(boolean horizontalFadingEdgeEnabled) {
        if (isHorizontalFadingEdgeEnabled() != horizontalFadingEdgeEnabled) mViewFlags ^= FADING_EDGE_HORIZONTAL;
    }

    public boolean isVerticalFadingEdgeEnabled() { return (mViewFlags & FADING_EDGE_VERTICAL) == FADING_EDGE_VERTICAL; }

    public void setVerticalFadingEdgeEnabled(boolean verticalFadingEdgeEnabled) {
        if (isVerticalFadingEdgeEnabled() != verticalFadingEdgeEnabled) mViewFlags ^= FADING_EDGE_VERTICAL;
    }

    public void setFadingEdgeLength(int length) { getScrollCache().fadingEdgeLength = length; }

    public int getVerticalFadingEdgeLength() {
        if (isVerticalFadingEdgeEnabled()) {
            ScrollabilityCache cache = getScrollCache();
            return cache.fadingEdgeLength;
        }
        return 0;
    }

    public int getHorizontalFadingEdgeLength() {
        if (isHorizontalFadingEdgeEnabled()) {
            ScrollabilityCache cache = getScrollCache();
            return cache.fadingEdgeLength;
        }
        return 0;
    }

    protected float getTopFadingEdgeStrength() { return computeVerticalScrollOffset() > 0 ? 1.0f : 0.0f; }

    protected float getBottomFadingEdgeStrength() {
        return computeVerticalScrollOffset() + computeVerticalScrollExtent() < computeVerticalScrollRange() ? 1.0f : 0.0f;
    }

    protected float getLeftFadingEdgeStrength() { return computeHorizontalScrollOffset() > 0 ? 1.0f : 0.0f; }

    protected float getRightFadingEdgeStrength() {
        return computeHorizontalScrollOffset() + computeHorizontalScrollExtent() < computeHorizontalScrollRange()
                ? 1.0f : 0.0f;
    }

    // ---------------------------------------------------------------- listeners

    public void setOnFocusChangeListener(OnFocusChangeListener l) { getListenerInfo().mOnFocusChangeListener = l; }

    public OnFocusChangeListener getOnFocusChangeListener() {
        return mListenerInfo != null ? mListenerInfo.mOnFocusChangeListener : null;
    }

    public void addOnLayoutChangeListener(OnLayoutChangeListener listener) {
        ListenerInfo li = getListenerInfo();
        if (li.mOnLayoutChangeListeners == null) li.mOnLayoutChangeListeners = new ArrayList<OnLayoutChangeListener>();
        if (!li.mOnLayoutChangeListeners.contains(listener)) li.mOnLayoutChangeListeners.add(listener);
    }

    public void removeOnLayoutChangeListener(OnLayoutChangeListener listener) {
        ListenerInfo li = mListenerInfo;
        if (li == null || li.mOnLayoutChangeListeners == null) return;
        li.mOnLayoutChangeListeners.remove(listener);
    }

    public void addOnAttachStateChangeListener(OnAttachStateChangeListener listener) {
        ListenerInfo li = getListenerInfo();
        if (li.mOnAttachStateChangeListeners == null) {
            li.mOnAttachStateChangeListeners = new CopyOnWriteArrayList<OnAttachStateChangeListener>();
        }
        li.mOnAttachStateChangeListeners.add(listener);
    }

    public void removeOnAttachStateChangeListener(OnAttachStateChangeListener listener) {
        ListenerInfo li = mListenerInfo;
        if (li == null || li.mOnAttachStateChangeListeners == null) return;
        li.mOnAttachStateChangeListeners.remove(listener);
    }

    public void setOnClickListener(OnClickListener l) {
        if (!isClickable()) setClickable(true);
        getListenerInfo().mOnClickListener = l;
    }

    public boolean hasOnClickListeners() {
        ListenerInfo li = mListenerInfo;
        return (li != null && li.mOnClickListener != null);
    }

    public void setOnLongClickListener(OnLongClickListener l) {
        if (!isLongClickable()) setLongClickable(true);
        getListenerInfo().mOnLongClickListener = l;
    }

    public boolean hasOnLongClickListeners() {
        ListenerInfo li = mListenerInfo;
        return (li != null && li.mOnLongClickListener != null);
    }

    public void setOnContextClickListener(OnContextClickListener l) {
        if (!isContextClickable()) setContextClickable(true);
        getListenerInfo().mOnContextClickListener = l;
    }

    public void setOnCreateContextMenuListener(OnCreateContextMenuListener l) {
        if (!isLongClickable()) setLongClickable(true);
        getListenerInfo().mOnCreateContextMenuListener = l;
    }

    public void setOnKeyListener(OnKeyListener l) { getListenerInfo().mOnKeyListener = l; }

    public void setOnTouchListener(OnTouchListener l) { getListenerInfo().mOnTouchListener = l; }

    public void setOnGenericMotionListener(OnGenericMotionListener l) { getListenerInfo().mOnGenericMotionListener = l; }

    public void setOnHoverListener(OnHoverListener l) { getListenerInfo().mOnHoverListener = l; }

    public void setOnDragListener(OnDragListener l) { getListenerInfo().mOnDragListener = l; }

    public void setOnSystemUiVisibilityChangeListener(OnSystemUiVisibilityChangeListener l) {
        getListenerInfo().mOnSystemUiVisibilityChangeListener = l;
    }

    public void setOnApplyWindowInsetsListener(OnApplyWindowInsetsListener listener) {
        getListenerInfo().mOnApplyWindowInsetsListener = listener;
    }

    public void setOnCapturedPointerListener(OnCapturedPointerListener l) {
        getListenerInfo().mOnCapturedPointerListener = l;
    }

    public void addOnUnhandledKeyEventListener(OnUnhandledKeyEventListener listener) {
        ListenerInfo li = getListenerInfo();
        if (li.mUnhandledKeyListeners == null) li.mUnhandledKeyListeners = new ArrayList<OnUnhandledKeyEventListener>();
        li.mUnhandledKeyListeners.add(listener);
    }

    public void removeOnUnhandledKeyEventListener(OnUnhandledKeyEventListener listener) {
        if (mListenerInfo != null && mListenerInfo.mUnhandledKeyListeners != null) {
            mListenerInfo.mUnhandledKeyListeners.remove(listener);
        }
    }

    /** framework-internal (hidden in AOSP). */
    boolean dispatchUnhandledKeyEvent(KeyEvent evt) {
        ListenerInfo li = mListenerInfo;
        if (li != null && li.mUnhandledKeyListeners != null) {
            for (int i = li.mUnhandledKeyListeners.size() - 1; i >= 0; --i) {
                if (li.mUnhandledKeyListeners.get(i).onUnhandledKeyEvent(this, evt)) return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- clicks

    public boolean performClick() {
        final boolean result;
        final ListenerInfo li = mListenerInfo;
        if (li != null && li.mOnClickListener != null) {
            playSoundEffect(SoundEffectConstants.CLICK);
            li.mOnClickListener.onClick(this);
            result = true;
        } else {
            result = false;
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
        return result;
    }

    private boolean performClickInternal() { return performClick(); }

    public boolean callOnClick() {
        ListenerInfo li = mListenerInfo;
        if (li != null && li.mOnClickListener != null) {
            li.mOnClickListener.onClick(this);
            return true;
        }
        return false;
    }

    public boolean performLongClick() { return performLongClickInternal(-1f, -1f); }

    public boolean performLongClick(float x, float y) {
        if (Float.isNaN(x) || Float.isNaN(y)) return performLongClick();
        return performLongClickInternal(x, y);
    }

    private boolean performLongClickInternal(float x, float y) {
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
        boolean handled = false;
        final ListenerInfo li = mListenerInfo;
        if (li != null && li.mOnLongClickListener != null) handled = li.mOnLongClickListener.onLongClick(View.this);
        if (!handled) {
            final boolean isAnchored = !Float.isNaN(x) && !Float.isNaN(y) && x >= 0 && y >= 0;
            handled = isAnchored ? showContextMenu(x, y) : showContextMenu();
        }
        if (handled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        return handled;
    }

    public boolean performContextClick(float x, float y) { return performContextClick(); }

    public boolean performContextClick() {
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED);
        boolean handled = false;
        ListenerInfo li = mListenerInfo;
        if (li != null && li.mOnContextClickListener != null) handled = li.mOnContextClickListener.onContextClick(View.this);
        if (handled) performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        return handled;
    }

    public boolean showContextMenu() { return getParent() != null && getParent().showContextMenuForChild(this); }

    public boolean showContextMenu(float x, float y) {
        return getParent() != null && getParent().showContextMenuForChild(this, x, y);
    }

    public void createContextMenu(ContextMenu menu) {
        ContextMenu.ContextMenuInfo menuInfo = getContextMenuInfo();
        onCreateContextMenu(menu);
        ListenerInfo li = mListenerInfo;
        if (li != null && li.mOnCreateContextMenuListener != null) {
            li.mOnCreateContextMenuListener.onCreateContextMenu(menu, this, menuInfo);
        }
        if (mParent != null) mParent.createContextMenu(menu);
    }

    protected ContextMenu.ContextMenuInfo getContextMenuInfo() { return null; }

    protected void onCreateContextMenu(ContextMenu menu) {}

    public ActionMode startActionMode(ActionMode.Callback callback) { return startActionMode(callback, ActionMode.TYPE_PRIMARY); }

    public ActionMode startActionMode(ActionMode.Callback callback, int type) {
        ViewParent parent = getParent();
        if (parent == null) return null;
        try {
            return parent.startActionModeForChild(this, callback, type);
        } catch (AbstractMethodError ame) {
            return parent.startActionModeForChild(this, callback);
        }
    }

    public void cancelLongPress() {
        removeLongPressCallback();
        removeTapCallback();
    }

    private void removeLongPressCallback() {
        if (mPendingCheckForLongPress != null) removeCallbacks(mPendingCheckForLongPress);
    }

    private void removePerformClickCallback() {
        if (mPerformClick != null) removeCallbacks(mPerformClick);
    }

    private void removeUnsetPressCallback() {
        if ((mPrivateFlags & PFLAG_PRESSED) != 0 && mUnsetPressedState != null) {
            setPressed(false);
            removeCallbacks(mUnsetPressedState);
        }
    }

    private void removeTapCallback() {
        if (mPendingCheckForTap != null) {
            mPrivateFlags &= ~PFLAG_PREPRESSED;
            removeCallbacks(mPendingCheckForTap);
        }
    }

    private void checkForLongClick(long delay, float x, float y) {
        if ((mViewFlags & LONG_CLICKABLE) == LONG_CLICKABLE || (mViewFlags & TOOLTIP) == TOOLTIP) {
            mHasPerformedLongPress = false;
            if (mPendingCheckForLongPress == null) mPendingCheckForLongPress = new CheckForLongPress();
            mPendingCheckForLongPress.setAnchor(x, y);
            mPendingCheckForLongPress.rememberWindowAttachCount();
            mPendingCheckForLongPress.rememberPressedState();
            postDelayed(mPendingCheckForLongPress, delay);
        }
    }

    private final class CheckForLongPress implements Runnable {
        private int mOriginalWindowAttachCount;
        private float mX;
        private float mY;
        private boolean mOriginalPressedState;

        public void run() {
            if ((mOriginalPressedState == isPressed()) && (mParent != null)
                    && mOriginalWindowAttachCount == mWindowAttachCount) {
                if (performLongClick(mX, mY)) mHasPerformedLongPress = true;
            }
        }

        void setAnchor(float x, float y) {
            mX = x;
            mY = y;
        }

        void rememberWindowAttachCount() { mOriginalWindowAttachCount = mWindowAttachCount; }

        void rememberPressedState() { mOriginalPressedState = isPressed(); }
    }

    private final class CheckForTap implements Runnable {
        float x;
        float y;

        public void run() {
            mPrivateFlags &= ~PFLAG_PREPRESSED;
            setPressed(true, x, y);
            final long delay = ViewConfiguration.getLongPressTimeout() - ViewConfiguration.getTapTimeout();
            checkForLongClick(delay, x, y);
        }
    }

    private final class PerformClick implements Runnable {
        public void run() { performClickInternal(); }
    }

    private final class UnsetPressedState implements Runnable {
        public void run() { setPressed(false); }
    }

    // ---------------------------------------------------------------- touch

    public final boolean dispatchPointerEvent(MotionEvent event) {
        if (event.isTouchEvent()) return dispatchTouchEvent(event);
        return dispatchGenericMotionEvent(event);
    }

    public boolean dispatchTouchEvent(MotionEvent event) {
        boolean result = false;
        final int actionMasked = event.getActionMasked();
        if (actionMasked == MotionEvent.ACTION_DOWN) stopNestedScroll();
        if (onFilterTouchEventForSecurity(event)) {
            ListenerInfo li = mListenerInfo;
            if (li != null && li.mOnTouchListener != null && (mViewFlags & ENABLED_MASK) == ENABLED
                    && li.mOnTouchListener.onTouch(this, event)) {
                result = true;
            }
            if (!result && onTouchEvent(event)) result = true;
        }
        if (actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_CANCEL
                || (actionMasked == MotionEvent.ACTION_DOWN && !result)) {
            stopNestedScroll();
        }
        return result;
    }

    public boolean onFilterTouchEventForSecurity(MotionEvent event) {
        if ((mViewFlags & FILTER_TOUCHES_WHEN_OBSCURED) != 0
                && (event.getFlags() & MotionEvent.FLAG_WINDOW_IS_OBSCURED) != 0) {
            return false;
        }
        return true;
    }

    /** framework-internal (hidden in AOSP). */
    public boolean pointInView(float localX, float localY) { return pointInView(localX, localY, 0); }

    /** framework-internal (hidden in AOSP). */
    public boolean pointInView(float localX, float localY, float slop) {
        return localX >= -slop && localY >= -slop && localX < ((mRight - mLeft) + slop)
                && localY < ((mBottom - mTop) + slop);
    }

    /** framework-internal (hidden in AOSP). */
    public boolean isInScrollingContainer() {
        ViewParent p = getParent();
        while (p instanceof ViewGroup) {
            if (((ViewGroup) p).shouldDelayChildPressedState()) return true;
            p = p.getParent();
        }
        return false;
    }

    public boolean onTouchEvent(MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();
        final int viewFlags = mViewFlags;
        final int action = event.getAction();
        final boolean clickable = ((viewFlags & CLICKABLE) == CLICKABLE || (viewFlags & LONG_CLICKABLE) == LONG_CLICKABLE)
                || (viewFlags & CONTEXT_CLICKABLE) == CONTEXT_CLICKABLE;

        if ((viewFlags & ENABLED_MASK) == DISABLED && !mAllowClickWhenDisabled) {
            if (action == MotionEvent.ACTION_UP && (mPrivateFlags & PFLAG_PRESSED) != 0) setPressed(false);
            mPrivateFlags3 &= ~PFLAG3_FINGER_DOWN;
            return clickable;
        }
        if (mTouchDelegate != null) {
            if (mTouchDelegate.onTouchEvent(event)) return true;
        }

        if (clickable || (viewFlags & TOOLTIP) == TOOLTIP) {
            switch (action) {
                case MotionEvent.ACTION_UP: {
                    mPrivateFlags3 &= ~PFLAG3_FINGER_DOWN;
                    if (!clickable) {
                        removeTapCallback();
                        removeLongPressCallback();
                        mInContextButtonPress = false;
                        mHasPerformedLongPress = false;
                        mIgnoreNextUpEvent = false;
                        break;
                    }
                    boolean prepressed = (mPrivateFlags & PFLAG_PREPRESSED) != 0;
                    if ((mPrivateFlags & PFLAG_PRESSED) != 0 || prepressed) {
                        boolean focusTaken = false;
                        if (isFocusable() && isFocusableInTouchMode() && !isFocused()) focusTaken = requestFocus();
                        if (prepressed) setPressed(true, x, y);
                        if (!mHasPerformedLongPress && !mIgnoreNextUpEvent) {
                            removeLongPressCallback();
                            if (!focusTaken) {
                                if (mPerformClick == null) mPerformClick = new PerformClick();
                                if (!post(mPerformClick)) performClickInternal();
                            }
                        }
                        if (mUnsetPressedState == null) mUnsetPressedState = new UnsetPressedState();
                        if (prepressed) {
                            postDelayed(mUnsetPressedState, ViewConfiguration.getPressedStateDuration());
                        } else if (!post(mUnsetPressedState)) {
                            mUnsetPressedState.run();
                        }
                        removeTapCallback();
                    }
                    mIgnoreNextUpEvent = false;
                    break;
                }
                case MotionEvent.ACTION_DOWN: {
                    mPrivateFlags3 |= PFLAG3_FINGER_DOWN;
                    mHasPerformedLongPress = false;
                    if (!clickable) {
                        checkForLongClick(ViewConfiguration.getLongPressTimeout(), x, y);
                        break;
                    }
                    if (performButtonActionOnTouchDown(event)) break;
                    boolean isInScrollingContainer = isInScrollingContainer();
                    if (isInScrollingContainer) {
                        mPrivateFlags |= PFLAG_PREPRESSED;
                        if (mPendingCheckForTap == null) mPendingCheckForTap = new CheckForTap();
                        mPendingCheckForTap.x = event.getX();
                        mPendingCheckForTap.y = event.getY();
                        postDelayed(mPendingCheckForTap, ViewConfiguration.getTapTimeout());
                    } else {
                        setPressed(true, x, y);
                        checkForLongClick(ViewConfiguration.getLongPressTimeout(), x, y);
                    }
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                    if (clickable) setPressed(false);
                    removeTapCallback();
                    removeLongPressCallback();
                    mInContextButtonPress = false;
                    mHasPerformedLongPress = false;
                    mIgnoreNextUpEvent = false;
                    mPrivateFlags3 &= ~PFLAG3_FINGER_DOWN;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (clickable) drawableHotspotChanged(x, y);
                    if (!pointInView(x, y, mTouchSlop)) {
                        removeTapCallback();
                        removeLongPressCallback();
                        if ((mPrivateFlags & PFLAG_PRESSED) != 0) setPressed(false);
                        mPrivateFlags3 &= ~PFLAG3_FINGER_DOWN;
                    }
                    break;
            }
            return true;
        }
        return false;
    }

    /** framework-internal (hidden in AOSP, protected). */
    protected boolean performButtonActionOnTouchDown(MotionEvent event) {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)
                && (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
            showContextMenu(event.getX(), event.getY());
            mPrivateFlags |= PFLAG_CANCEL_NEXT_UP_EVENT;
            return true;
        }
        return false;
    }

    public void setTouchDelegate(TouchDelegate delegate) { mTouchDelegate = delegate; }

    public TouchDelegate getTouchDelegate() { return mTouchDelegate; }

    public final void requestUnbufferedDispatch(MotionEvent event) {}

    public final void requestUnbufferedDispatch(int source) {}

    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        final int source = event.getSource();
        if ((source & InputDevice.SOURCE_CLASS_POINTER) != 0) {
            final int action = event.getAction();
            if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE
                    || action == MotionEvent.ACTION_HOVER_EXIT) {
                if (dispatchHoverEvent(event)) return true;
            } else if (dispatchGenericPointerEvent(event)) {
                return true;
            }
        } else if (dispatchGenericFocusedEvent(event)) {
            return true;
        }
        if (dispatchGenericMotionEventInternal(event)) return true;
        return false;
    }

    private boolean dispatchGenericMotionEventInternal(MotionEvent event) {
        ListenerInfo li = mListenerInfo;
        if (li != null && li.mOnGenericMotionListener != null && (mViewFlags & ENABLED_MASK) == ENABLED
                && li.mOnGenericMotionListener.onGenericMotion(this, event)) {
            return true;
        }
        if (onGenericMotionEvent(event)) return true;
        return false;
    }

    public boolean onGenericMotionEvent(MotionEvent event) { return false; }

    protected boolean dispatchHoverEvent(MotionEvent event) {
        ListenerInfo li = mListenerInfo;
        if (li != null && li.mOnHoverListener != null && (mViewFlags & ENABLED_MASK) == ENABLED
                && li.mOnHoverListener.onHover(this, event)) {
            return true;
        }
        return onHoverEvent(event);
    }

    public boolean onHoverEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_HOVER_ENTER) setHovered(true);
        else if (action == MotionEvent.ACTION_HOVER_EXIT) setHovered(false);
        return false;
    }

    protected boolean dispatchGenericPointerEvent(MotionEvent event) { return false; }

    protected boolean dispatchGenericFocusedEvent(MotionEvent event) { return false; }

    public boolean dispatchTrackballEvent(MotionEvent event) { return onTrackballEvent(event); }

    public boolean onTrackballEvent(MotionEvent event) { return false; }

    public boolean dispatchCapturedPointerEvent(MotionEvent event) {
        if (!hasPointerCapture()) return false;
        ListenerInfo li = mListenerInfo;
        if (li != null && li.mOnCapturedPointerListener != null
                && li.mOnCapturedPointerListener.onCapturedPointer(this, event)) {
            return true;
        }
        return onCapturedPointerEvent(event);
    }

    public boolean onCapturedPointerEvent(MotionEvent event) { return false; }

    public void requestPointerCapture() {}

    public void releasePointerCapture() {}

    public boolean hasPointerCapture() { return false; }

    public void onPointerCaptureChange(boolean hasCapture) {}

    public void dispatchPointerCaptureChanged(boolean hasCapture) { onPointerCaptureChange(hasCapture); }

    public PointerIcon getPointerIcon() { return null; }

    public void setPointerIcon(PointerIcon pointerIcon) {}

    public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) { return null; }

    public boolean dispatchDragEvent(DragEvent event) {
        ListenerInfo li = mListenerInfo;
        if (li != null && li.mOnDragListener != null && (mViewFlags & ENABLED_MASK) == ENABLED
                && li.mOnDragListener.onDrag(this, event)) {
            return true;
        }
        return onDragEvent(event);
    }

    public boolean onDragEvent(DragEvent event) { return false; }

    @Deprecated
    public final boolean startDrag(android.content.ClipData data, DragShadowBuilder shadowBuilder,
            Object myLocalState, int flags) {
        return startDragAndDrop(data, shadowBuilder, myLocalState, flags);
    }

    public final boolean startDragAndDrop(android.content.ClipData data, DragShadowBuilder shadowBuilder,
            Object myLocalState, int flags) {
        Log.w(TAG, "startDragAndDrop is not supported");
        return false;
    }

    public final void cancelDragAndDrop() {}

    public final void updateDragShadow(DragShadowBuilder shadowBuilder) {}

    // ---------------------------------------------------------------- keys

    public KeyEvent.DispatcherState getKeyDispatcherState() {
        return mAttachInfo != null ? mAttachInfo.mKeyDispatchState : null;
    }

    public boolean dispatchKeyEventPreIme(KeyEvent event) { return onKeyPreIme(event.getKeyCode(), event); }

    public boolean dispatchKeyEvent(KeyEvent event) {
        ListenerInfo li = mListenerInfo;
        if (li != null && li.mOnKeyListener != null && (mViewFlags & ENABLED_MASK) == ENABLED
                && li.mOnKeyListener.onKey(this, event.getKeyCode(), event)) {
            return true;
        }
        if (event.dispatch(this, mAttachInfo != null ? mAttachInfo.mKeyDispatchState : null, this)) return true;
        return false;
    }

    public boolean dispatchKeyShortcutEvent(KeyEvent event) { return onKeyShortcut(event.getKeyCode(), event); }

    public boolean onKeyPreIme(int keyCode, KeyEvent event) { return false; }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (KeyEvent.isConfirmKey(keyCode)) {
            if ((mViewFlags & ENABLED_MASK) == DISABLED) return true;
            if (event.getRepeatCount() == 0) {
                final boolean clickable = (mViewFlags & CLICKABLE) == CLICKABLE
                        || (mViewFlags & LONG_CLICKABLE) == LONG_CLICKABLE;
                if (clickable || (mViewFlags & TOOLTIP) == TOOLTIP) {
                    final float x = getWidth() / 2f;
                    final float y = getHeight() / 2f;
                    if (clickable) setPressed(true, x, y);
                    checkForLongClick(ViewConfiguration.getLongPressTimeout(), x, y);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean onKeyLongPress(int keyCode, KeyEvent event) { return false; }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (KeyEvent.isConfirmKey(keyCode)) {
            if ((mViewFlags & ENABLED_MASK) == DISABLED) return true;
            if ((mViewFlags & CLICKABLE) == CLICKABLE && isPressed()) {
                setPressed(false);
                if (!mHasPerformedLongPress) {
                    removeLongPressCallback();
                    if (!event.isCanceled()) return performClickInternal();
                }
            }
        }
        return false;
    }

    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) { return false; }

    public boolean onKeyShortcut(int keyCode, KeyEvent event) { return false; }

    public boolean onCheckIsTextEditor() { return false; }

    public android.view.inputmethod.InputConnection onCreateInputConnection(
            android.view.inputmethod.EditorInfo outAttrs) {
        return null;
    }

    public boolean checkInputConnectionProxy(View view) { return false; }

    public final void cancelPendingInputEvents() {
        removePerformClickCallback();
        cancelLongPress();
        onCancelPendingInputEvents();
    }

    public void onCancelPendingInputEvents() {}

    public boolean dispatchUnhandledMove(View focused, int direction) { return false; }

    // ---------------------------------------------------------------- focus

    public boolean hasFocus() { return (mPrivateFlags & PFLAG_FOCUSED) != 0; }

    public boolean isFocused() { return (mPrivateFlags & PFLAG_FOCUSED) != 0; }

    public View findFocus() { return (mPrivateFlags & PFLAG_FOCUSED) != 0 ? this : null; }

    public boolean hasFocusable() { return hasFocusable(true, false); }

    public boolean hasExplicitFocusable() { return hasFocusable(false, true); }

    boolean hasFocusable(boolean allowAutoFocus, boolean dispatchExplicit) {
        if (!isFocusableInTouchMode()) {
            for (ViewParent p = mParent; p instanceof ViewGroup; p = p.getParent()) {
                final ViewGroup g = (ViewGroup) p;
                if (g.shouldBlockFocusForTouchscreen()) return false;
            }
        }
        if ((mViewFlags & VISIBILITY_MASK) != VISIBLE || (mViewFlags & ENABLED_MASK) != ENABLED) return false;
        if ((allowAutoFocus || getFocusable() != FOCUSABLE_AUTO) && isFocusable()) return true;
        return false;
    }

    public final boolean requestFocus() { return requestFocus(View.FOCUS_DOWN); }

    public boolean restoreDefaultFocus() { return requestFocus(View.FOCUS_DOWN); }

    public final boolean requestFocus(int direction) { return requestFocus(direction, null); }

    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        return requestFocusNoSearch(direction, previouslyFocusedRect);
    }

    private boolean requestFocusNoSearch(int direction, Rect previouslyFocusedRect) {
        if (!canTakeFocus()) return false;
        if (isInTouchMode() && (FOCUSABLE_IN_TOUCH_MODE != (mViewFlags & FOCUSABLE_IN_TOUCH_MODE))) return false;
        if (hasAncestorThatBlocksDescendantFocus()) return false;
        handleFocusGainInternal(direction, previouslyFocusedRect);
        return true;
    }

    public final boolean requestFocusFromTouch() {
        if (isInTouchMode()) {
            ViewRootImpl viewRoot = getViewRootImpl();
            if (viewRoot != null) viewRoot.ensureTouchMode(false);
        }
        return requestFocus(View.FOCUS_DOWN);
    }

    private boolean hasAncestorThatBlocksDescendantFocus() {
        final boolean focusableInTouchMode = isFocusableInTouchMode();
        ViewParent ancestor = mParent;
        while (ancestor instanceof ViewGroup) {
            final ViewGroup vgAncestor = (ViewGroup) ancestor;
            if (vgAncestor.getDescendantFocusability() == ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    || (!focusableInTouchMode && vgAncestor.shouldBlockFocusForTouchscreen())) {
                return true;
            }
            ancestor = vgAncestor.getParent();
        }
        return false;
    }

    void handleFocusGainInternal(int direction, Rect previouslyFocusedRect) {
        if ((mPrivateFlags & PFLAG_FOCUSED) == 0) {
            mPrivateFlags |= PFLAG_FOCUSED;
            View oldFocus = (mAttachInfo != null) ? getRootView().findFocus() : null;
            if (mParent != null) mParent.requestChildFocus(this, this);
            if (mAttachInfo != null) mAttachInfo.mTreeObserver.dispatchOnGlobalFocusChange(oldFocus, this);
            onFocusChanged(true, direction, previouslyFocusedRect);
            refreshDrawableState();
        }
    }

    public void clearFocus() { clearFocusInternal(null, true, !isInTouchMode()); }

    void clearFocusInternal(View focused, boolean propagate, boolean refocus) {
        if ((mPrivateFlags & PFLAG_FOCUSED) != 0) {
            mPrivateFlags &= ~PFLAG_FOCUSED;
            if (propagate && mParent != null) mParent.clearChildFocus(this);
            onFocusChanged(false, 0, null);
            refreshDrawableState();
            if (propagate && (!refocus || !rootViewRequestFocus())) notifyGlobalFocusCleared(this);
        }
    }

    void notifyGlobalFocusCleared(View oldFocus) {
        if (oldFocus != null && mAttachInfo != null) mAttachInfo.mTreeObserver.dispatchOnGlobalFocusChange(oldFocus, null);
    }

    boolean rootViewRequestFocus() {
        final View root = getRootView();
        return root != null && root.requestFocus();
    }

    void unFocus(View focused) { clearFocusInternal(focused, false, false); }

    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        if (gainFocus) sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        if (!gainFocus) {
            if (isPressed()) setPressed(false);
            onFocusLost();
        } else if (!isInTouchMode()) {
            // bring the newly focused view into view (AOSP: requestRectangleOnScreen on reveal)
            if ((mPrivateFlags3 & PFLAG3_NO_REVEAL_ON_FOCUS) == 0 && mAttachInfo != null) {
                Rect r = new Rect(0, 0, getWidth(), getHeight());
                requestRectangleOnScreen(r, false);
            }
        }
        invalidate(true);
        ListenerInfo li = mListenerInfo;
        if (li != null && li.mOnFocusChangeListener != null) li.mOnFocusChangeListener.onFocusChange(this, gainFocus);
        if (mAttachInfo != null) mAttachInfo.mKeyDispatchState.reset(this);
    }

    /** framework-internal (hidden in AOSP, protected). */
    protected void onFocusLost() { resetPressedState(); }

    public View focusSearch(int direction) {
        if (mParent != null) return mParent.focusSearch(this, direction);
        return null;
    }

    public View keyboardNavigationClusterSearch(View currentCluster, int direction) { return null; }

    public final boolean isKeyboardNavigationCluster() { return (mPrivateFlags3 & PFLAG3_CLUSTER) != 0; }

    public void setKeyboardNavigationCluster(boolean isCluster) {
        if (isCluster) mPrivateFlags3 |= PFLAG3_CLUSTER;
        else mPrivateFlags3 &= ~PFLAG3_CLUSTER;
    }

    public void addKeyboardNavigationClusters(Collection<View> views, int direction) {
        if (!isKeyboardNavigationCluster() || !hasFocusable()) return;
        views.add(this);
    }

    public final boolean isFocusedByDefault() { return (mPrivateFlags3 & PFLAG3_FOCUSED_BY_DEFAULT) != 0; }

    public void setFocusedByDefault(boolean isFocusedByDefault) {
        if (isFocusedByDefault == ((mPrivateFlags3 & PFLAG3_FOCUSED_BY_DEFAULT) != 0)) return;
        if (isFocusedByDefault) mPrivateFlags3 |= PFLAG3_FOCUSED_BY_DEFAULT;
        else mPrivateFlags3 &= ~PFLAG3_FOCUSED_BY_DEFAULT;
        if (mParent instanceof ViewGroup) {
            if (isFocusedByDefault) ((ViewGroup) mParent).setDefaultFocus(this);
            else ((ViewGroup) mParent).clearDefaultFocus(this);
        }
    }

    /** framework-internal (hidden in AOSP). */
    boolean hasDefaultFocus() { return isFocusedByDefault(); }

    public final void setRevealOnFocusHint(boolean revealOnFocus) {
        if (revealOnFocus) mPrivateFlags3 &= ~PFLAG3_NO_REVEAL_ON_FOCUS;
        else mPrivateFlags3 |= PFLAG3_NO_REVEAL_ON_FOCUS;
    }

    public final boolean getRevealOnFocusHint() { return (mPrivateFlags3 & PFLAG3_NO_REVEAL_ON_FOCUS) == 0; }

    public ArrayList<View> getFocusables(int direction) {
        ArrayList<View> result = new ArrayList<View>(24);
        addFocusables(result, direction);
        return result;
    }

    public void addFocusables(ArrayList<View> views, int direction) {
        addFocusables(views, direction, isInTouchMode() ? FOCUSABLES_TOUCH_MODE : FOCUSABLES_ALL);
    }

    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        if (views == null) return;
        if (!canTakeFocus()) return;
        if ((focusableMode & FOCUSABLES_TOUCH_MODE) == FOCUSABLES_TOUCH_MODE && !isFocusableInTouchMode()) return;
        views.add(this);
    }

    public ArrayList<View> getTouchables() {
        ArrayList<View> result = new ArrayList<View>();
        addTouchables(result);
        return result;
    }

    public void addTouchables(ArrayList<View> views) {
        final int viewFlags = mViewFlags;
        if (((viewFlags & CLICKABLE) == CLICKABLE || (viewFlags & LONG_CLICKABLE) == LONG_CLICKABLE
                || (viewFlags & CONTEXT_CLICKABLE) == CONTEXT_CLICKABLE)
                && (viewFlags & ENABLED_MASK) == ENABLED) {
            views.add(this);
        }
    }

    public void getFocusedRect(Rect r) { getDrawingRect(r); }

    public void setNextFocusLeftId(int nextFocusLeftId) { mNextFocusLeftId = nextFocusLeftId; }
    public int getNextFocusLeftId() { return mNextFocusLeftId; }
    public void setNextFocusRightId(int nextFocusRightId) { mNextFocusRightId = nextFocusRightId; }
    public int getNextFocusRightId() { return mNextFocusRightId; }
    public void setNextFocusUpId(int nextFocusUpId) { mNextFocusUpId = nextFocusUpId; }
    public int getNextFocusUpId() { return mNextFocusUpId; }
    public void setNextFocusDownId(int nextFocusDownId) { mNextFocusDownId = nextFocusDownId; }
    public int getNextFocusDownId() { return mNextFocusDownId; }
    public void setNextFocusForwardId(int nextFocusForwardId) { mNextFocusForwardId = nextFocusForwardId; }
    public int getNextFocusForwardId() { return mNextFocusForwardId; }
    public void setNextClusterForwardId(int nextClusterForwardId) { mNextClusterForwardId = nextClusterForwardId; }
    public int getNextClusterForwardId() { return mNextClusterForwardId; }

    View findUserSetNextFocus(View root, int direction) {
        switch (direction) {
            case FOCUS_LEFT:
                if (mNextFocusLeftId == View.NO_ID) return null;
                return findViewInsideOutShouldExist(root, mNextFocusLeftId);
            case FOCUS_RIGHT:
                if (mNextFocusRightId == View.NO_ID) return null;
                return findViewInsideOutShouldExist(root, mNextFocusRightId);
            case FOCUS_UP:
                if (mNextFocusUpId == View.NO_ID) return null;
                return findViewInsideOutShouldExist(root, mNextFocusUpId);
            case FOCUS_DOWN:
                if (mNextFocusDownId == View.NO_ID) return null;
                return findViewInsideOutShouldExist(root, mNextFocusDownId);
            case FOCUS_FORWARD:
                if (mNextFocusForwardId == View.NO_ID) return null;
                return findViewInsideOutShouldExist(root, mNextFocusForwardId);
            case FOCUS_BACKWARD: {
                if (mID == View.NO_ID) return null;
                final int id = mID;
                return root.findViewByPredicateInsideOut(this, new java.util.function.Predicate<View>() {
                    public boolean test(View t) { return t.mNextFocusForwardId == id; }
                });
            }
        }
        return null;
    }

    private View findViewInsideOutShouldExist(View root, int id) {
        View result = root.findViewByPredicateInsideOut(this, new MatchIdPredicate(id));
        if (result == null) Log.w(TAG, "couldn't find view with id " + id);
        return result;
    }

    private static class MatchIdPredicate implements java.util.function.Predicate<View> {
        final int mId;

        MatchIdPredicate(int id) { mId = id; }

        public boolean test(View view) { return view.mID == mId; }
    }

    /** framework-internal (hidden in AOSP). Searches this subtree, then siblings outwards from start. */
    public final View findViewByPredicateInsideOut(View start, java.util.function.Predicate<View> predicate) {
        View childToSkip = null;
        for (;;) {
            View view = start.findViewByPredicateTraversal(predicate, childToSkip);
            if (view != null || start == this) return view;
            ViewParent parent = start.getParent();
            if (parent == null || !(parent instanceof View)) return null;
            childToSkip = start;
            start = (View) parent;
        }
    }

    /** framework-internal (hidden in AOSP). */
    public final View findViewByPredicate(java.util.function.Predicate<View> predicate) {
        return findViewByPredicateTraversal(predicate, null);
    }

    View findViewByPredicateTraversal(java.util.function.Predicate<View> predicate, View childToSkip) {
        if (predicate.test(this)) return this;
        return null;
    }

    public boolean requestRectangleOnScreen(Rect rectangle) { return requestRectangleOnScreen(rectangle, false); }

    public boolean requestRectangleOnScreen(Rect rectangle, boolean immediate) {
        if (mParent == null) return false;
        View child = this;
        RectF position = new RectF();
        position.set(rectangle);
        ViewParent parent = mParent;
        boolean scrolled = false;
        while (parent != null) {
            rectangle.set((int) position.left, (int) position.top, (int) position.right, (int) position.bottom);
            scrolled |= parent.requestChildRectangleOnScreen(child, rectangle, immediate);
            if (!(parent instanceof View)) break;
            position.offset(child.mLeft - child.getScrollX(), child.mTop - child.getScrollY());
            child = (View) parent;
            parent = child.getParent();
        }
        return scrolled;
    }

    // ---------------------------------------------------------------- touch mode and window state

    public boolean isInTouchMode() {
        if (mAttachInfo != null) return mAttachInfo.mInTouchMode;
        return ViewRootImpl.isInTouchModeStatic();
    }

    public boolean hasWindowFocus() { return mAttachInfo != null && mAttachInfo.mHasWindowFocus; }

    public void dispatchWindowFocusChanged(boolean hasFocus) { onWindowFocusChanged(hasFocus); }

    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (!hasWindowFocus) {
            if (isPressed()) setPressed(false);
            mPrivateFlags3 &= ~PFLAG3_FINGER_DOWN;
            removeLongPressCallback();
            removeTapCallback();
            onFocusLost();
        }
        refreshDrawableState();
    }

    public void dispatchWindowVisibilityChanged(int visibility) { onWindowVisibilityChanged(visibility); }

    protected void onWindowVisibilityChanged(int visibility) {
        if (visibility == VISIBLE) initialAwakenScrollBars();
    }

    private boolean initialAwakenScrollBars() {
        return mScrollCache != null && awakenScrollBars(mScrollCache.scrollBarDefaultDelayBeforeFade * 4, true);
    }

    public int getWindowVisibility() { return mAttachInfo != null ? mAttachInfo.mWindowVisibility : GONE; }

    public void getWindowVisibleDisplayFrame(Rect outRect) {
        Display d = Display.defaultDisplay();
        outRect.set(0, 0, d.getWidth(), d.getHeight());
    }

    public void dispatchConfigurationChanged(Configuration newConfig) { onConfigurationChanged(newConfig); }

    protected void onConfigurationChanged(Configuration newConfig) {}

    public void dispatchDisplayHint(int hint) { onDisplayHint(hint); }

    protected void onDisplayHint(int hint) {}

    protected void dispatchVisibilityChanged(View changedView, int visibility) {
        onVisibilityChanged(changedView, visibility);
    }

    protected void onVisibilityChanged(View changedView, int visibility) {}

    /** framework-internal (hidden in AOSP). */
    boolean dispatchVisibilityAggregated(boolean isVisible) {
        final boolean thisVisible = getVisibility() == VISIBLE;
        if (thisVisible || !isVisible) onVisibilityAggregated(isVisible);
        return thisVisible && isVisible;
    }

    public void onVisibilityAggregated(boolean isVisible) {
        if (isVisible && mAttachInfo != null) initialAwakenScrollBars();
        final Drawable bg = mBackground;
        if (bg != null && isVisible != bg.isVisible()) bg.setVisible(isVisible, false);
        final Drawable fg = mForeground;
        if (fg != null && isVisible != fg.isVisible()) fg.setVisible(isVisible, false);
    }

    public boolean isShown() {
        View current = this;
        do {
            if ((current.mViewFlags & VISIBILITY_MASK) != VISIBLE) return false;
            ViewParent parent = current.mParent;
            if (parent == null) return false;
            if (!(parent instanceof View)) return true;
            current = (View) parent;
        } while (current != null);
        return false;
    }

    public void setSystemUiVisibility(int visibility) {
        if (visibility != mSystemUiVisibility) {
            mSystemUiVisibility = visibility;
            if (mParent != null && mAttachInfo != null) mParent.recomputeViewAttributes(this);
        }
    }

    public int getSystemUiVisibility() { return mSystemUiVisibility; }

    public int getWindowSystemUiVisibility() { return mAttachInfo != null ? mAttachInfo.mSystemUiVisibility : 0; }

    public void onWindowSystemUiVisibilityChanged(int visible) {}

    public void dispatchWindowSystemUiVisiblityChanged(int visible) { onWindowSystemUiVisibilityChanged(visible); }

    public void dispatchSystemUiVisibilityChanged(int visibility) {
        ListenerInfo li = mListenerInfo;
        if (li != null && li.mOnSystemUiVisibilityChangeListener != null) {
            li.mOnSystemUiVisibilityChangeListener.onSystemUiVisibilityChange(visibility & PUBLIC_STATUS_BAR_VISIBILITY_MASK);
        }
    }

    private static final int PUBLIC_STATUS_BAR_VISIBILITY_MASK = 0x00003FF7;

    public void onScreenStateChanged(int screenState) {}

    public void onMovedToDisplay(int displayId, Configuration config) {}

    // ---------------------------------------------------------------- window insets

    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (getFitsSystemWindows()) {
            Rect r = new Rect(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            if (fitSystemWindows(r)) return insets.consumeSystemWindowInsets();
        }
        return insets;
    }

    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        ListenerInfo li = mListenerInfo;
        if (li != null && li.mOnApplyWindowInsetsListener != null) {
            return li.mOnApplyWindowInsetsListener.onApplyWindowInsets(this, insets);
        }
        return onApplyWindowInsets(insets);
    }

    @Deprecated
    protected boolean fitSystemWindows(Rect insets) {
        if ((mViewFlags & FITS_SYSTEM_WINDOWS) == FITS_SYSTEM_WINDOWS) {
            internalSetPadding(insets.left, insets.top, insets.right, insets.bottom);
            return true;
        }
        return false;
    }

    public void requestApplyInsets() { requestFitSystemWindows(); }

    @Deprecated
    public void requestFitSystemWindows() {
        if (mParent != null) mParent.requestFitSystemWindows();
    }

    public WindowInsets getRootWindowInsets() {
        return mAttachInfo != null ? mAttachInfo.mViewRootImpl.getWindowInsets() : null;
    }

    public WindowInsets computeSystemWindowInsets(WindowInsets in, Rect outLocalInsets) {
        outLocalInsets.set(in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(),
                in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom());
        return in.consumeSystemWindowInsets();
    }

    public void setSystemGestureExclusionRects(List<Rect> rects) { mSystemGestureExclusionRects = rects; }

    public List<Rect> getSystemGestureExclusionRects() {
        return mSystemGestureExclusionRects != null ? mSystemGestureExclusionRects : Collections.<Rect>emptyList();
    }

    // ---------------------------------------------------------------- attach / detach

    public boolean isAttachedToWindow() { return mAttachInfo != null; }

    /** framework-internal (hidden in AOSP). */
    public ViewRootImpl getViewRootImpl() { return mAttachInfo != null ? mAttachInfo.mViewRootImpl : null; }

    void assignParent(ViewParent parent) {
        if (mParent == null) {
            mParent = parent;
        } else if (parent == null) {
            mParent = null;
        } else {
            throw new RuntimeException("view " + this + " being added, but it already has a parent");
        }
    }

    void dispatchAttachedToWindow(AttachInfo info, int visibility) {
        mAttachInfo = info;
        mWindowAttachCount++;
        mPrivateFlags |= PFLAG_DRAWABLE_STATE_DIRTY;
        if (mFloatingTreeObserver != null) {
            info.mTreeObserver.merge(mFloatingTreeObserver);
            mFloatingTreeObserver = null;
        }
        if ((mPrivateFlags & PFLAG_SCROLL_CONTAINER) != 0) {
            mAttachInfo.mScrollContainers.add(this);
            mPrivateFlags |= PFLAG_SCROLL_CONTAINER_ADDED;
        }
        if (mRunQueue != null) {
            mRunQueue.executeActions(info.mHandler);
            mRunQueue = null;
        }
        onAttachedToWindow();
        ListenerInfo li = mListenerInfo;
        final CopyOnWriteArrayList<OnAttachStateChangeListener> listeners =
                li != null ? li.mOnAttachStateChangeListeners : null;
        if (listeners != null && listeners.size() > 0) {
            for (OnAttachStateChangeListener listener : listeners) listener.onViewAttachedToWindow(this);
        }
        int vis = info.mWindowVisibility;
        if (vis != GONE) {
            onWindowVisibilityChanged(vis);
            if (isShown()) onVisibilityAggregated(vis == VISIBLE);
        }
        onVisibilityChanged(this, visibility);
        if ((mPrivateFlags & PFLAG_DRAWABLE_STATE_DIRTY) != 0) refreshDrawableState();
    }

    void dispatchDetachedFromWindow() {
        AttachInfo info = mAttachInfo;
        if (info != null) {
            int vis = info.mWindowVisibility;
            if (vis != GONE) {
                onWindowVisibilityChanged(GONE);
                if (isShown()) onVisibilityAggregated(false);
            }
        }
        onDetachedFromWindow();
        onDetachedFromWindowInternal();
        ListenerInfo li = mListenerInfo;
        final CopyOnWriteArrayList<OnAttachStateChangeListener> listeners =
                li != null ? li.mOnAttachStateChangeListeners : null;
        if (listeners != null && listeners.size() > 0) {
            for (OnAttachStateChangeListener listener : listeners) listener.onViewDetachedFromWindow(this);
        }
        if ((mPrivateFlags & PFLAG_SCROLL_CONTAINER_ADDED) != 0 && info != null) {
            info.mScrollContainers.remove(this);
            mPrivateFlags &= ~PFLAG_SCROLL_CONTAINER_ADDED;
        }
        mAttachInfo = null;
    }

    protected void onAttachedToWindow() {
        jumpDrawablesToCurrentState();
    }

    protected void onDetachedFromWindow() {}

    /** framework-internal (hidden in AOSP, protected). */
    protected void onDetachedFromWindowInternal() {
        mPrivateFlags &= ~PFLAG_CANCEL_NEXT_UP_EVENT;
        mPrivateFlags3 &= ~PFLAG3_IS_LAID_OUT;
        removeUnsetPressCallback();
        removeLongPressCallback();
        removePerformClickCallback();
        cancelLongPress();
        mPrivateFlags3 &= ~PFLAG3_FINGER_DOWN;
        destroyDrawingCache();
        if (mAttachInfo != null) mAttachInfo.mHandler.removeCallbacks(mScrollBarFade);
    }

    protected int getWindowAttachCount() { return mWindowAttachCount; }

    public IBinder getWindowToken() { return mAttachInfo != null ? mAttachInfo.mWindowToken : null; }

    public WindowId getWindowId() { return null; }

    public IBinder getApplicationWindowToken() {
        AttachInfo ai = mAttachInfo;
        return ai != null ? ai.mWindowToken : null;
    }

    public Display getDisplay() { return mAttachInfo != null ? mAttachInfo.mDisplay : null; }

    public final boolean isTemporarilyDetached() { return (mPrivateFlags3 & PFLAG3_TEMPORARY_DETACH) != 0; }

    public void dispatchStartTemporaryDetach() {
        mPrivateFlags3 |= PFLAG3_TEMPORARY_DETACH;
        onStartTemporaryDetach();
    }

    public void onStartTemporaryDetach() {
        removeUnsetPressCallback();
        mPrivateFlags |= PFLAG_CANCEL_NEXT_UP_EVENT;
    }

    public void dispatchFinishTemporaryDetach() {
        mPrivateFlags3 &= ~PFLAG3_TEMPORARY_DETACH;
        onFinishTemporaryDetach();
    }

    public void onFinishTemporaryDetach() {}

    public void setHasTransientState(boolean hasTransientState) {
        final boolean oldHasTransientState = hasTransientState();
        mTransientStateCount = hasTransientState ? mTransientStateCount + 1 : mTransientStateCount - 1;
        if (mTransientStateCount < 0) {
            mTransientStateCount = 0;
            Log.e(TAG, "hasTransientState decremented below 0: unmatched pair of setHasTransientState calls");
        } else if ((hasTransientState && mTransientStateCount == 1)
                || (!hasTransientState && mTransientStateCount == 0)) {
            if (mParent != null && oldHasTransientState != hasTransientState()) {
                mParent.childHasTransientStateChanged(this, hasTransientState);
            }
        }
    }

    public boolean hasTransientState() { return mTransientStateCount > 0; }

    // ---------------------------------------------------------------- tree observer, handler, posting

    public ViewTreeObserver getViewTreeObserver() {
        if (mAttachInfo != null) return mAttachInfo.mTreeObserver;
        if (mFloatingTreeObserver == null) mFloatingTreeObserver = new ViewTreeObserver(mContext);
        return mFloatingTreeObserver;
    }

    public Handler getHandler() {
        final AttachInfo attachInfo = mAttachInfo;
        if (attachInfo != null) return attachInfo.mHandler;
        return null;
    }

    private HandlerActionQueue getRunQueue() {
        if (mRunQueue == null) mRunQueue = new HandlerActionQueue();
        return mRunQueue;
    }

    public boolean post(Runnable action) {
        final AttachInfo attachInfo = mAttachInfo;
        if (attachInfo != null) return attachInfo.mHandler.post(action);
        getRunQueue().post(action);
        return true;
    }

    public boolean postDelayed(Runnable action, long delayMillis) {
        final AttachInfo attachInfo = mAttachInfo;
        if (attachInfo != null) return attachInfo.mHandler.postDelayed(action, delayMillis);
        getRunQueue().postDelayed(action, delayMillis);
        return true;
    }

    public void postOnAnimation(Runnable action) {
        final AttachInfo attachInfo = mAttachInfo;
        if (attachInfo != null) attachInfo.mViewRootImpl.postOnAnimation(action, 0);
        else getRunQueue().post(action);
    }

    public void postOnAnimationDelayed(Runnable action, long delayMillis) {
        final AttachInfo attachInfo = mAttachInfo;
        if (attachInfo != null) attachInfo.mViewRootImpl.postOnAnimation(action, delayMillis);
        else getRunQueue().postDelayed(action, delayMillis);
    }

    public boolean removeCallbacks(Runnable action) {
        if (action != null) {
            final AttachInfo attachInfo = mAttachInfo;
            if (attachInfo != null) {
                attachInfo.mHandler.removeCallbacks(action);
                attachInfo.mViewRootImpl.removeOnAnimation(action);
            }
            getRunQueue().removeCallbacks(action);
        }
        return true;
    }

    // ---------------------------------------------------------------- saved state

    public void saveHierarchyState(SparseArray<Parcelable> container) { dispatchSaveInstanceState(container); }

    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        if (mID != NO_ID && (mViewFlags & SAVE_DISABLED_MASK) == 0) {
            mPrivateFlags &= ~PFLAG_SAVE_STATE_CALLED;
            Parcelable state = onSaveInstanceState();
            if ((mPrivateFlags & PFLAG_SAVE_STATE_CALLED) == 0) {
                throw new IllegalStateException("Derived class did not call super.onSaveInstanceState()");
            }
            if (state != null) container.put(mID, state);
        }
    }

    protected Parcelable onSaveInstanceState() {
        mPrivateFlags |= PFLAG_SAVE_STATE_CALLED;
        return BaseSavedState.EMPTY_STATE;
    }

    public void restoreHierarchyState(SparseArray<Parcelable> container) { dispatchRestoreInstanceState(container); }

    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        if (mID != NO_ID) {
            Parcelable state = container.get(mID);
            if (state != null) {
                mPrivateFlags &= ~PFLAG_SAVE_STATE_CALLED;
                onRestoreInstanceState(state);
                if ((mPrivateFlags & PFLAG_SAVE_STATE_CALLED) == 0) {
                    throw new IllegalStateException("Derived class did not call super.onRestoreInstanceState()");
                }
            }
        }
    }

    protected void onRestoreInstanceState(Parcelable state) {
        mPrivateFlags |= PFLAG_SAVE_STATE_CALLED;
        if (state != null && !(state instanceof AbsSavedState)) {
            throw new IllegalArgumentException("Wrong state class, expecting View State but received "
                    + state.getClass().toString() + " instead. This usually happens "
                    + "when two views of different type have the same id in the same hierarchy. "
                    + "This view's id is " + getId() + ". Make sure other views do not use the same id.");
        }
    }

    // ---------------------------------------------------------------- hierarchy queries

    public final ViewParent getParent() { return mParent; }

    public View getRootView() {
        if (mAttachInfo != null) {
            final View v = mAttachInfo.mRootView;
            if (v != null) return v;
        }
        View parent = this;
        while (parent.mParent instanceof View) parent = (View) parent.mParent;
        return parent;
    }

    @SuppressWarnings("unchecked")
    public final <T extends View> T findViewById(int id) {
        if (id == NO_ID) return null;
        return (T) findViewTraversal(id);
    }

    public final <T extends View> T requireViewById(int id) {
        T view = findViewById(id);
        if (view == null) throw new IllegalArgumentException("ID does not reference a View inside this View");
        return view;
    }

    @SuppressWarnings("unchecked")
    public final <T extends View> T findViewWithTag(Object tag) {
        if (tag == null) return null;
        return (T) findViewWithTagTraversal(tag);
    }

    View findViewTraversal(int id) {
        if (id == mID) return this;
        return null;
    }

    View findViewWithTagTraversal(Object tag) {
        if (tag != null && tag.equals(mTag)) return this;
        return null;
    }

    public void findViewsWithText(ArrayList<View> outViews, CharSequence searched, int flags) {
        if ((flags & FIND_VIEWS_WITH_CONTENT_DESCRIPTION) != 0 && searched != null && searched.length() > 0
                && mContentDescription != null && mContentDescription.length() > 0) {
            String searchedLowerCase = searched.toString().toLowerCase();
            String contentDescriptionLowerCase = mContentDescription.toString().toLowerCase();
            if (contentDescriptionLowerCase.contains(searchedLowerCase)) outViews.add(this);
        }
    }

    public static View inflate(Context context, int resource, ViewGroup root) {
        LayoutInflater factory = LayoutInflater.from(context);
        return factory.inflate(resource, root);
    }

    public int getSourceLayoutResId() { return mSourceLayoutId; }

    public void bringToFront() {
        if (mParent != null) mParent.bringChildToFront(this);
    }

    public boolean isInEditMode() { return false; }

    public boolean isShowingLayoutBounds() { return false; }

    // ---------------------------------------------------------------- locations

    public void getLocationOnScreen(int[] outLocation) {
        getLocationInWindow(outLocation);
        final AttachInfo info = mAttachInfo;
        if (info != null) {
            outLocation[0] += info.mWindowLeft;
            outLocation[1] += info.mWindowTop;
        }
    }

    public void getLocationInWindow(int[] outLocation) {
        if (outLocation == null || outLocation.length < 2) {
            throw new IllegalArgumentException("outLocation must be an array of two integers");
        }
        outLocation[0] = 0;
        outLocation[1] = 0;
        transformFromViewToWindowSpace(outLocation);
    }

    public void getLocationInSurface(int[] location) { getLocationInWindow(location); }

    /** framework-internal (hidden in AOSP). */
    public void transformFromViewToWindowSpace(int[] inOutLocation) {
        float[] position = new float[] {inOutLocation[0], inOutLocation[1]};
        if (!hasIdentityMatrix()) getMatrix().mapPoints(position);
        position[0] += mLeft;
        position[1] += mTop;
        ViewParent viewParent = mParent;
        while (viewParent instanceof View) {
            final View view = (View) viewParent;
            position[0] -= view.mScrollX;
            position[1] -= view.mScrollY;
            if (!view.hasIdentityMatrix()) view.getMatrix().mapPoints(position);
            position[0] += view.mLeft;
            position[1] += view.mTop;
            viewParent = view.mParent;
        }
        inOutLocation[0] = Math.round(position[0]);
        inOutLocation[1] = Math.round(position[1]);
    }

    public void getHitRect(Rect outRect) {
        if (hasIdentityMatrix() || mAttachInfo == null) {
            outRect.set(mLeft, mTop, mRight, mBottom);
        } else {
            final RectF tmpRect = new RectF(0, 0, getWidth(), getHeight());
            getMatrix().mapRect(tmpRect);
            outRect.set((int) tmpRect.left + mLeft, (int) tmpRect.top + mTop, (int) tmpRect.right + mLeft,
                    (int) tmpRect.bottom + mTop);
        }
    }

    public boolean getGlobalVisibleRect(Rect r, Point globalOffset) {
        int width = mRight - mLeft;
        int height = mBottom - mTop;
        if (width > 0 && height > 0) {
            r.set(0, 0, width, height);
            if (globalOffset != null) globalOffset.set(-mScrollX, -mScrollY);
            return mParent == null || mParent.getChildVisibleRect(this, r, globalOffset);
        }
        return false;
    }

    public final boolean getGlobalVisibleRect(Rect r) { return getGlobalVisibleRect(r, null); }

    public final boolean getLocalVisibleRect(Rect r) {
        final Point offset = new Point();
        if (getGlobalVisibleRect(r, offset)) {
            r.offset(-offset.x, -offset.y);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- feedback

    public void playSoundEffect(int soundConstant) {
        if (mAttachInfo == null || !isSoundEffectsEnabled()) return;
    }

    public boolean performHapticFeedback(int feedbackConstant) { return performHapticFeedback(feedbackConstant, 0); }

    public boolean performHapticFeedback(int feedbackConstant, int flags) {
        if (feedbackConstant == HapticFeedbackConstants.NO_HAPTICS || mAttachInfo == null) return false;
        if ((flags & HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING) == 0 && !isHapticFeedbackEnabled()) return false;
        return false;
    }

    // ---------------------------------------------------------------- accessibility (no services on switchapk)

    public void sendAccessibilityEvent(int eventType) {
        if (mAccessibilityDelegate != null) mAccessibilityDelegate.sendAccessibilityEvent(this, eventType);
        else sendAccessibilityEventInternal(eventType);
    }

    void sendAccessibilityEventInternal(int eventType) {}

    public void announceForAccessibility(CharSequence text) {}

    public void sendAccessibilityEventUnchecked(AccessibilityEvent event) {
        if (mAccessibilityDelegate != null) mAccessibilityDelegate.sendAccessibilityEventUnchecked(this, event);
        else sendAccessibilityEventUncheckedInternal(event);
    }

    void sendAccessibilityEventUncheckedInternal(AccessibilityEvent event) {}

    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (mAccessibilityDelegate != null) return mAccessibilityDelegate.dispatchPopulateAccessibilityEvent(this, event);
        return dispatchPopulateAccessibilityEventInternal(event);
    }

    boolean dispatchPopulateAccessibilityEventInternal(AccessibilityEvent event) {
        onPopulateAccessibilityEvent(event);
        return false;
    }

    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (mAccessibilityDelegate != null) mAccessibilityDelegate.onPopulateAccessibilityEvent(this, event);
        else onPopulateAccessibilityEventInternal(event);
    }

    void onPopulateAccessibilityEventInternal(AccessibilityEvent event) {}

    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        if (mAccessibilityDelegate != null) mAccessibilityDelegate.onInitializeAccessibilityEvent(this, event);
        else onInitializeAccessibilityEventInternal(event);
    }

    void onInitializeAccessibilityEventInternal(AccessibilityEvent event) {
        event.setClassName(getAccessibilityClassName());
        event.setPackageName(getContext().getPackageName());
        event.setEnabled(isEnabled());
        event.setContentDescription(mContentDescription);
    }

    public AccessibilityNodeInfo createAccessibilityNodeInfo() {
        if (mAccessibilityDelegate != null) return mAccessibilityDelegate.createAccessibilityNodeInfo(this);
        return createAccessibilityNodeInfoInternal();
    }

    AccessibilityNodeInfo createAccessibilityNodeInfoInternal() {
        AccessibilityNodeProvider provider = getAccessibilityNodeProvider();
        if (provider != null) return provider.createAccessibilityNodeInfo(AccessibilityNodeProvider.HOST_VIEW_ID);
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(this);
        onInitializeAccessibilityNodeInfo(info);
        return info;
    }

    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        if (mAccessibilityDelegate != null) mAccessibilityDelegate.onInitializeAccessibilityNodeInfo(this, info);
        else onInitializeAccessibilityNodeInfoInternal(info);
    }

    void onInitializeAccessibilityNodeInfoInternal(AccessibilityNodeInfo info) {
        Rect bounds = new Rect();
        getDrawingRect(bounds);
        info.setBoundsInParent(bounds);
        info.setPackageName(mContext.getPackageName());
        info.setClassName(getAccessibilityClassName());
        info.setContentDescription(getContentDescription());
        info.setEnabled(isEnabled());
        info.setClickable(isClickable());
        info.setFocusable(isFocusable());
        info.setFocused(isFocused());
        info.setVisibleToUser(isShown());
        info.setLongClickable(isLongClickable());
        info.setSelected(isSelected());
    }

    public void addExtraDataToAccessibilityNodeInfo(AccessibilityNodeInfo info, String extraDataKey, Bundle arguments) {}

    public CharSequence getAccessibilityClassName() { return View.class.getName(); }

    public void setAccessibilityDelegate(AccessibilityDelegate delegate) { mAccessibilityDelegate = delegate; }

    public AccessibilityDelegate getAccessibilityDelegate() { return mAccessibilityDelegate; }

    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        if (mAccessibilityDelegate != null) return mAccessibilityDelegate.getAccessibilityNodeProvider(this);
        return null;
    }

    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (mAccessibilityDelegate != null) return mAccessibilityDelegate.performAccessibilityAction(this, action, arguments);
        return performAccessibilityActionInternal(action, arguments);
    }

    boolean performAccessibilityActionInternal(int action, Bundle arguments) {
        switch (action) {
            case AccessibilityNodeInfo.ACTION_CLICK:
                if (isClickable()) {
                    performClickInternal();
                    return true;
                }
                break;
            case AccessibilityNodeInfo.ACTION_LONG_CLICK:
                if (isLongClickable()) return performLongClick();
                break;
            case AccessibilityNodeInfo.ACTION_FOCUS:
                if (!hasFocus()) return requestFocus();
                break;
            case AccessibilityNodeInfo.ACTION_CLEAR_FOCUS:
                if (hasFocus()) {
                    clearFocus();
                    return !isFocused();
                }
                break;
        }
        return false;
    }

    public CharSequence getContentDescription() { return mContentDescription; }

    public void setContentDescription(CharSequence contentDescription) { mContentDescription = contentDescription; }

    public CharSequence getTooltipText() { return mTooltipText; }

    public void setTooltipText(CharSequence tooltipText) { mTooltipText = tooltipText; }

    public final CharSequence getStateDescription() { return mStateDescription; }

    public void setStateDescription(CharSequence stateDescription) { mStateDescription = stateDescription; }

    public void setAccessibilityPaneTitle(CharSequence accessibilityPaneTitle) { mAccessibilityPaneTitle = accessibilityPaneTitle; }

    public CharSequence getAccessibilityPaneTitle() { return mAccessibilityPaneTitle; }

    public void setAccessibilityTraversalBefore(int beforeId) { mAccessibilityTraversalBeforeId = beforeId; }

    public int getAccessibilityTraversalBefore() { return mAccessibilityTraversalBeforeId; }

    public void setAccessibilityTraversalAfter(int afterId) { mAccessibilityTraversalAfterId = afterId; }

    public int getAccessibilityTraversalAfter() { return mAccessibilityTraversalAfterId; }

    public int getLabelFor() { return mLabelForId; }

    public void setLabelFor(int id) { mLabelForId = id; }

    public void setAccessibilityLiveRegion(int mode) { mAccessibilityLiveRegion = mode; }

    public int getAccessibilityLiveRegion() { return mAccessibilityLiveRegion; }

    public int getImportantForAccessibility() { return mImportantForAccessibility; }

    public void setImportantForAccessibility(int mode) { mImportantForAccessibility = mode; }

    public boolean isImportantForAccessibility() {
        return mImportantForAccessibility != IMPORTANT_FOR_ACCESSIBILITY_NO
                && mImportantForAccessibility != IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;
    }

    public ViewParent getParentForAccessibility() {
        if (mParent instanceof View) {
            View parentView = (View) mParent;
            if (parentView.isImportantForAccessibility()) return mParent;
            return mParent.getParentForAccessibility();
        }
        return null;
    }

    public void addChildrenForAccessibility(ArrayList<View> outChildren) {}

    public boolean isAccessibilityFocused() { return false; }

    public boolean isAccessibilityHeading() { return false; }

    public void setAccessibilityHeading(boolean isHeading) {}

    public boolean isScreenReaderFocusable() { return false; }

    public void setScreenReaderFocusable(boolean screenReaderFocusable) {}

    public boolean isAccessibilityDataSensitive() { return false; }

    public void setAccessibilityDataSensitive(int accessibilityDataSensitive) {}

    public boolean dispatchNestedPrePerformAccessibilityAction(int action, Bundle arguments) { return false; }

    /** framework-internal (hidden in AOSP). */
    public void notifyViewAccessibilityStateChangedIfNeeded(int changeType) {}

    /** framework-internal (hidden in AOSP). */
    public void notifySubtreeAccessibilityStateChangedIfNeeded() {}

    // ---------------------------------------------------------------- autofill / content capture (unsupported)

    public int getAutofillType() { return AUTOFILL_TYPE_NONE; }

    public String[] getAutofillHints() { return mAutofillHints; }

    public void setAutofillHints(String... autofillHints) { mAutofillHints = autofillHints; }

    public int getImportantForAutofill() { return mImportantForAutofill; }

    public void setImportantForAutofill(int mode) { mImportantForAutofill = mode; }

    public final boolean isImportantForAutofill() { return false; }

    public int getImportantForContentCapture() { return IMPORTANT_FOR_CONTENT_CAPTURE_NO; }

    public void setImportantForContentCapture(int mode) {}

    public final boolean isImportantForContentCapture() { return false; }

    public boolean isVisibleToUserForAutofill(int virtualId) { return false; }

    // ---------------------------------------------------------------- nested scrolling

    private ViewParent mNestedScrollingParent;
    private int[] mTempNestedScrollConsumed;

    public void setNestedScrollingEnabled(boolean enabled) {
        if (enabled) mPrivateFlags3 |= PFLAG3_NESTED_SCROLLING_ENABLED;
        else {
            stopNestedScroll();
            mPrivateFlags3 &= ~PFLAG3_NESTED_SCROLLING_ENABLED;
        }
    }

    public boolean isNestedScrollingEnabled() {
        return (mPrivateFlags3 & PFLAG3_NESTED_SCROLLING_ENABLED) == PFLAG3_NESTED_SCROLLING_ENABLED;
    }

    public boolean startNestedScroll(int axes) {
        if (hasNestedScrollingParent()) return true;
        if (isNestedScrollingEnabled()) {
            ViewParent p = getParent();
            View child = this;
            while (p != null) {
                try {
                    if (p.onStartNestedScroll(child, this, axes)) {
                        mNestedScrollingParent = p;
                        p.onNestedScrollAccepted(child, this, axes);
                        return true;
                    }
                } catch (AbstractMethodError e) {
                    Log.e(VIEW_LOG_TAG, "ViewParent " + p + " does not implement interface method onStartNestedScroll", e);
                }
                if (p instanceof View) child = (View) p;
                p = p.getParent();
            }
        }
        return false;
    }

    public void stopNestedScroll() {
        if (mNestedScrollingParent != null) {
            mNestedScrollingParent.onStopNestedScroll(this);
            mNestedScrollingParent = null;
        }
    }

    public boolean hasNestedScrollingParent() { return mNestedScrollingParent != null; }

    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed,
            int[] offsetInWindow) {
        if (isNestedScrollingEnabled() && mNestedScrollingParent != null) {
            if (dxConsumed != 0 || dyConsumed != 0 || dxUnconsumed != 0 || dyUnconsumed != 0) {
                int startX = 0;
                int startY = 0;
                if (offsetInWindow != null) {
                    getLocationInWindow(offsetInWindow);
                    startX = offsetInWindow[0];
                    startY = offsetInWindow[1];
                }
                mNestedScrollingParent.onNestedScroll(this, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed);
                if (offsetInWindow != null) {
                    getLocationInWindow(offsetInWindow);
                    offsetInWindow[0] -= startX;
                    offsetInWindow[1] -= startY;
                }
                return true;
            } else if (offsetInWindow != null) {
                offsetInWindow[0] = 0;
                offsetInWindow[1] = 0;
            }
        }
        return false;
    }

    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
        if (isNestedScrollingEnabled() && mNestedScrollingParent != null) {
            if (dx != 0 || dy != 0) {
                int startX = 0;
                int startY = 0;
                if (offsetInWindow != null) {
                    getLocationInWindow(offsetInWindow);
                    startX = offsetInWindow[0];
                    startY = offsetInWindow[1];
                }
                if (consumed == null) {
                    if (mTempNestedScrollConsumed == null) mTempNestedScrollConsumed = new int[2];
                    consumed = mTempNestedScrollConsumed;
                }
                consumed[0] = 0;
                consumed[1] = 0;
                mNestedScrollingParent.onNestedPreScroll(this, dx, dy, consumed);
                if (offsetInWindow != null) {
                    getLocationInWindow(offsetInWindow);
                    offsetInWindow[0] -= startX;
                    offsetInWindow[1] -= startY;
                }
                return consumed[0] != 0 || consumed[1] != 0;
            } else if (offsetInWindow != null) {
                offsetInWindow[0] = 0;
                offsetInWindow[1] = 0;
            }
        }
        return false;
    }

    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        if (isNestedScrollingEnabled() && mNestedScrollingParent != null) {
            return mNestedScrollingParent.onNestedFling(this, velocityX, velocityY, consumed);
        }
        return false;
    }

    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        if (isNestedScrollingEnabled() && mNestedScrollingParent != null) {
            return mNestedScrollingParent.onNestedPreFling(this, velocityX, velocityY);
        }
        return false;
    }

    // ---------------------------------------------------------------- overlay, transitions, misc

    ViewOverlay mOverlay;

    public ViewOverlay getOverlay() {
        if (mOverlay == null) mOverlay = new ViewOverlay(mContext, this);
        return mOverlay;
    }

    public final void setTransitionName(String transitionName) { mTransitionName = transitionName; }

    public String getTransitionName() { return mTransitionName; }

    public void setRequestedFrameRate(float frameRate) { mRequestedFrameRate = frameRate; }

    public float getRequestedFrameRate() { return mRequestedFrameRate; }

    public long getUniqueDrawingId() { return System.identityHashCode(this); }

    public int getExplicitStyle() { return 0; }

    public int[] getAttributeResolutionStack(int attribute) { return new int[0]; }

    public java.util.Map<Integer, Integer> getAttributeSourceResourceMap() {
        return new HashMap<Integer, Integer>();
    }

    public final void saveAttributeDataForStyleable(Context context, int[] styleable, AttributeSet attrs,
            TypedArray t, int defStyleAttr, int defStyleRes) {}

    public void onProvideStructure(ViewStructure structure) {}

    public void onProvideAutofillStructure(ViewStructure structure, int flags) {}

    public void onProvideVirtualStructure(ViewStructure structure) {}

    public void onProvideAutofillVirtualStructure(ViewStructure structure, int flags) {}

    public void onProvideContentCaptureStructure(ViewStructure structure, int flags) {}

    public void dispatchProvideStructure(ViewStructure structure) {}

    public void dispatchProvideAutofillStructure(ViewStructure structure, int flags) {}

    public void setPreferKeepClear(boolean preferKeepClear) {}

    public final boolean isPreferKeepClear() { return false; }

    public final void setPreferKeepClearRects(List<Rect> rects) {}

    public final List<Rect> getPreferKeepClearRects() { return Collections.<Rect>emptyList(); }

    public boolean isAutoHandwritingEnabled() { return false; }

    public void setAutoHandwritingEnabled(boolean enabled) {}

    public final int getContentSensitivity() { return CONTENT_SENSITIVITY_AUTO; }

    public final void setContentSensitivity(int mode) {}

    public final boolean isContentSensitive() { return false; }

    public boolean isCredential() { return false; }

    public void setIsCredential(boolean isCredential) {}

    public float getFrameContentVelocity() { return 0; }

    public void setFrameContentVelocity(float pixelsPerSecond) {}

    public int getScrollCaptureHint() { return SCROLL_CAPTURE_HINT_AUTO; }

    public void setScrollCaptureHint(int hint) {}

    // ---------------------------------------------------------------- state set constants
}
