package android.view;

import android.graphics.PixelFormat;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

public interface WindowManager extends ViewManager {
    Display getDefaultDisplay();
    void removeViewImmediate(View view);

    class LayoutParams extends ViewGroup.LayoutParams implements Parcelable {
        public static final int TYPE_BASE_APPLICATION = 1;
        public static final int TYPE_APPLICATION = 2;
        public static final int TYPE_APPLICATION_STARTING = 3;
        public static final int TYPE_DRAWN_APPLICATION = 4;
        public static final int TYPE_APPLICATION_PANEL = 1000;
        public static final int TYPE_APPLICATION_MEDIA = 1001;
        public static final int TYPE_APPLICATION_SUB_PANEL = 1002;
        public static final int TYPE_APPLICATION_ATTACHED_DIALOG = 1003;
        public static final int TYPE_STATUS_BAR = 2000;
        public static final int TYPE_SEARCH_BAR = 2001;
        public static final int TYPE_PHONE = 2002;
        public static final int TYPE_SYSTEM_ALERT = 2003;
        public static final int TYPE_TOAST = 2005;
        public static final int TYPE_SYSTEM_OVERLAY = 2006;
        public static final int TYPE_PRIORITY_PHONE = 2007;
        public static final int TYPE_SYSTEM_DIALOG = 2008;
        public static final int TYPE_KEYGUARD_DIALOG = 2009;
        public static final int TYPE_SYSTEM_ERROR = 2010;
        public static final int TYPE_INPUT_METHOD = 2011;
        public static final int TYPE_INPUT_METHOD_DIALOG = 2012;
        public static final int TYPE_WALLPAPER = 2013;
        public static final int TYPE_PRIVATE_PRESENTATION = 2030;
        public static final int TYPE_ACCESSIBILITY_OVERLAY = 2032;
        public static final int TYPE_APPLICATION_OVERLAY = 2038;
        public static final int FIRST_APPLICATION_WINDOW = 1;
        public static final int LAST_APPLICATION_WINDOW = 99;
        public static final int FIRST_SUB_WINDOW = 1000;
        public static final int LAST_SUB_WINDOW = 1999;
        public static final int FIRST_SYSTEM_WINDOW = 2000;
        public static final int LAST_SYSTEM_WINDOW = 2999;

        public static final int FLAG_ALLOW_LOCK_WHILE_SCREEN_ON = 1;
        public static final int FLAG_DIM_BEHIND = 2;
        public static final int FLAG_BLUR_BEHIND = 4;
        public static final int FLAG_NOT_FOCUSABLE = 8;
        public static final int FLAG_NOT_TOUCHABLE = 16;
        public static final int FLAG_NOT_TOUCH_MODAL = 32;
        public static final int FLAG_TOUCHABLE_WHEN_WAKING = 64;
        public static final int FLAG_KEEP_SCREEN_ON = 128;
        public static final int FLAG_LAYOUT_IN_SCREEN = 256;
        public static final int FLAG_LAYOUT_NO_LIMITS = 512;
        public static final int FLAG_FULLSCREEN = 1024;
        public static final int FLAG_FORCE_NOT_FULLSCREEN = 2048;
        public static final int FLAG_DITHER = 4096;
        public static final int FLAG_SECURE = 8192;
        public static final int FLAG_SCALED = 16384;
        public static final int FLAG_IGNORE_CHEEK_PRESSES = 32768;
        public static final int FLAG_LAYOUT_INSET_DECOR = 65536;
        public static final int FLAG_ALT_FOCUSABLE_IM = 131072;
        public static final int FLAG_WATCH_OUTSIDE_TOUCH = 262144;
        public static final int FLAG_SHOW_WHEN_LOCKED = 524288;
        public static final int FLAG_SHOW_WALLPAPER = 1048576;
        public static final int FLAG_TURN_SCREEN_ON = 2097152;
        public static final int FLAG_DISMISS_KEYGUARD = 4194304;
        public static final int FLAG_SPLIT_TOUCH = 8388608;
        public static final int FLAG_HARDWARE_ACCELERATED = 16777216;
        public static final int FLAG_LAYOUT_IN_OVERSCAN = 33554432;
        public static final int FLAG_TRANSLUCENT_STATUS = 67108864;
        public static final int FLAG_TRANSLUCENT_NAVIGATION = 134217728;
        public static final int FLAG_LOCAL_FOCUS_MODE = 268435456;
        public static final int FLAG_LAYOUT_ATTACHED_IN_DECOR = 1073741824;
        public static final int FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS = 0x80000000;

        public static final int SOFT_INPUT_STATE_UNSPECIFIED = 0;
        public static final int SOFT_INPUT_STATE_UNCHANGED = 1;
        public static final int SOFT_INPUT_STATE_HIDDEN = 2;
        public static final int SOFT_INPUT_STATE_ALWAYS_HIDDEN = 3;
        public static final int SOFT_INPUT_STATE_VISIBLE = 4;
        public static final int SOFT_INPUT_STATE_ALWAYS_VISIBLE = 5;
        public static final int SOFT_INPUT_ADJUST_UNSPECIFIED = 0;
        public static final int SOFT_INPUT_ADJUST_RESIZE = 16;
        public static final int SOFT_INPUT_ADJUST_PAN = 32;
        public static final int SOFT_INPUT_ADJUST_NOTHING = 48;
        public static final int SOFT_INPUT_IS_FORWARD_NAVIGATION = 256;
        public static final int SOFT_INPUT_MASK_STATE = 15;
        public static final int SOFT_INPUT_MASK_ADJUST = 240;

        public static final float BRIGHTNESS_OVERRIDE_NONE = -1.0f;
        public static final float BRIGHTNESS_OVERRIDE_OFF = 0.0f;
        public static final float BRIGHTNESS_OVERRIDE_FULL = 1.0f;

        public int x;
        public int y;
        public int type = TYPE_APPLICATION;
        public int flags;
        public int gravity;
        public int format = PixelFormat.OPAQUE;
        public int windowAnimations;
        public float alpha = 1.0f;
        public float dimAmount = 1.0f;
        public float screenBrightness = BRIGHTNESS_OVERRIDE_NONE;
        public float buttonBrightness = BRIGHTNESS_OVERRIDE_NONE;
        public IBinder token;
        public String packageName;
        public int softInputMode;
        public int screenOrientation;
        public int systemUiVisibility;
        public float horizontalMargin;
        public float verticalMargin;
        public float horizontalWeight;
        public float verticalWeight;
        public float preferredRefreshRate;
        public int layoutInDisplayCutoutMode;
        public int memoryType;
        public int rotationAnimation;
        public boolean preferMinimalPostProcessing;
        public int preferredDisplayModeId;

        public LayoutParams() {
            super(MATCH_PARENT, MATCH_PARENT);
        }

        public LayoutParams(int type) {
            this();
            this.type = type;
            format = PixelFormat.TRANSLUCENT;
        }

        public LayoutParams(int type, int flags) {
            this(type);
            this.flags = flags;
        }

        public LayoutParams(int type, int flags, int format) {
            this(type, flags);
            this.format = format;
        }

        public LayoutParams(int w, int h, int type, int flags, int format) {
            super(w, h);
            this.type = type;
            this.flags = flags;
            this.format = format;
        }

        public LayoutParams(int w, int h, int xpos, int ypos, int type, int flags, int format) {
            this(w, h, type, flags, format);
            x = xpos;
            y = ypos;
        }

        public LayoutParams(Parcel in) {
            super(MATCH_PARENT, MATCH_PARENT);
            readFromParcel(in);
        }

        /** Copies every field that differs and returns the AOSP change flags (approximated: 0 or ~0). */
        public final int copyFrom(LayoutParams o) {
            boolean changed = width != o.width || height != o.height || x != o.x || y != o.y || type != o.type
                    || flags != o.flags || gravity != o.gravity || format != o.format || alpha != o.alpha
                    || dimAmount != o.dimAmount || softInputMode != o.softInputMode;
            width = o.width;
            height = o.height;
            x = o.x;
            y = o.y;
            type = o.type;
            flags = o.flags;
            gravity = o.gravity;
            format = o.format;
            windowAnimations = o.windowAnimations;
            alpha = o.alpha;
            dimAmount = o.dimAmount;
            screenBrightness = o.screenBrightness;
            buttonBrightness = o.buttonBrightness;
            token = o.token;
            packageName = o.packageName;
            softInputMode = o.softInputMode;
            screenOrientation = o.screenOrientation;
            systemUiVisibility = o.systemUiVisibility;
            horizontalMargin = o.horizontalMargin;
            verticalMargin = o.verticalMargin;
            horizontalWeight = o.horizontalWeight;
            verticalWeight = o.verticalWeight;
            preferredRefreshRate = o.preferredRefreshRate;
            layoutInDisplayCutoutMode = o.layoutInDisplayCutoutMode;
            memoryType = o.memoryType;
            rotationAnimation = o.rotationAnimation;
            preferMinimalPostProcessing = o.preferMinimalPostProcessing;
            preferredDisplayModeId = o.preferredDisplayModeId;
            mTitle = o.mTitle;
            return changed ? ~0 : 0;
        }

        private CharSequence mTitle = "";

        public final void setTitle(CharSequence title) { mTitle = title != null ? title : ""; }

        public final CharSequence getTitle() { return mTitle != null ? mTitle : ""; }

        public int describeContents() { return 0; }

        public void writeToParcel(Parcel dest, int parcelFlags) {
            dest.writeInt(width);
            dest.writeInt(height);
            dest.writeInt(x);
            dest.writeInt(y);
            dest.writeInt(type);
            dest.writeInt(flags);
            dest.writeInt(gravity);
            dest.writeInt(format);
            dest.writeInt(windowAnimations);
            dest.writeFloat(alpha);
            dest.writeFloat(dimAmount);
            dest.writeFloat(screenBrightness);
            dest.writeFloat(buttonBrightness);
            dest.writeStrongBinder(token);
            dest.writeString(packageName);
            dest.writeInt(softInputMode);
            dest.writeInt(screenOrientation);
            dest.writeInt(systemUiVisibility);
            dest.writeFloat(horizontalMargin);
            dest.writeFloat(verticalMargin);
            dest.writeFloat(horizontalWeight);
            dest.writeFloat(verticalWeight);
            dest.writeFloat(preferredRefreshRate);
            dest.writeInt(layoutInDisplayCutoutMode);
            dest.writeInt(memoryType);
            dest.writeInt(rotationAnimation);
            dest.writeInt(preferMinimalPostProcessing ? 1 : 0);
            dest.writeInt(preferredDisplayModeId);
        }

        public void readFromParcel(Parcel in) {
            width = in.readInt();
            height = in.readInt();
            x = in.readInt();
            y = in.readInt();
            type = in.readInt();
            flags = in.readInt();
            gravity = in.readInt();
            format = in.readInt();
            windowAnimations = in.readInt();
            alpha = in.readFloat();
            dimAmount = in.readFloat();
            screenBrightness = in.readFloat();
            buttonBrightness = in.readFloat();
            token = in.readStrongBinder();
            packageName = in.readString();
            softInputMode = in.readInt();
            screenOrientation = in.readInt();
            systemUiVisibility = in.readInt();
            horizontalMargin = in.readFloat();
            verticalMargin = in.readFloat();
            horizontalWeight = in.readFloat();
            verticalWeight = in.readFloat();
            preferredRefreshRate = in.readFloat();
            layoutInDisplayCutoutMode = in.readInt();
            memoryType = in.readInt();
            rotationAnimation = in.readInt();
            preferMinimalPostProcessing = in.readInt() != 0;
            preferredDisplayModeId = in.readInt();
        }

        public static final Creator<LayoutParams> CREATOR = new Creator<LayoutParams>() {
            public LayoutParams createFromParcel(Parcel source) { return new LayoutParams(source); }
            public LayoutParams[] newArray(int size) { return new LayoutParams[size]; }
        };
    }
}
