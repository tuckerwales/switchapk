package android.view;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

/** Key event, following AOSP KeyEvent (constants, meta state helpers, dispatch and tracking). */
public class KeyEvent extends InputEvent implements Parcelable {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_MULTIPLE = 2;
    public static final int ACTION_UP = 1;
    public static final int FLAG_CANCELED = 32;
    public static final int FLAG_CANCELED_LONG_PRESS = 256;
    public static final int FLAG_EDITOR_ACTION = 16;
    public static final int FLAG_FALLBACK = 1024;
    public static final int FLAG_FROM_SYSTEM = 8;
    public static final int FLAG_KEEP_TOUCH_MODE = 4;
    public static final int FLAG_LONG_PRESS = 128;
    public static final int FLAG_SOFT_KEYBOARD = 2;
    public static final int FLAG_TRACKING = 512;
    public static final int FLAG_VIRTUAL_HARD_KEY = 64;
    public static final int FLAG_WOKE_HERE = 1;
    public static final int KEYCODE_0 = 7;
    public static final int KEYCODE_1 = 8;
    public static final int KEYCODE_11 = 227;
    public static final int KEYCODE_12 = 228;
    public static final int KEYCODE_2 = 9;
    public static final int KEYCODE_3 = 10;
    public static final int KEYCODE_3D_MODE = 206;
    public static final int KEYCODE_4 = 11;
    public static final int KEYCODE_5 = 12;
    public static final int KEYCODE_6 = 13;
    public static final int KEYCODE_7 = 14;
    public static final int KEYCODE_8 = 15;
    public static final int KEYCODE_9 = 16;
    public static final int KEYCODE_A = 29;
    public static final int KEYCODE_ALL_APPS = 284;
    public static final int KEYCODE_ALT_LEFT = 57;
    public static final int KEYCODE_ALT_RIGHT = 58;
    public static final int KEYCODE_APOSTROPHE = 75;
    public static final int KEYCODE_APP_SWITCH = 187;
    public static final int KEYCODE_ASSIST = 219;
    public static final int KEYCODE_AT = 77;
    public static final int KEYCODE_AVR_INPUT = 182;
    public static final int KEYCODE_AVR_POWER = 181;
    public static final int KEYCODE_B = 30;
    public static final int KEYCODE_BACK = 4;
    public static final int KEYCODE_BACKSLASH = 73;
    public static final int KEYCODE_BOOKMARK = 174;
    public static final int KEYCODE_BREAK = 121;
    public static final int KEYCODE_BRIGHTNESS_DOWN = 220;
    public static final int KEYCODE_BRIGHTNESS_UP = 221;
    public static final int KEYCODE_BUTTON_1 = 188;
    public static final int KEYCODE_BUTTON_10 = 197;
    public static final int KEYCODE_BUTTON_11 = 198;
    public static final int KEYCODE_BUTTON_12 = 199;
    public static final int KEYCODE_BUTTON_13 = 200;
    public static final int KEYCODE_BUTTON_14 = 201;
    public static final int KEYCODE_BUTTON_15 = 202;
    public static final int KEYCODE_BUTTON_16 = 203;
    public static final int KEYCODE_BUTTON_2 = 189;
    public static final int KEYCODE_BUTTON_3 = 190;
    public static final int KEYCODE_BUTTON_4 = 191;
    public static final int KEYCODE_BUTTON_5 = 192;
    public static final int KEYCODE_BUTTON_6 = 193;
    public static final int KEYCODE_BUTTON_7 = 194;
    public static final int KEYCODE_BUTTON_8 = 195;
    public static final int KEYCODE_BUTTON_9 = 196;
    public static final int KEYCODE_BUTTON_A = 96;
    public static final int KEYCODE_BUTTON_B = 97;
    public static final int KEYCODE_BUTTON_C = 98;
    public static final int KEYCODE_BUTTON_L1 = 102;
    public static final int KEYCODE_BUTTON_L2 = 104;
    public static final int KEYCODE_BUTTON_MODE = 110;
    public static final int KEYCODE_BUTTON_R1 = 103;
    public static final int KEYCODE_BUTTON_R2 = 105;
    public static final int KEYCODE_BUTTON_SELECT = 109;
    public static final int KEYCODE_BUTTON_START = 108;
    public static final int KEYCODE_BUTTON_THUMBL = 106;
    public static final int KEYCODE_BUTTON_THUMBR = 107;
    public static final int KEYCODE_BUTTON_X = 99;
    public static final int KEYCODE_BUTTON_Y = 100;
    public static final int KEYCODE_BUTTON_Z = 101;
    public static final int KEYCODE_C = 31;
    public static final int KEYCODE_CALCULATOR = 210;
    public static final int KEYCODE_CALENDAR = 208;
    public static final int KEYCODE_CALL = 5;
    public static final int KEYCODE_CAMERA = 27;
    public static final int KEYCODE_CAPS_LOCK = 115;
    public static final int KEYCODE_CAPTIONS = 175;
    public static final int KEYCODE_CHANNEL_DOWN = 167;
    public static final int KEYCODE_CHANNEL_UP = 166;
    public static final int KEYCODE_CLEAR = 28;
    public static final int KEYCODE_COMMA = 55;
    public static final int KEYCODE_CONTACTS = 207;
    public static final int KEYCODE_COPY = 278;
    public static final int KEYCODE_CTRL_LEFT = 113;
    public static final int KEYCODE_CTRL_RIGHT = 114;
    public static final int KEYCODE_CUT = 277;
    public static final int KEYCODE_D = 32;
    public static final int KEYCODE_DEL = 67;
    public static final int KEYCODE_DEMO_APP_1 = 301;
    public static final int KEYCODE_DEMO_APP_2 = 302;
    public static final int KEYCODE_DEMO_APP_3 = 303;
    public static final int KEYCODE_DEMO_APP_4 = 304;
    public static final int KEYCODE_DPAD_CENTER = 23;
    public static final int KEYCODE_DPAD_DOWN = 20;
    public static final int KEYCODE_DPAD_DOWN_LEFT = 269;
    public static final int KEYCODE_DPAD_DOWN_RIGHT = 271;
    public static final int KEYCODE_DPAD_LEFT = 21;
    public static final int KEYCODE_DPAD_RIGHT = 22;
    public static final int KEYCODE_DPAD_UP = 19;
    public static final int KEYCODE_DPAD_UP_LEFT = 268;
    public static final int KEYCODE_DPAD_UP_RIGHT = 270;
    public static final int KEYCODE_DVR = 173;
    public static final int KEYCODE_E = 33;
    public static final int KEYCODE_EISU = 212;
    public static final int KEYCODE_EMOJI_PICKER = 317;
    public static final int KEYCODE_ENDCALL = 6;
    public static final int KEYCODE_ENTER = 66;
    public static final int KEYCODE_ENVELOPE = 65;
    public static final int KEYCODE_EQUALS = 70;
    public static final int KEYCODE_ESCAPE = 111;
    public static final int KEYCODE_EXPLORER = 64;
    public static final int KEYCODE_F = 34;
    public static final int KEYCODE_F1 = 131;
    public static final int KEYCODE_F10 = 140;
    public static final int KEYCODE_F11 = 141;
    public static final int KEYCODE_F12 = 142;
    public static final int KEYCODE_F2 = 132;
    public static final int KEYCODE_F3 = 133;
    public static final int KEYCODE_F4 = 134;
    public static final int KEYCODE_F5 = 135;
    public static final int KEYCODE_F6 = 136;
    public static final int KEYCODE_F7 = 137;
    public static final int KEYCODE_F8 = 138;
    public static final int KEYCODE_F9 = 139;
    public static final int KEYCODE_FEATURED_APP_1 = 297;
    public static final int KEYCODE_FEATURED_APP_2 = 298;
    public static final int KEYCODE_FEATURED_APP_3 = 299;
    public static final int KEYCODE_FEATURED_APP_4 = 300;
    public static final int KEYCODE_FOCUS = 80;
    public static final int KEYCODE_FORWARD = 125;
    public static final int KEYCODE_FORWARD_DEL = 112;
    public static final int KEYCODE_FUNCTION = 119;
    public static final int KEYCODE_G = 35;
    public static final int KEYCODE_GRAVE = 68;
    public static final int KEYCODE_GUIDE = 172;
    public static final int KEYCODE_H = 36;
    public static final int KEYCODE_HEADSETHOOK = 79;
    public static final int KEYCODE_HELP = 259;
    public static final int KEYCODE_HENKAN = 214;
    public static final int KEYCODE_HOME = 3;
    public static final int KEYCODE_I = 37;
    public static final int KEYCODE_INFO = 165;
    public static final int KEYCODE_INSERT = 124;
    public static final int KEYCODE_J = 38;
    public static final int KEYCODE_K = 39;
    public static final int KEYCODE_KANA = 218;
    public static final int KEYCODE_KATAKANA_HIRAGANA = 215;
    public static final int KEYCODE_KEYBOARD_BACKLIGHT_DOWN = 305;
    public static final int KEYCODE_KEYBOARD_BACKLIGHT_TOGGLE = 307;
    public static final int KEYCODE_KEYBOARD_BACKLIGHT_UP = 306;
    public static final int KEYCODE_L = 40;
    public static final int KEYCODE_LANGUAGE_SWITCH = 204;
    public static final int KEYCODE_LAST_CHANNEL = 229;
    public static final int KEYCODE_LEFT_BRACKET = 71;
    public static final int KEYCODE_M = 41;
    public static final int KEYCODE_MACRO_1 = 313;
    public static final int KEYCODE_MACRO_2 = 314;
    public static final int KEYCODE_MACRO_3 = 315;
    public static final int KEYCODE_MACRO_4 = 316;
    public static final int KEYCODE_MANNER_MODE = 205;
    public static final int KEYCODE_MEDIA_AUDIO_TRACK = 222;
    public static final int KEYCODE_MEDIA_CLOSE = 128;
    public static final int KEYCODE_MEDIA_EJECT = 129;
    public static final int KEYCODE_MEDIA_FAST_FORWARD = 90;
    public static final int KEYCODE_MEDIA_NEXT = 87;
    public static final int KEYCODE_MEDIA_PAUSE = 127;
    public static final int KEYCODE_MEDIA_PLAY = 126;
    public static final int KEYCODE_MEDIA_PLAY_PAUSE = 85;
    public static final int KEYCODE_MEDIA_PREVIOUS = 88;
    public static final int KEYCODE_MEDIA_RECORD = 130;
    public static final int KEYCODE_MEDIA_REWIND = 89;
    public static final int KEYCODE_MEDIA_SKIP_BACKWARD = 273;
    public static final int KEYCODE_MEDIA_SKIP_FORWARD = 272;
    public static final int KEYCODE_MEDIA_STEP_BACKWARD = 275;
    public static final int KEYCODE_MEDIA_STEP_FORWARD = 274;
    public static final int KEYCODE_MEDIA_STOP = 86;
    public static final int KEYCODE_MEDIA_TOP_MENU = 226;
    public static final int KEYCODE_MENU = 82;
    public static final int KEYCODE_META_LEFT = 117;
    public static final int KEYCODE_META_RIGHT = 118;
    public static final int KEYCODE_MINUS = 69;
    public static final int KEYCODE_MOVE_END = 123;
    public static final int KEYCODE_MOVE_HOME = 122;
    public static final int KEYCODE_MUHENKAN = 213;
    public static final int KEYCODE_MUSIC = 209;
    public static final int KEYCODE_MUTE = 91;
    public static final int KEYCODE_N = 42;
    public static final int KEYCODE_NAVIGATE_IN = 262;
    public static final int KEYCODE_NAVIGATE_NEXT = 261;
    public static final int KEYCODE_NAVIGATE_OUT = 263;
    public static final int KEYCODE_NAVIGATE_PREVIOUS = 260;
    public static final int KEYCODE_NOTIFICATION = 83;
    public static final int KEYCODE_NUM = 78;
    public static final int KEYCODE_NUMPAD_0 = 144;
    public static final int KEYCODE_NUMPAD_1 = 145;
    public static final int KEYCODE_NUMPAD_2 = 146;
    public static final int KEYCODE_NUMPAD_3 = 147;
    public static final int KEYCODE_NUMPAD_4 = 148;
    public static final int KEYCODE_NUMPAD_5 = 149;
    public static final int KEYCODE_NUMPAD_6 = 150;
    public static final int KEYCODE_NUMPAD_7 = 151;
    public static final int KEYCODE_NUMPAD_8 = 152;
    public static final int KEYCODE_NUMPAD_9 = 153;
    public static final int KEYCODE_NUMPAD_ADD = 157;
    public static final int KEYCODE_NUMPAD_COMMA = 159;
    public static final int KEYCODE_NUMPAD_DIVIDE = 154;
    public static final int KEYCODE_NUMPAD_DOT = 158;
    public static final int KEYCODE_NUMPAD_ENTER = 160;
    public static final int KEYCODE_NUMPAD_EQUALS = 161;
    public static final int KEYCODE_NUMPAD_LEFT_PAREN = 162;
    public static final int KEYCODE_NUMPAD_MULTIPLY = 155;
    public static final int KEYCODE_NUMPAD_RIGHT_PAREN = 163;
    public static final int KEYCODE_NUMPAD_SUBTRACT = 156;
    public static final int KEYCODE_NUM_LOCK = 143;
    public static final int KEYCODE_O = 43;
    public static final int KEYCODE_P = 44;
    public static final int KEYCODE_PAGE_DOWN = 93;
    public static final int KEYCODE_PAGE_UP = 92;
    public static final int KEYCODE_PAIRING = 225;
    public static final int KEYCODE_PASTE = 279;
    public static final int KEYCODE_PERIOD = 56;
    public static final int KEYCODE_PICTSYMBOLS = 94;
    public static final int KEYCODE_PLUS = 81;
    public static final int KEYCODE_POUND = 18;
    public static final int KEYCODE_POWER = 26;
    public static final int KEYCODE_PROFILE_SWITCH = 288;
    public static final int KEYCODE_PROG_BLUE = 186;
    public static final int KEYCODE_PROG_GREEN = 184;
    public static final int KEYCODE_PROG_RED = 183;
    public static final int KEYCODE_PROG_YELLOW = 185;
    public static final int KEYCODE_Q = 45;
    public static final int KEYCODE_R = 46;
    public static final int KEYCODE_RECENT_APPS = 312;
    public static final int KEYCODE_REFRESH = 285;
    public static final int KEYCODE_RIGHT_BRACKET = 72;
    public static final int KEYCODE_RO = 217;
    public static final int KEYCODE_S = 47;
    public static final int KEYCODE_SCREENSHOT = 318;
    public static final int KEYCODE_SCROLL_LOCK = 116;
    public static final int KEYCODE_SEARCH = 84;
    public static final int KEYCODE_SEMICOLON = 74;
    public static final int KEYCODE_SETTINGS = 176;
    public static final int KEYCODE_SHIFT_LEFT = 59;
    public static final int KEYCODE_SHIFT_RIGHT = 60;
    public static final int KEYCODE_SLASH = 76;
    public static final int KEYCODE_SLEEP = 223;
    public static final int KEYCODE_SOFT_LEFT = 1;
    public static final int KEYCODE_SOFT_RIGHT = 2;
    public static final int KEYCODE_SOFT_SLEEP = 276;
    public static final int KEYCODE_SPACE = 62;
    public static final int KEYCODE_STAR = 17;
    public static final int KEYCODE_STB_INPUT = 180;
    public static final int KEYCODE_STB_POWER = 179;
    public static final int KEYCODE_STEM_1 = 265;
    public static final int KEYCODE_STEM_2 = 266;
    public static final int KEYCODE_STEM_3 = 267;
    public static final int KEYCODE_STEM_PRIMARY = 264;
    public static final int KEYCODE_STYLUS_BUTTON_PRIMARY = 308;
    public static final int KEYCODE_STYLUS_BUTTON_SECONDARY = 309;
    public static final int KEYCODE_STYLUS_BUTTON_TAIL = 311;
    public static final int KEYCODE_STYLUS_BUTTON_TERTIARY = 310;
    public static final int KEYCODE_SWITCH_CHARSET = 95;
    public static final int KEYCODE_SYM = 63;
    public static final int KEYCODE_SYSRQ = 120;
    public static final int KEYCODE_SYSTEM_NAVIGATION_DOWN = 281;
    public static final int KEYCODE_SYSTEM_NAVIGATION_LEFT = 282;
    public static final int KEYCODE_SYSTEM_NAVIGATION_RIGHT = 283;
    public static final int KEYCODE_SYSTEM_NAVIGATION_UP = 280;
    public static final int KEYCODE_T = 48;
    public static final int KEYCODE_TAB = 61;
    public static final int KEYCODE_THUMBS_DOWN = 287;
    public static final int KEYCODE_THUMBS_UP = 286;
    public static final int KEYCODE_TV = 170;
    public static final int KEYCODE_TV_ANTENNA_CABLE = 242;
    public static final int KEYCODE_TV_AUDIO_DESCRIPTION = 252;
    public static final int KEYCODE_TV_AUDIO_DESCRIPTION_MIX_DOWN = 254;
    public static final int KEYCODE_TV_AUDIO_DESCRIPTION_MIX_UP = 253;
    public static final int KEYCODE_TV_CONTENTS_MENU = 256;
    public static final int KEYCODE_TV_DATA_SERVICE = 230;
    public static final int KEYCODE_TV_INPUT = 178;
    public static final int KEYCODE_TV_INPUT_COMPONENT_1 = 249;
    public static final int KEYCODE_TV_INPUT_COMPONENT_2 = 250;
    public static final int KEYCODE_TV_INPUT_COMPOSITE_1 = 247;
    public static final int KEYCODE_TV_INPUT_COMPOSITE_2 = 248;
    public static final int KEYCODE_TV_INPUT_HDMI_1 = 243;
    public static final int KEYCODE_TV_INPUT_HDMI_2 = 244;
    public static final int KEYCODE_TV_INPUT_HDMI_3 = 245;
    public static final int KEYCODE_TV_INPUT_HDMI_4 = 246;
    public static final int KEYCODE_TV_INPUT_VGA_1 = 251;
    public static final int KEYCODE_TV_MEDIA_CONTEXT_MENU = 257;
    public static final int KEYCODE_TV_NETWORK = 241;
    public static final int KEYCODE_TV_NUMBER_ENTRY = 234;
    public static final int KEYCODE_TV_POWER = 177;
    public static final int KEYCODE_TV_RADIO_SERVICE = 232;
    public static final int KEYCODE_TV_SATELLITE = 237;
    public static final int KEYCODE_TV_SATELLITE_BS = 238;
    public static final int KEYCODE_TV_SATELLITE_CS = 239;
    public static final int KEYCODE_TV_SATELLITE_SERVICE = 240;
    public static final int KEYCODE_TV_TELETEXT = 233;
    public static final int KEYCODE_TV_TERRESTRIAL_ANALOG = 235;
    public static final int KEYCODE_TV_TERRESTRIAL_DIGITAL = 236;
    public static final int KEYCODE_TV_TIMER_PROGRAMMING = 258;
    public static final int KEYCODE_TV_ZOOM_MODE = 255;
    public static final int KEYCODE_U = 49;
    public static final int KEYCODE_UNKNOWN = 0;
    public static final int KEYCODE_V = 50;
    public static final int KEYCODE_VIDEO_APP_1 = 289;
    public static final int KEYCODE_VIDEO_APP_2 = 290;
    public static final int KEYCODE_VIDEO_APP_3 = 291;
    public static final int KEYCODE_VIDEO_APP_4 = 292;
    public static final int KEYCODE_VIDEO_APP_5 = 293;
    public static final int KEYCODE_VIDEO_APP_6 = 294;
    public static final int KEYCODE_VIDEO_APP_7 = 295;
    public static final int KEYCODE_VIDEO_APP_8 = 296;
    public static final int KEYCODE_VOICE_ASSIST = 231;
    public static final int KEYCODE_VOLUME_DOWN = 25;
    public static final int KEYCODE_VOLUME_MUTE = 164;
    public static final int KEYCODE_VOLUME_UP = 24;
    public static final int KEYCODE_W = 51;
    public static final int KEYCODE_WAKEUP = 224;
    public static final int KEYCODE_WINDOW = 171;
    public static final int KEYCODE_X = 52;
    public static final int KEYCODE_Y = 53;
    public static final int KEYCODE_YEN = 216;
    public static final int KEYCODE_Z = 54;
    public static final int KEYCODE_ZENKAKU_HANKAKU = 211;
    public static final int KEYCODE_ZOOM_IN = 168;
    public static final int KEYCODE_ZOOM_OUT = 169;
    public static final int MAX_KEYCODE = 84;
    public static final int META_ALT_LEFT_ON = 16;
    public static final int META_ALT_MASK = 50;
    public static final int META_ALT_ON = 2;
    public static final int META_ALT_RIGHT_ON = 32;
    public static final int META_CAPS_LOCK_ON = 1048576;
    public static final int META_CTRL_LEFT_ON = 8192;
    public static final int META_CTRL_MASK = 28672;
    public static final int META_CTRL_ON = 4096;
    public static final int META_CTRL_RIGHT_ON = 16384;
    public static final int META_FUNCTION_ON = 8;
    public static final int META_META_LEFT_ON = 131072;
    public static final int META_META_MASK = 458752;
    public static final int META_META_ON = 65536;
    public static final int META_META_RIGHT_ON = 262144;
    public static final int META_NUM_LOCK_ON = 2097152;
    public static final int META_SCROLL_LOCK_ON = 4194304;
    public static final int META_SHIFT_LEFT_ON = 64;
    public static final int META_SHIFT_MASK = 193;
    public static final int META_SHIFT_ON = 1;
    public static final int META_SHIFT_RIGHT_ON = 128;
    public static final int META_SYM_ON = 4;

    static final int META_MODIFIER_MASK = META_SHIFT_ON | META_SHIFT_LEFT_ON | META_SHIFT_RIGHT_ON | META_ALT_ON
            | META_ALT_LEFT_ON | META_ALT_RIGHT_ON | META_CTRL_ON | META_CTRL_LEFT_ON | META_CTRL_RIGHT_ON
            | META_META_ON | META_META_LEFT_ON | META_META_RIGHT_ON | META_SYM_ON | META_FUNCTION_ON;
    static final int META_LOCK_MASK = META_CAPS_LOCK_ON | META_NUM_LOCK_ON | META_SCROLL_LOCK_ON;
    static final int META_ALL_MASK = META_MODIFIER_MASK | META_LOCK_MASK;

    private int mDeviceId;
    private int mSource;
    private int mDisplayId;
    private int mMetaState;
    private int mAction;
    private int mKeyCode;
    private int mScanCode;
    private int mRepeatCount;
    private int mFlags;
    private long mDownTime;
    private long mEventTime;
    private String mCharacters;

    public interface Callback {
        boolean onKeyDown(int keyCode, KeyEvent event);
        boolean onKeyLongPress(int keyCode, KeyEvent event);
        boolean onKeyUp(int keyCode, KeyEvent event);
        boolean onKeyMultiple(int keyCode, int count, KeyEvent event);
    }

    public KeyEvent(int action, int code) {
        mAction = action;
        mKeyCode = code;
        mRepeatCount = 0;
        mDeviceId = KeyCharacterMap.VIRTUAL_KEYBOARD;
    }

    public KeyEvent(long downTime, long eventTime, int action, int code, int repeat) {
        this(downTime, eventTime, action, code, repeat, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, 0);
    }

    public KeyEvent(long downTime, long eventTime, int action, int code, int repeat, int metaState) {
        this(downTime, eventTime, action, code, repeat, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, 0);
    }

    public KeyEvent(long downTime, long eventTime, int action, int code, int repeat, int metaState, int deviceId,
            int scancode) {
        this(downTime, eventTime, action, code, repeat, metaState, deviceId, scancode, 0, 0);
    }

    public KeyEvent(long downTime, long eventTime, int action, int code, int repeat, int metaState, int deviceId,
            int scancode, int flags) {
        this(downTime, eventTime, action, code, repeat, metaState, deviceId, scancode, flags, 0);
    }

    public KeyEvent(long downTime, long eventTime, int action, int code, int repeat, int metaState, int deviceId,
            int scancode, int flags, int source) {
        mDownTime = downTime;
        mEventTime = eventTime;
        mAction = action;
        mKeyCode = code;
        mRepeatCount = repeat;
        mMetaState = metaState;
        mDeviceId = deviceId;
        mScanCode = scancode;
        mFlags = flags;
        mSource = source;
    }

    public KeyEvent(long time, String characters, int deviceId, int flags) {
        mDownTime = time;
        mEventTime = time;
        mCharacters = characters;
        mAction = ACTION_MULTIPLE;
        mKeyCode = KEYCODE_UNKNOWN;
        mRepeatCount = 0;
        mDeviceId = deviceId;
        mFlags = flags;
        mSource = InputDevice.SOURCE_KEYBOARD;
    }

    public KeyEvent(KeyEvent origEvent) {
        mDownTime = origEvent.mDownTime;
        mEventTime = origEvent.mEventTime;
        mAction = origEvent.mAction;
        mKeyCode = origEvent.mKeyCode;
        mRepeatCount = origEvent.mRepeatCount;
        mMetaState = origEvent.mMetaState;
        mDeviceId = origEvent.mDeviceId;
        mSource = origEvent.mSource;
        mDisplayId = origEvent.mDisplayId;
        mScanCode = origEvent.mScanCode;
        mFlags = origEvent.mFlags;
        mCharacters = origEvent.mCharacters;
    }

    @Deprecated
    public KeyEvent(KeyEvent origEvent, long eventTime, int newRepeat) {
        this(origEvent);
        mEventTime = eventTime;
        mRepeatCount = newRepeat;
    }

    private KeyEvent(KeyEvent origEvent, int action) {
        this(origEvent);
        mAction = action;
        mCharacters = null;
    }

    private KeyEvent(Parcel in) {
        mDeviceId = in.readInt();
        mSource = in.readInt();
        mDisplayId = in.readInt();
        mAction = in.readInt();
        mKeyCode = in.readInt();
        mRepeatCount = in.readInt();
        mMetaState = in.readInt();
        mScanCode = in.readInt();
        mFlags = in.readInt();
        mDownTime = in.readLong();
        mEventTime = in.readLong();
        mCharacters = in.readString();
    }

    /** framework-internal (hidden in AOSP). */
    public static KeyEvent obtain(long downTime, long eventTime, int action, int code, int repeat, int metaState,
            int deviceId, int scancode, int flags, int source, String characters) {
        KeyEvent ev = new KeyEvent(downTime, eventTime, action, code, repeat, metaState, deviceId, scancode, flags,
                source);
        ev.mCharacters = characters;
        return ev;
    }

    /** framework-internal (hidden in AOSP). */
    public static KeyEvent obtain(KeyEvent other) { return new KeyEvent(other); }

    /** framework-internal (hidden in AOSP). */
    public KeyEvent copy() { return new KeyEvent(this); }

    public static int getMaxKeyCode() { return LAST_KEYCODE; }

    public static int getDeadChar(int accent, int c) { return KeyCharacterMap.getDeadChar(accent, c); }

    public static KeyEvent changeTimeRepeat(KeyEvent event, long eventTime, int newRepeat) {
        return new KeyEvent(event, eventTime, newRepeat);
    }

    public static KeyEvent changeTimeRepeat(KeyEvent event, long eventTime, int newRepeat, int newFlags) {
        KeyEvent ret = new KeyEvent(event);
        ret.mEventTime = eventTime;
        ret.mRepeatCount = newRepeat;
        ret.mFlags = newFlags;
        return ret;
    }

    public static KeyEvent changeAction(KeyEvent event, int action) { return new KeyEvent(event, action); }

    public static KeyEvent changeFlags(KeyEvent event, int flags) {
        event = new KeyEvent(event);
        event.mFlags = flags;
        return event;
    }

    /** framework-internal (hidden in AOSP). */
    public final boolean isTainted() { return false; }

    /** framework-internal (hidden in AOSP). */
    public final void setTainted(boolean tainted) {}

    public final boolean isSystem() { return isSystemKey(mKeyCode); }

    /** framework-internal (hidden in AOSP). */
    public final boolean isWakeKey() { return isWakeKey(mKeyCode); }

    public static final boolean isGamepadButton(int keyCode) {
        switch (keyCode) {
            case KEYCODE_BUTTON_A: case KEYCODE_BUTTON_B: case KEYCODE_BUTTON_C:
            case KEYCODE_BUTTON_X: case KEYCODE_BUTTON_Y: case KEYCODE_BUTTON_Z:
            case KEYCODE_BUTTON_L1: case KEYCODE_BUTTON_R1: case KEYCODE_BUTTON_L2: case KEYCODE_BUTTON_R2:
            case KEYCODE_BUTTON_THUMBL: case KEYCODE_BUTTON_THUMBR:
            case KEYCODE_BUTTON_START: case KEYCODE_BUTTON_SELECT: case KEYCODE_BUTTON_MODE:
            case KEYCODE_BUTTON_1: case KEYCODE_BUTTON_2: case KEYCODE_BUTTON_3: case KEYCODE_BUTTON_4:
            case KEYCODE_BUTTON_5: case KEYCODE_BUTTON_6: case KEYCODE_BUTTON_7: case KEYCODE_BUTTON_8:
            case KEYCODE_BUTTON_9: case KEYCODE_BUTTON_10: case KEYCODE_BUTTON_11: case KEYCODE_BUTTON_12:
            case KEYCODE_BUTTON_13: case KEYCODE_BUTTON_14: case KEYCODE_BUTTON_15: case KEYCODE_BUTTON_16:
                return true;
            default:
                return false;
        }
    }

    /** framework-internal (hidden in AOSP). */
    public static final boolean isConfirmKey(int keyCode) {
        switch (keyCode) {
            case KEYCODE_DPAD_CENTER: case KEYCODE_ENTER: case KEYCODE_SPACE: case KEYCODE_NUMPAD_ENTER:
                return true;
            default:
                return false;
        }
    }

    public static final boolean isMediaSessionKey(int keyCode) {
        switch (keyCode) {
            case KEYCODE_MEDIA_PLAY: case KEYCODE_MEDIA_PAUSE: case KEYCODE_MEDIA_PLAY_PAUSE:
            case KEYCODE_HEADSETHOOK: case KEYCODE_MEDIA_STOP: case KEYCODE_MEDIA_NEXT:
            case KEYCODE_MEDIA_PREVIOUS: case KEYCODE_MEDIA_REWIND: case KEYCODE_MEDIA_RECORD:
            case KEYCODE_MEDIA_FAST_FORWARD:
                return true;
            default:
                return false;
        }
    }

    /** framework-internal (hidden in AOSP). */
    public static final boolean isSystemKey(int keyCode) {
        switch (keyCode) {
            case KEYCODE_MENU: case KEYCODE_SOFT_RIGHT: case KEYCODE_HOME: case KEYCODE_BACK:
            case KEYCODE_CALL: case KEYCODE_ENDCALL: case KEYCODE_VOLUME_UP: case KEYCODE_VOLUME_DOWN:
            case KEYCODE_VOLUME_MUTE: case KEYCODE_MUTE: case KEYCODE_POWER: case KEYCODE_HEADSETHOOK:
            case KEYCODE_MEDIA_PLAY: case KEYCODE_MEDIA_PAUSE: case KEYCODE_MEDIA_PLAY_PAUSE:
            case KEYCODE_MEDIA_STOP: case KEYCODE_MEDIA_NEXT: case KEYCODE_MEDIA_PREVIOUS:
            case KEYCODE_MEDIA_REWIND: case KEYCODE_MEDIA_RECORD: case KEYCODE_MEDIA_FAST_FORWARD:
            case KEYCODE_CAMERA: case KEYCODE_FOCUS: case KEYCODE_SEARCH: case KEYCODE_BRIGHTNESS_DOWN:
            case KEYCODE_BRIGHTNESS_UP: case KEYCODE_MEDIA_AUDIO_TRACK:
                return true;
            default:
                return false;
        }
    }

    /** framework-internal (hidden in AOSP). */
    public static final boolean isWakeKey(int keyCode) {
        switch (keyCode) {
            case KEYCODE_CAMERA: case KEYCODE_MENU: case KEYCODE_PAIRING: case KEYCODE_STEM_1:
            case KEYCODE_STEM_2: case KEYCODE_STEM_3: case KEYCODE_WAKEUP:
                return true;
            default:
                return false;
        }
    }

    @Override
    public final int getDeviceId() { return mDeviceId; }

    @Override
    public final int getSource() { return mSource; }

    @Override
    public final void setSource(int source) { mSource = source; }

    /** framework-internal (hidden in AOSP). */
    public final int getDisplayId() { return mDisplayId; }

    /** framework-internal (hidden in AOSP). */
    public final void setDisplayId(int displayId) { mDisplayId = displayId; }

    public final int getMetaState() { return mMetaState; }

    public final int getModifiers() { return normalizeMetaState(mMetaState) & META_MODIFIER_MASK; }

    /** framework-internal (hidden in AOSP). */
    public final void setFlags(int flags) { mFlags = flags; }

    public final int getFlags() { return mFlags; }

    public static int getModifierMetaStateMask() { return META_MODIFIER_MASK; }

    public static boolean isModifierKey(int keyCode) {
        switch (keyCode) {
            case KEYCODE_SHIFT_LEFT: case KEYCODE_SHIFT_RIGHT: case KEYCODE_ALT_LEFT: case KEYCODE_ALT_RIGHT:
            case KEYCODE_CTRL_LEFT: case KEYCODE_CTRL_RIGHT: case KEYCODE_META_LEFT: case KEYCODE_META_RIGHT:
            case KEYCODE_SYM: case KEYCODE_NUM: case KEYCODE_FUNCTION:
                return true;
            default:
                return false;
        }
    }

    public static int normalizeMetaState(int metaState) {
        if ((metaState & (META_SHIFT_LEFT_ON | META_SHIFT_RIGHT_ON)) != 0) metaState |= META_SHIFT_ON;
        if ((metaState & (META_ALT_LEFT_ON | META_ALT_RIGHT_ON)) != 0) metaState |= META_ALT_ON;
        if ((metaState & (META_CTRL_LEFT_ON | META_CTRL_RIGHT_ON)) != 0) metaState |= META_CTRL_ON;
        if ((metaState & (META_META_LEFT_ON | META_META_RIGHT_ON)) != 0) metaState |= META_META_ON;
        return metaState & META_ALL_MASK;
    }

    public static boolean metaStateHasNoModifiers(int metaState) {
        return (normalizeMetaState(metaState) & META_MODIFIER_MASK) == 0;
    }

    public static boolean metaStateHasModifiers(int metaState, int modifiers) {
        if ((modifiers & META_LOCK_MASK) != 0) {
            throw new IllegalArgumentException("modifiers must not contain META_CAPS_LOCK_ON, META_NUM_LOCK_ON, "
                    + "META_SCROLL_LOCK_ON, or META_SELECTING");
        }
        metaState = normalizeMetaState(metaState) & META_MODIFIER_MASK;
        metaState = metaStateFilterDirectionalModifiers(metaState, modifiers, META_SHIFT_ON, META_SHIFT_LEFT_ON,
                META_SHIFT_RIGHT_ON);
        metaState = metaStateFilterDirectionalModifiers(metaState, modifiers, META_ALT_ON, META_ALT_LEFT_ON,
                META_ALT_RIGHT_ON);
        metaState = metaStateFilterDirectionalModifiers(metaState, modifiers, META_CTRL_ON, META_CTRL_LEFT_ON,
                META_CTRL_RIGHT_ON);
        metaState = metaStateFilterDirectionalModifiers(metaState, modifiers, META_META_ON, META_META_LEFT_ON,
                META_META_RIGHT_ON);
        return metaState == modifiers;
    }

    private static int metaStateFilterDirectionalModifiers(int metaState, int modifiers, int basic, int left,
            int right) {
        final boolean wantBasic = (modifiers & basic) != 0;
        final int directional = left | right;
        final boolean wantLeftOrRight = (modifiers & directional) != 0;
        if (wantBasic) {
            if (wantLeftOrRight) {
                throw new IllegalArgumentException("modifiers must not contain both basic and directional modifiers");
            }
            return metaState & ~directional;
        } else if (wantLeftOrRight) {
            return metaState & ~basic;
        }
        return metaState;
    }

    public final boolean hasNoModifiers() { return metaStateHasNoModifiers(mMetaState); }
    public final boolean hasModifiers(int modifiers) { return metaStateHasModifiers(mMetaState, modifiers); }
    public final boolean isAltPressed() { return (mMetaState & META_ALT_ON) != 0; }
    public final boolean isShiftPressed() { return (mMetaState & META_SHIFT_ON) != 0; }
    public final boolean isSymPressed() { return (mMetaState & META_SYM_ON) != 0; }
    public final boolean isCtrlPressed() { return (mMetaState & META_CTRL_ON) != 0; }
    public final boolean isMetaPressed() { return (mMetaState & META_META_ON) != 0; }
    public final boolean isFunctionPressed() { return (mMetaState & META_FUNCTION_ON) != 0; }
    public final boolean isCapsLockOn() { return (mMetaState & META_CAPS_LOCK_ON) != 0; }
    public final boolean isNumLockOn() { return (mMetaState & META_NUM_LOCK_ON) != 0; }
    public final boolean isScrollLockOn() { return (mMetaState & META_SCROLL_LOCK_ON) != 0; }

    public final int getAction() { return mAction; }
    public final boolean isCanceled() { return (mFlags & FLAG_CANCELED) != 0; }

    /** framework-internal (hidden in AOSP). */
    public final void cancel() { mFlags |= FLAG_CANCELED; }

    public final void startTracking() { mFlags |= FLAG_START_TRACKING; }
    public final boolean isTracking() { return (mFlags & FLAG_TRACKING) != 0; }
    public final boolean isLongPress() { return (mFlags & FLAG_LONG_PRESS) != 0; }
    public final int getKeyCode() { return mKeyCode; }
    public final String getCharacters() { return mCharacters; }
    public final int getScanCode() { return mScanCode; }
    public final int getRepeatCount() { return mRepeatCount; }

    /** framework-internal (hidden in AOSP). */
    public final void setTime(long downTime, long eventTime) {
        mDownTime = downTime;
        mEventTime = eventTime;
    }

    public final long getDownTime() { return mDownTime; }

    @Override
    public final long getEventTime() { return mEventTime; }

    public final KeyCharacterMap getKeyCharacterMap() { return KeyCharacterMap.load(mDeviceId); }

    public char getDisplayLabel() { return getKeyCharacterMap().getDisplayLabel(mKeyCode); }
    public int getUnicodeChar() { return getUnicodeChar(mMetaState); }
    public int getUnicodeChar(int metaState) { return getKeyCharacterMap().get(mKeyCode, metaState); }
    public boolean getKeyData(KeyCharacterMap.KeyData results) { return getKeyCharacterMap().getKeyData(mKeyCode, results); }
    public char getMatch(char[] chars) { return getMatch(chars, 0); }
    public char getMatch(char[] chars, int metaState) { return getKeyCharacterMap().getMatch(mKeyCode, chars, metaState); }
    public char getNumber() { return getKeyCharacterMap().getNumber(mKeyCode); }
    public boolean isPrintingKey() { return getKeyCharacterMap().isPrintingKey(mKeyCode); }

    @Deprecated
    public final boolean dispatch(Callback receiver) { return dispatch(receiver, null, null); }

    public final boolean dispatch(Callback receiver, DispatcherState state, Object target) {
        switch (mAction) {
            case ACTION_DOWN: {
                mFlags &= ~FLAG_START_TRACKING;
                boolean res = receiver.onKeyDown(mKeyCode, this);
                if (state != null) {
                    if (res && mRepeatCount == 0 && (mFlags & FLAG_START_TRACKING) != 0) {
                        state.startTracking(this, target);
                    } else if (isLongPress() && state.isTracking(this)) {
                        try {
                            if (receiver.onKeyLongPress(mKeyCode, this)) {
                                state.performedLongPress(this);
                                res = true;
                            }
                        } catch (AbstractMethodError e) {
                        }
                    }
                }
                return res;
            }
            case ACTION_UP:
                if (state != null) state.handleUpEvent(this);
                return receiver.onKeyUp(mKeyCode, this);
            case ACTION_MULTIPLE: {
                final int count = mRepeatCount;
                final int code = mKeyCode;
                if (receiver.onKeyMultiple(code, count, this)) return true;
                if (code != KEYCODE_UNKNOWN) {
                    mAction = ACTION_DOWN;
                    mRepeatCount = 0;
                    boolean handled = receiver.onKeyDown(code, this);
                    if (handled) {
                        mAction = ACTION_UP;
                        receiver.onKeyUp(code, this);
                    }
                    mAction = ACTION_MULTIPLE;
                    mRepeatCount = count;
                    return handled;
                }
                return false;
            }
        }
        return false;
    }

    /** Tracks key presses for long press detection (AOSP KeyEvent.DispatcherState). */
    public static class DispatcherState {
        int mDownKeyCode;
        Object mDownTarget;
        android.util.SparseIntArray mActiveLongPresses = new android.util.SparseIntArray();

        public DispatcherState() {}

        public void reset() {
            mDownKeyCode = 0;
            mDownTarget = null;
            mActiveLongPresses.clear();
        }

        public void reset(Object target) {
            if (mDownTarget == target) {
                mDownKeyCode = 0;
                mDownTarget = null;
            }
        }

        public void startTracking(KeyEvent event, Object target) {
            if (event.getAction() != ACTION_DOWN) {
                throw new IllegalArgumentException("Can only start tracking on a down event");
            }
            mDownKeyCode = event.getKeyCode();
            mDownTarget = target;
        }

        public boolean isTracking(KeyEvent event) { return mDownKeyCode == event.getKeyCode(); }

        public void performedLongPress(KeyEvent event) { mActiveLongPresses.put(event.getKeyCode(), 1); }

        public void handleUpEvent(KeyEvent event) {
            final int keyCode = event.getKeyCode();
            int index = mActiveLongPresses.indexOfKey(keyCode);
            if (index >= 0) {
                event.mFlags |= FLAG_CANCELED | FLAG_CANCELED_LONG_PRESS;
                mActiveLongPresses.removeAt(index);
            }
            if (mDownKeyCode == keyCode) {
                event.mFlags |= FLAG_TRACKING;
                mDownKeyCode = 0;
                mDownTarget = null;
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder msg = new StringBuilder();
        msg.append("KeyEvent { action=").append(actionToString(mAction));
        msg.append(", keyCode=").append(keyCodeToString(mKeyCode));
        msg.append(", scanCode=").append(mScanCode);
        if (mCharacters != null) msg.append(", characters=\"").append(mCharacters).append("\"");
        msg.append(", metaState=").append(mMetaState);
        msg.append(", flags=0x").append(Integer.toHexString(mFlags));
        msg.append(", repeatCount=").append(mRepeatCount);
        msg.append(", eventTime=").append(mEventTime);
        msg.append(", downTime=").append(mDownTime);
        msg.append(", deviceId=").append(mDeviceId);
        msg.append(", source=0x").append(Integer.toHexString(mSource));
        msg.append(" }");
        return msg.toString();
    }

    /** framework-internal (hidden in AOSP). */
    public static String actionToString(int action) {
        switch (action) {
            case ACTION_DOWN: return "ACTION_DOWN";
            case ACTION_UP: return "ACTION_UP";
            case ACTION_MULTIPLE: return "ACTION_MULTIPLE";
            default: return Integer.toString(action);
        }
    }

    public static String keyCodeToString(int keyCode) {
        if (keyCode >= 0 && keyCode < KEYCODE_NAMES.length) {
            String name = KEYCODE_NAMES[keyCode];
            if (name.startsWith("KEYCODE_")) return name;
        }
        return Integer.toString(keyCode);
    }

    public static int keyCodeFromString(String symbolicName) {
        try {
            int keyCode = Integer.parseInt(symbolicName);
            if (keyCode >= 0 && keyCode <= LAST_KEYCODE) return keyCode;
        } catch (NumberFormatException ex) {
        }
        if (!symbolicName.startsWith("KEYCODE_")) symbolicName = "KEYCODE_" + symbolicName;
        for (int i = 0; i < KEYCODE_NAMES.length; i++) {
            if (KEYCODE_NAMES[i].equals(symbolicName)) return i;
        }
        return KEYCODE_UNKNOWN;
    }

    private static final int FLAG_START_TRACKING = 0x40000000;
    private static final int LAST_KEYCODE = 318;

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(2);
        out.writeInt(mDeviceId);
        out.writeInt(mSource);
        out.writeInt(mDisplayId);
        out.writeInt(mAction);
        out.writeInt(mKeyCode);
        out.writeInt(mRepeatCount);
        out.writeInt(mMetaState);
        out.writeInt(mScanCode);
        out.writeInt(mFlags);
        out.writeLong(mDownTime);
        out.writeLong(mEventTime);
        out.writeString(mCharacters);
    }

    /** framework-internal (hidden in AOSP). */
    public static KeyEvent createFromParcelBody(Parcel in) { return new KeyEvent(in); }

    public static final Parcelable.Creator<KeyEvent> CREATOR = new Parcelable.Creator<KeyEvent>() {
        public KeyEvent createFromParcel(Parcel in) {
            in.readInt();
            return KeyEvent.createFromParcelBody(in);
        }

        public KeyEvent[] newArray(int size) { return new KeyEvent[size]; }
    };

    private static final String[] KEYCODE_NAMES = {
            "KEYCODE_UNKNOWN",
            "KEYCODE_SOFT_LEFT",
            "KEYCODE_SOFT_RIGHT",
            "KEYCODE_HOME",
            "KEYCODE_BACK",
            "KEYCODE_CALL",
            "KEYCODE_ENDCALL",
            "KEYCODE_0",
            "KEYCODE_1",
            "KEYCODE_2",
            "KEYCODE_3",
            "KEYCODE_4",
            "KEYCODE_5",
            "KEYCODE_6",
            "KEYCODE_7",
            "KEYCODE_8",
            "KEYCODE_9",
            "KEYCODE_STAR",
            "KEYCODE_POUND",
            "KEYCODE_DPAD_UP",
            "KEYCODE_DPAD_DOWN",
            "KEYCODE_DPAD_LEFT",
            "KEYCODE_DPAD_RIGHT",
            "KEYCODE_DPAD_CENTER",
            "KEYCODE_VOLUME_UP",
            "KEYCODE_VOLUME_DOWN",
            "KEYCODE_POWER",
            "KEYCODE_CAMERA",
            "KEYCODE_CLEAR",
            "KEYCODE_A",
            "KEYCODE_B",
            "KEYCODE_C",
            "KEYCODE_D",
            "KEYCODE_E",
            "KEYCODE_F",
            "KEYCODE_G",
            "KEYCODE_H",
            "KEYCODE_I",
            "KEYCODE_J",
            "KEYCODE_K",
            "KEYCODE_L",
            "KEYCODE_M",
            "KEYCODE_N",
            "KEYCODE_O",
            "KEYCODE_P",
            "KEYCODE_Q",
            "KEYCODE_R",
            "KEYCODE_S",
            "KEYCODE_T",
            "KEYCODE_U",
            "KEYCODE_V",
            "KEYCODE_W",
            "KEYCODE_X",
            "KEYCODE_Y",
            "KEYCODE_Z",
            "KEYCODE_COMMA",
            "KEYCODE_PERIOD",
            "KEYCODE_ALT_LEFT",
            "KEYCODE_ALT_RIGHT",
            "KEYCODE_SHIFT_LEFT",
            "KEYCODE_SHIFT_RIGHT",
            "KEYCODE_TAB",
            "KEYCODE_SPACE",
            "KEYCODE_SYM",
            "KEYCODE_EXPLORER",
            "KEYCODE_ENVELOPE",
            "KEYCODE_ENTER",
            "KEYCODE_DEL",
            "KEYCODE_GRAVE",
            "KEYCODE_MINUS",
            "KEYCODE_EQUALS",
            "KEYCODE_LEFT_BRACKET",
            "KEYCODE_RIGHT_BRACKET",
            "KEYCODE_BACKSLASH",
            "KEYCODE_SEMICOLON",
            "KEYCODE_APOSTROPHE",
            "KEYCODE_SLASH",
            "KEYCODE_AT",
            "KEYCODE_NUM",
            "KEYCODE_HEADSETHOOK",
            "KEYCODE_FOCUS",
            "KEYCODE_PLUS",
            "KEYCODE_MENU",
            "KEYCODE_NOTIFICATION",
            "KEYCODE_SEARCH",
            "KEYCODE_MEDIA_PLAY_PAUSE",
            "KEYCODE_MEDIA_STOP",
            "KEYCODE_MEDIA_NEXT",
            "KEYCODE_MEDIA_PREVIOUS",
            "KEYCODE_MEDIA_REWIND",
            "KEYCODE_MEDIA_FAST_FORWARD",
            "KEYCODE_MUTE",
            "KEYCODE_PAGE_UP",
            "KEYCODE_PAGE_DOWN",
            "KEYCODE_PICTSYMBOLS",
            "KEYCODE_SWITCH_CHARSET",
            "KEYCODE_BUTTON_A",
            "KEYCODE_BUTTON_B",
            "KEYCODE_BUTTON_C",
            "KEYCODE_BUTTON_X",
            "KEYCODE_BUTTON_Y",
            "KEYCODE_BUTTON_Z",
            "KEYCODE_BUTTON_L1",
            "KEYCODE_BUTTON_R1",
            "KEYCODE_BUTTON_L2",
            "KEYCODE_BUTTON_R2",
            "KEYCODE_BUTTON_THUMBL",
            "KEYCODE_BUTTON_THUMBR",
            "KEYCODE_BUTTON_START",
            "KEYCODE_BUTTON_SELECT",
            "KEYCODE_BUTTON_MODE",
            "KEYCODE_ESCAPE",
            "KEYCODE_FORWARD_DEL",
            "KEYCODE_CTRL_LEFT",
            "KEYCODE_CTRL_RIGHT",
            "KEYCODE_CAPS_LOCK",
            "KEYCODE_SCROLL_LOCK",
            "KEYCODE_META_LEFT",
            "KEYCODE_META_RIGHT",
            "KEYCODE_FUNCTION",
            "KEYCODE_SYSRQ",
            "KEYCODE_BREAK",
            "KEYCODE_MOVE_HOME",
            "KEYCODE_MOVE_END",
            "KEYCODE_INSERT",
            "KEYCODE_FORWARD",
            "KEYCODE_MEDIA_PLAY",
            "KEYCODE_MEDIA_PAUSE",
            "KEYCODE_MEDIA_CLOSE",
            "KEYCODE_MEDIA_EJECT",
            "KEYCODE_MEDIA_RECORD",
            "KEYCODE_F1",
            "KEYCODE_F2",
            "KEYCODE_F3",
            "KEYCODE_F4",
            "KEYCODE_F5",
            "KEYCODE_F6",
            "KEYCODE_F7",
            "KEYCODE_F8",
            "KEYCODE_F9",
            "KEYCODE_F10",
            "KEYCODE_F11",
            "KEYCODE_F12",
            "KEYCODE_NUM_LOCK",
            "KEYCODE_NUMPAD_0",
            "KEYCODE_NUMPAD_1",
            "KEYCODE_NUMPAD_2",
            "KEYCODE_NUMPAD_3",
            "KEYCODE_NUMPAD_4",
            "KEYCODE_NUMPAD_5",
            "KEYCODE_NUMPAD_6",
            "KEYCODE_NUMPAD_7",
            "KEYCODE_NUMPAD_8",
            "KEYCODE_NUMPAD_9",
            "KEYCODE_NUMPAD_DIVIDE",
            "KEYCODE_NUMPAD_MULTIPLY",
            "KEYCODE_NUMPAD_SUBTRACT",
            "KEYCODE_NUMPAD_ADD",
            "KEYCODE_NUMPAD_DOT",
            "KEYCODE_NUMPAD_COMMA",
            "KEYCODE_NUMPAD_ENTER",
            "KEYCODE_NUMPAD_EQUALS",
            "KEYCODE_NUMPAD_LEFT_PAREN",
            "KEYCODE_NUMPAD_RIGHT_PAREN",
            "KEYCODE_VOLUME_MUTE",
            "KEYCODE_INFO",
            "KEYCODE_CHANNEL_UP",
            "KEYCODE_CHANNEL_DOWN",
            "KEYCODE_ZOOM_IN",
            "KEYCODE_ZOOM_OUT",
            "KEYCODE_TV",
            "KEYCODE_WINDOW",
            "KEYCODE_GUIDE",
            "KEYCODE_DVR",
            "KEYCODE_BOOKMARK",
            "KEYCODE_CAPTIONS",
            "KEYCODE_SETTINGS",
            "KEYCODE_TV_POWER",
            "KEYCODE_TV_INPUT",
            "KEYCODE_STB_POWER",
            "KEYCODE_STB_INPUT",
            "KEYCODE_AVR_POWER",
            "KEYCODE_AVR_INPUT",
            "KEYCODE_PROG_RED",
            "KEYCODE_PROG_GREEN",
            "KEYCODE_PROG_YELLOW",
            "KEYCODE_PROG_BLUE",
            "KEYCODE_APP_SWITCH",
            "KEYCODE_BUTTON_1",
            "KEYCODE_BUTTON_2",
            "KEYCODE_BUTTON_3",
            "KEYCODE_BUTTON_4",
            "KEYCODE_BUTTON_5",
            "KEYCODE_BUTTON_6",
            "KEYCODE_BUTTON_7",
            "KEYCODE_BUTTON_8",
            "KEYCODE_BUTTON_9",
            "KEYCODE_BUTTON_10",
            "KEYCODE_BUTTON_11",
            "KEYCODE_BUTTON_12",
            "KEYCODE_BUTTON_13",
            "KEYCODE_BUTTON_14",
            "KEYCODE_BUTTON_15",
            "KEYCODE_BUTTON_16",
            "KEYCODE_LANGUAGE_SWITCH",
            "KEYCODE_MANNER_MODE",
            "KEYCODE_3D_MODE",
            "KEYCODE_CONTACTS",
            "KEYCODE_CALENDAR",
            "KEYCODE_MUSIC",
            "KEYCODE_CALCULATOR",
            "KEYCODE_ZENKAKU_HANKAKU",
            "KEYCODE_EISU",
            "KEYCODE_MUHENKAN",
            "KEYCODE_HENKAN",
            "KEYCODE_KATAKANA_HIRAGANA",
            "KEYCODE_YEN",
            "KEYCODE_RO",
            "KEYCODE_KANA",
            "KEYCODE_ASSIST",
            "KEYCODE_BRIGHTNESS_DOWN",
            "KEYCODE_BRIGHTNESS_UP",
            "KEYCODE_MEDIA_AUDIO_TRACK",
            "KEYCODE_SLEEP",
            "KEYCODE_WAKEUP",
            "KEYCODE_PAIRING",
            "KEYCODE_MEDIA_TOP_MENU",
            "KEYCODE_11",
            "KEYCODE_12",
            "KEYCODE_LAST_CHANNEL",
            "KEYCODE_TV_DATA_SERVICE",
            "KEYCODE_VOICE_ASSIST",
            "KEYCODE_TV_RADIO_SERVICE",
            "KEYCODE_TV_TELETEXT",
            "KEYCODE_TV_NUMBER_ENTRY",
            "KEYCODE_TV_TERRESTRIAL_ANALOG",
            "KEYCODE_TV_TERRESTRIAL_DIGITAL",
            "KEYCODE_TV_SATELLITE",
            "KEYCODE_TV_SATELLITE_BS",
            "KEYCODE_TV_SATELLITE_CS",
            "KEYCODE_TV_SATELLITE_SERVICE",
            "KEYCODE_TV_NETWORK",
            "KEYCODE_TV_ANTENNA_CABLE",
            "KEYCODE_TV_INPUT_HDMI_1",
            "KEYCODE_TV_INPUT_HDMI_2",
            "KEYCODE_TV_INPUT_HDMI_3",
            "KEYCODE_TV_INPUT_HDMI_4",
            "KEYCODE_TV_INPUT_COMPOSITE_1",
            "KEYCODE_TV_INPUT_COMPOSITE_2",
            "KEYCODE_TV_INPUT_COMPONENT_1",
            "KEYCODE_TV_INPUT_COMPONENT_2",
            "KEYCODE_TV_INPUT_VGA_1",
            "KEYCODE_TV_AUDIO_DESCRIPTION",
            "KEYCODE_TV_AUDIO_DESCRIPTION_MIX_UP",
            "KEYCODE_TV_AUDIO_DESCRIPTION_MIX_DOWN",
            "KEYCODE_TV_ZOOM_MODE",
            "KEYCODE_TV_CONTENTS_MENU",
            "KEYCODE_TV_MEDIA_CONTEXT_MENU",
            "KEYCODE_TV_TIMER_PROGRAMMING",
            "KEYCODE_HELP",
            "KEYCODE_NAVIGATE_PREVIOUS",
            "KEYCODE_NAVIGATE_NEXT",
            "KEYCODE_NAVIGATE_IN",
            "KEYCODE_NAVIGATE_OUT",
            "KEYCODE_STEM_PRIMARY",
            "KEYCODE_STEM_1",
            "KEYCODE_STEM_2",
            "KEYCODE_STEM_3",
            "KEYCODE_DPAD_UP_LEFT",
            "KEYCODE_DPAD_DOWN_LEFT",
            "KEYCODE_DPAD_UP_RIGHT",
            "KEYCODE_DPAD_DOWN_RIGHT",
            "KEYCODE_MEDIA_SKIP_FORWARD",
            "KEYCODE_MEDIA_SKIP_BACKWARD",
            "KEYCODE_MEDIA_STEP_FORWARD",
            "KEYCODE_MEDIA_STEP_BACKWARD",
            "KEYCODE_SOFT_SLEEP",
            "KEYCODE_CUT",
            "KEYCODE_COPY",
            "KEYCODE_PASTE",
            "KEYCODE_SYSTEM_NAVIGATION_UP",
            "KEYCODE_SYSTEM_NAVIGATION_DOWN",
            "KEYCODE_SYSTEM_NAVIGATION_LEFT",
            "KEYCODE_SYSTEM_NAVIGATION_RIGHT",
            "KEYCODE_ALL_APPS",
            "KEYCODE_REFRESH",
            "KEYCODE_THUMBS_UP",
            "KEYCODE_THUMBS_DOWN",
            "KEYCODE_PROFILE_SWITCH",
            "KEYCODE_VIDEO_APP_1",
            "KEYCODE_VIDEO_APP_2",
            "KEYCODE_VIDEO_APP_3",
            "KEYCODE_VIDEO_APP_4",
            "KEYCODE_VIDEO_APP_5",
            "KEYCODE_VIDEO_APP_6",
            "KEYCODE_VIDEO_APP_7",
            "KEYCODE_VIDEO_APP_8",
            "KEYCODE_FEATURED_APP_1",
            "KEYCODE_FEATURED_APP_2",
            "KEYCODE_FEATURED_APP_3",
            "KEYCODE_FEATURED_APP_4",
            "KEYCODE_DEMO_APP_1",
            "KEYCODE_DEMO_APP_2",
            "KEYCODE_DEMO_APP_3",
            "KEYCODE_DEMO_APP_4",
            "KEYCODE_KEYBOARD_BACKLIGHT_DOWN",
            "KEYCODE_KEYBOARD_BACKLIGHT_UP",
            "KEYCODE_KEYBOARD_BACKLIGHT_TOGGLE",
            "KEYCODE_STYLUS_BUTTON_PRIMARY",
            "KEYCODE_STYLUS_BUTTON_SECONDARY",
            "KEYCODE_STYLUS_BUTTON_TERTIARY",
            "KEYCODE_STYLUS_BUTTON_TAIL",
            "KEYCODE_RECENT_APPS",
            "KEYCODE_MACRO_1",
            "KEYCODE_MACRO_2",
            "KEYCODE_MACRO_3",
            "KEYCODE_MACRO_4",
            "KEYCODE_EMOJI_PICKER",
            "KEYCODE_SCREENSHOT"
    };
}
