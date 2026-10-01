package android.content;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class Intent implements Parcelable, Cloneable {
    public static final String ACTION_MAIN = "android.intent.action.MAIN";
    public static final String ACTION_VIEW = "android.intent.action.VIEW";
    public static final String ACTION_DEFAULT = ACTION_VIEW;
    public static final String ACTION_ATTACH_DATA = "android.intent.action.ATTACH_DATA";
    public static final String ACTION_EDIT = "android.intent.action.EDIT";
    public static final String ACTION_INSERT_OR_EDIT = "android.intent.action.INSERT_OR_EDIT";
    public static final String ACTION_PICK = "android.intent.action.PICK";
    public static final String ACTION_CREATE_SHORTCUT = "android.intent.action.CREATE_SHORTCUT";
    public static final String ACTION_CHOOSER = "android.intent.action.CHOOSER";
    public static final String ACTION_GET_CONTENT = "android.intent.action.GET_CONTENT";
    public static final String ACTION_DIAL = "android.intent.action.DIAL";
    public static final String ACTION_CALL = "android.intent.action.CALL";
    public static final String ACTION_SENDTO = "android.intent.action.SENDTO";
    public static final String ACTION_SEND = "android.intent.action.SEND";
    public static final String ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE";
    public static final String ACTION_ANSWER = "android.intent.action.ANSWER";
    public static final String ACTION_INSERT = "android.intent.action.INSERT";
    public static final String ACTION_PASTE = "android.intent.action.PASTE";
    public static final String ACTION_DELETE = "android.intent.action.DELETE";
    public static final String ACTION_RUN = "android.intent.action.RUN";
    public static final String ACTION_SYNC = "android.intent.action.SYNC";
    public static final String ACTION_PICK_ACTIVITY = "android.intent.action.PICK_ACTIVITY";
    public static final String ACTION_SEARCH = "android.intent.action.SEARCH";
    public static final String ACTION_WEB_SEARCH = "android.intent.action.WEB_SEARCH";
    public static final String ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT";
    public static final String ACTION_CREATE_DOCUMENT = "android.intent.action.CREATE_DOCUMENT";
    public static final String ACTION_OPEN_DOCUMENT_TREE = "android.intent.action.OPEN_DOCUMENT_TREE";
    public static final String ACTION_SCREEN_OFF = "android.intent.action.SCREEN_OFF";
    public static final String ACTION_SCREEN_ON = "android.intent.action.SCREEN_ON";
    public static final String ACTION_USER_PRESENT = "android.intent.action.USER_PRESENT";
    public static final String ACTION_TIME_TICK = "android.intent.action.TIME_TICK";
    public static final String ACTION_TIME_CHANGED = "android.intent.action.TIME_SET";
    public static final String ACTION_DATE_CHANGED = "android.intent.action.DATE_CHANGED";
    public static final String ACTION_TIMEZONE_CHANGED = "android.intent.action.TIMEZONE_CHANGED";
    public static final String ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";
    public static final String ACTION_CLOSE_SYSTEM_DIALOGS = "android.intent.action.CLOSE_SYSTEM_DIALOGS";
    public static final String ACTION_PACKAGE_ADDED = "android.intent.action.PACKAGE_ADDED";
    public static final String ACTION_PACKAGE_REMOVED = "android.intent.action.PACKAGE_REMOVED";
    public static final String ACTION_BATTERY_CHANGED = "android.intent.action.BATTERY_CHANGED";
    public static final String ACTION_BATTERY_LOW = "android.intent.action.BATTERY_LOW";
    public static final String ACTION_BATTERY_OKAY = "android.intent.action.BATTERY_OKAY";
    public static final String ACTION_POWER_CONNECTED = "android.intent.action.ACTION_POWER_CONNECTED";
    public static final String ACTION_POWER_DISCONNECTED = "android.intent.action.ACTION_POWER_DISCONNECTED";
    public static final String ACTION_SHUTDOWN = "android.intent.action.ACTION_SHUTDOWN";
    public static final String ACTION_CONFIGURATION_CHANGED = "android.intent.action.CONFIGURATION_CHANGED";
    public static final String ACTION_LOCALE_CHANGED = "android.intent.action.LOCALE_CHANGED";
    public static final String ACTION_MEDIA_MOUNTED = "android.intent.action.MEDIA_MOUNTED";
    public static final String ACTION_MEDIA_SCANNER_SCAN_FILE = "android.intent.action.MEDIA_SCANNER_SCAN_FILE";
    public static final String ACTION_HEADSET_PLUG = "android.intent.action.HEADSET_PLUG";
    public static final String ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS";
    public static final String ACTION_INSTALL_PACKAGE = "android.intent.action.INSTALL_PACKAGE";
    public static final String ACTION_APP_ERROR = "android.intent.action.APP_ERROR";
    public static final String ACTION_ASSIST = "android.intent.action.ASSIST";
    public static final String ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED";

    public static final String CATEGORY_DEFAULT = "android.intent.category.DEFAULT";
    public static final String CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE";
    public static final String CATEGORY_ALTERNATIVE = "android.intent.category.ALTERNATIVE";
    public static final String CATEGORY_SELECTED_ALTERNATIVE = "android.intent.category.SELECTED_ALTERNATIVE";
    public static final String CATEGORY_TAB = "android.intent.category.TAB";
    public static final String CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER";
    public static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";
    public static final String CATEGORY_INFO = "android.intent.category.INFO";
    public static final String CATEGORY_HOME = "android.intent.category.HOME";
    public static final String CATEGORY_PREFERENCE = "android.intent.category.PREFERENCE";
    public static final String CATEGORY_TEST = "android.intent.category.TEST";
    public static final String CATEGORY_OPENABLE = "android.intent.category.OPENABLE";
    public static final String CATEGORY_GAME = "android.intent.category.GAME";
    public static final String CATEGORY_APP_MARKET = "android.intent.category.APP_MARKET";
    public static final String CATEGORY_APP_BROWSER = "android.intent.category.APP_BROWSER";
    public static final String CATEGORY_APP_EMAIL = "android.intent.category.APP_EMAIL";

    public static final String EXTRA_TEXT = "android.intent.extra.TEXT";
    public static final String EXTRA_HTML_TEXT = "android.intent.extra.HTML_TEXT";
    public static final String EXTRA_STREAM = "android.intent.extra.STREAM";
    public static final String EXTRA_EMAIL = "android.intent.extra.EMAIL";
    public static final String EXTRA_CC = "android.intent.extra.CC";
    public static final String EXTRA_BCC = "android.intent.extra.BCC";
    public static final String EXTRA_SUBJECT = "android.intent.extra.SUBJECT";
    public static final String EXTRA_INTENT = "android.intent.extra.INTENT";
    public static final String EXTRA_TITLE = "android.intent.extra.TITLE";
    public static final String EXTRA_INITIAL_INTENTS = "android.intent.extra.INITIAL_INTENTS";
    public static final String EXTRA_SHORTCUT_INTENT = "android.intent.extra.shortcut.INTENT";
    public static final String EXTRA_SHORTCUT_NAME = "android.intent.extra.shortcut.NAME";
    public static final String EXTRA_SHORTCUT_ICON = "android.intent.extra.shortcut.ICON";
    public static final String EXTRA_SHORTCUT_ICON_RESOURCE = "android.intent.extra.shortcut.ICON_RESOURCE";
    public static final String EXTRA_PACKAGE_NAME = "android.intent.extra.PACKAGE_NAME";
    public static final String EXTRA_UID = "android.intent.extra.UID";
    public static final String EXTRA_ALLOW_MULTIPLE = "android.intent.extra.ALLOW_MULTIPLE";
    public static final String EXTRA_LOCAL_ONLY = "android.intent.extra.LOCAL_ONLY";
    public static final String EXTRA_MIME_TYPES = "android.intent.extra.MIME_TYPES";
    public static final String EXTRA_REFERRER = "android.intent.extra.REFERRER";
    public static final String EXTRA_PHONE_NUMBER = "android.intent.extra.PHONE_NUMBER";
    public static final String EXTRA_KEY_EVENT = "android.intent.extra.KEY_EVENT";
    public static final String EXTRA_REPLACING = "android.intent.extra.REPLACING";
    public static final String EXTRA_CHOSEN_COMPONENT = "android.intent.extra.CHOSEN_COMPONENT";
    public static final String EXTRA_TEMPLATE = "android.intent.extra.TEMPLATE";

    public static final int FLAG_GRANT_READ_URI_PERMISSION = 0x00000001;
    public static final int FLAG_GRANT_WRITE_URI_PERMISSION = 0x00000002;
    public static final int FLAG_FROM_BACKGROUND = 0x00000004;
    public static final int FLAG_DEBUG_LOG_RESOLUTION = 0x00000008;
    public static final int FLAG_EXCLUDE_STOPPED_PACKAGES = 0x00000010;
    public static final int FLAG_INCLUDE_STOPPED_PACKAGES = 0x00000020;
    public static final int FLAG_GRANT_PERSISTABLE_URI_PERMISSION = 0x00000040;
    public static final int FLAG_GRANT_PREFIX_URI_PERMISSION = 0x00000080;
    public static final int FLAG_ACTIVITY_MATCH_EXTERNAL = 0x00000800;
    public static final int FLAG_ACTIVITY_NO_HISTORY = 0x40000000;
    public static final int FLAG_ACTIVITY_SINGLE_TOP = 0x20000000;
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    public static final int FLAG_ACTIVITY_MULTIPLE_TASK = 0x08000000;
    public static final int FLAG_ACTIVITY_CLEAR_TOP = 0x04000000;
    public static final int FLAG_ACTIVITY_FORWARD_RESULT = 0x02000000;
    public static final int FLAG_ACTIVITY_PREVIOUS_IS_TOP = 0x01000000;
    public static final int FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS = 0x00800000;
    public static final int FLAG_ACTIVITY_BROUGHT_TO_FRONT = 0x00400000;
    public static final int FLAG_ACTIVITY_RESET_TASK_IF_NEEDED = 0x00200000;
    public static final int FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY = 0x00100000;
    public static final int FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET = 0x00080000;
    public static final int FLAG_ACTIVITY_NEW_DOCUMENT = FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET;
    public static final int FLAG_ACTIVITY_NO_USER_ACTION = 0x00040000;
    public static final int FLAG_ACTIVITY_REORDER_TO_FRONT = 0x00020000;
    public static final int FLAG_ACTIVITY_NO_ANIMATION = 0x00010000;
    public static final int FLAG_ACTIVITY_CLEAR_TASK = 0x00008000;
    public static final int FLAG_ACTIVITY_TASK_ON_HOME = 0x00004000;
    public static final int FLAG_ACTIVITY_RETAIN_IN_RECENTS = 0x00002000;
    public static final int FLAG_ACTIVITY_LAUNCH_ADJACENT = 0x00001000;
    public static final int FLAG_ACTIVITY_REQUIRE_NON_BROWSER = 0x00000400;
    public static final int FLAG_ACTIVITY_REQUIRE_DEFAULT = 0x00000200;
    public static final int FLAG_RECEIVER_REGISTERED_ONLY = 0x40000000;
    public static final int FLAG_RECEIVER_REPLACE_PENDING = 0x20000000;
    public static final int FLAG_RECEIVER_FOREGROUND = 0x10000000;
    public static final int FLAG_RECEIVER_NO_ABORT = 0x08000000;
    public static final int FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS = 0x00200000;
    public static final int URI_INTENT_SCHEME = 1 << 0;
    public static final int URI_ANDROID_APP_SCHEME = 1 << 1;
    public static final int URI_ALLOW_UNSAFE = 1 << 2;
    public static final int FILL_IN_ACTION = 1 << 0;
    public static final int FILL_IN_DATA = 1 << 1;
    public static final int FILL_IN_CATEGORIES = 1 << 2;
    public static final int FILL_IN_COMPONENT = 1 << 3;
    public static final int FILL_IN_PACKAGE = 1 << 4;
    public static final int FILL_IN_SOURCE_BOUNDS = 1 << 5;
    public static final int FILL_IN_SELECTOR = 1 << 6;
    public static final int FILL_IN_CLIP_DATA = 1 << 7;

    private String mAction;
    private Uri mData;
    private String mType;
    private String mPackage;
    private ComponentName mComponent;
    private int mFlags;
    private HashSet<String> mCategories;
    private Bundle mExtras;
    private Intent mSelector;
    private ClipData mClipData;
    private android.graphics.Rect mSourceBounds;
    private String mIdentifier;

    public Intent() {}

    public Intent(Intent o) {
        this.mAction = o.mAction;
        this.mData = o.mData;
        this.mType = o.mType;
        this.mPackage = o.mPackage;
        this.mComponent = o.mComponent;
        this.mFlags = o.mFlags;
        this.mIdentifier = o.mIdentifier;
        if (o.mCategories != null) this.mCategories = new HashSet<String>(o.mCategories);
        if (o.mExtras != null) this.mExtras = new Bundle(o.mExtras);
        if (o.mSelector != null) this.mSelector = new Intent(o.mSelector);
        this.mClipData = o.mClipData;
        if (o.mSourceBounds != null) this.mSourceBounds = new android.graphics.Rect(o.mSourceBounds);
    }

    public Intent(String action) { setAction(action); }

    public Intent(String action, Uri uri) {
        setAction(action);
        mData = uri;
    }

    public Intent(Context packageContext, Class<?> cls) { mComponent = new ComponentName(packageContext, cls); }

    public Intent(String action, Uri uri, Context packageContext, Class<?> cls) {
        setAction(action);
        mData = uri;
        mComponent = new ComponentName(packageContext, cls);
    }

    @Override
    public Object clone() { return new Intent(this); }

    public Intent cloneFilter() {
        Intent i = new Intent();
        i.mAction = mAction;
        i.mData = mData;
        i.mType = mType;
        i.mPackage = mPackage;
        i.mComponent = mComponent;
        if (mCategories != null) i.mCategories = new HashSet<String>(mCategories);
        return i;
    }

    public static Intent createChooser(Intent target, CharSequence title) { return createChooser(target, title, null); }

    public static Intent createChooser(Intent target, CharSequence title, IntentSender sender) {
        Intent intent = new Intent(ACTION_CHOOSER);
        intent.putExtra(EXTRA_INTENT, target);
        if (title != null) intent.putExtra(EXTRA_TITLE, title);
        return intent;
    }

    public static Intent makeMainActivity(ComponentName mainActivity) {
        Intent intent = new Intent(ACTION_MAIN);
        intent.setComponent(mainActivity);
        intent.addCategory(CATEGORY_LAUNCHER);
        return intent;
    }

    public static Intent makeMainSelectorActivity(String selectorAction, String selectorCategory) {
        Intent intent = new Intent(ACTION_MAIN);
        intent.addCategory(CATEGORY_LAUNCHER);
        return intent;
    }

    public static Intent makeRestartActivityTask(ComponentName mainActivity) {
        Intent intent = makeMainActivity(mainActivity);
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }

    @Deprecated
    public static Intent getIntent(String uri) throws java.net.URISyntaxException { return parseUri(uri, 0); }

    public static Intent parseUri(String uri, int flags) throws java.net.URISyntaxException {
        if (uri.startsWith("intent:") || uri.startsWith("#Intent;")) {
            Intent intent = new Intent(ACTION_VIEW);
            int i = uri.indexOf("#Intent;");
            String data = uri.startsWith("intent:") ? uri.substring(7, i < 0 ? uri.length() : i) : null;
            if (data != null && !data.isEmpty()) intent.mData = Uri.parse(data);
            if (i >= 0) {
                String[] parts = uri.substring(i + 8).split(";");
                for (String p : parts) {
                    int eq = p.indexOf('=');
                    if (eq < 0) continue;
                    String k = p.substring(0, eq), v = Uri.decode(p.substring(eq + 1));
                    if (k.equals("action")) intent.setAction(v);
                    else if (k.equals("category")) intent.addCategory(v);
                    else if (k.equals("type")) intent.mType = v;
                    else if (k.equals("launchFlags")) intent.mFlags = Integer.decode(v);
                    else if (k.equals("package")) intent.mPackage = v;
                    else if (k.equals("component")) intent.mComponent = ComponentName.unflattenFromString(v);
                    else if (k.equals("scheme") && intent.mData != null) intent.mData = Uri.parse(v + ":" + intent.mData);
                    else if (k.startsWith("S.")) intent.putExtra(k.substring(2), v);
                    else if (k.startsWith("i.")) intent.putExtra(k.substring(2), Integer.parseInt(v));
                    else if (k.startsWith("l.")) intent.putExtra(k.substring(2), Long.parseLong(v));
                    else if (k.startsWith("B.")) intent.putExtra(k.substring(2), Boolean.parseBoolean(v));
                }
            }
            return intent;
        }
        return new Intent(ACTION_VIEW, Uri.parse(uri));
    }

    public static Intent parseIntent(android.content.res.Resources resources, org.xmlpull.v1.XmlPullParser parser, android.util.AttributeSet attrs) {
        return new Intent();
    }

    public String getAction() { return mAction; }
    public Uri getData() { return mData; }
    public String getDataString() { return mData != null ? mData.toString() : null; }
    public String getScheme() { return mData != null ? mData.getScheme() : null; }
    public String getType() { return mType; }
    public String resolveType(Context context) { return mType; }
    public String resolveType(ContentResolver resolver) { return mType; }
    public String resolveTypeIfNeeded(ContentResolver resolver) { return mComponent != null ? mType : resolveType(resolver); }
    public boolean hasCategory(String category) { return mCategories != null && mCategories.contains(category); }
    public Set<String> getCategories() { return mCategories; }
    public Intent getSelector() { return mSelector; }
    public ClipData getClipData() { return mClipData; }
    public void setExtrasClassLoader(ClassLoader loader) {}
    public boolean hasExtra(String name) { return mExtras != null && mExtras.containsKey(name); }
    public boolean hasFileDescriptors() { return false; }

    public boolean getBooleanExtra(String name, boolean defaultValue) { return mExtras == null ? defaultValue : mExtras.getBoolean(name, defaultValue); }
    public byte getByteExtra(String name, byte defaultValue) { return mExtras == null ? defaultValue : mExtras.getByte(name, defaultValue); }
    public short getShortExtra(String name, short defaultValue) { return mExtras == null ? defaultValue : mExtras.getShort(name, defaultValue); }
    public char getCharExtra(String name, char defaultValue) { return mExtras == null ? defaultValue : mExtras.getChar(name, defaultValue); }
    public int getIntExtra(String name, int defaultValue) { return mExtras == null ? defaultValue : mExtras.getInt(name, defaultValue); }
    public long getLongExtra(String name, long defaultValue) { return mExtras == null ? defaultValue : mExtras.getLong(name, defaultValue); }
    public float getFloatExtra(String name, float defaultValue) { return mExtras == null ? defaultValue : mExtras.getFloat(name, defaultValue); }
    public double getDoubleExtra(String name, double defaultValue) { return mExtras == null ? defaultValue : mExtras.getDouble(name, defaultValue); }
    public String getStringExtra(String name) { return mExtras == null ? null : mExtras.getString(name); }
    public CharSequence getCharSequenceExtra(String name) { return mExtras == null ? null : mExtras.getCharSequence(name); }
    public <T extends Parcelable> T getParcelableExtra(String name) { return mExtras == null ? null : mExtras.<T>getParcelable(name); }
    public <T> T getParcelableExtra(String name, Class<T> clazz) { return mExtras == null ? null : mExtras.getParcelable(name, clazz); }
    public Parcelable[] getParcelableArrayExtra(String name) { return mExtras == null ? null : mExtras.getParcelableArray(name); }
    public <T extends Parcelable> ArrayList<T> getParcelableArrayListExtra(String name) { return mExtras == null ? null : mExtras.<T>getParcelableArrayList(name); }
    public Serializable getSerializableExtra(String name) { return mExtras == null ? null : mExtras.getSerializable(name); }
    public <T extends Serializable> T getSerializableExtra(String name, Class<T> clazz) { return mExtras == null ? null : mExtras.getSerializable(name, clazz); }
    public ArrayList<Integer> getIntegerArrayListExtra(String name) { return mExtras == null ? null : mExtras.getIntegerArrayList(name); }
    public ArrayList<String> getStringArrayListExtra(String name) { return mExtras == null ? null : mExtras.getStringArrayList(name); }
    public ArrayList<CharSequence> getCharSequenceArrayListExtra(String name) { return mExtras == null ? null : mExtras.getCharSequenceArrayList(name); }
    public boolean[] getBooleanArrayExtra(String name) { return mExtras == null ? null : mExtras.getBooleanArray(name); }
    public byte[] getByteArrayExtra(String name) { return mExtras == null ? null : mExtras.getByteArray(name); }
    public short[] getShortArrayExtra(String name) { return mExtras == null ? null : mExtras.getShortArray(name); }
    public char[] getCharArrayExtra(String name) { return mExtras == null ? null : mExtras.getCharArray(name); }
    public int[] getIntArrayExtra(String name) { return mExtras == null ? null : mExtras.getIntArray(name); }
    public long[] getLongArrayExtra(String name) { return mExtras == null ? null : mExtras.getLongArray(name); }
    public float[] getFloatArrayExtra(String name) { return mExtras == null ? null : mExtras.getFloatArray(name); }
    public double[] getDoubleArrayExtra(String name) { return mExtras == null ? null : mExtras.getDoubleArray(name); }
    public String[] getStringArrayExtra(String name) { return mExtras == null ? null : mExtras.getStringArray(name); }
    public CharSequence[] getCharSequenceArrayExtra(String name) { return mExtras == null ? null : mExtras.getCharSequenceArray(name); }
    public Bundle getBundleExtra(String name) { return mExtras == null ? null : mExtras.getBundle(name); }
    public Bundle getExtras() { return mExtras != null ? new Bundle(mExtras) : null; }

    public int getFlags() { return mFlags; }
    public String getPackage() { return mPackage; }
    public ComponentName getComponent() { return mComponent; }
    public android.graphics.Rect getSourceBounds() { return mSourceBounds; }
    public String getIdentifier() { return mIdentifier; }

    public ComponentName resolveActivity(android.content.pm.PackageManager pm) {
        if (mComponent != null) return mComponent;
        android.content.pm.ResolveInfo info = pm.resolveActivity(this, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if (info != null && info.activityInfo != null) return new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
        return null;
    }

    public android.content.pm.ActivityInfo resolveActivityInfo(android.content.pm.PackageManager pm, int flags) {
        android.content.pm.ResolveInfo info = pm.resolveActivity(this, flags);
        return info != null ? info.activityInfo : null;
    }

    public Intent setAction(String action) {
        mAction = action != null ? action.intern() : null;
        return this;
    }

    public Intent setData(Uri data) {
        mData = data;
        mType = null;
        return this;
    }

    public Intent setDataAndNormalize(Uri data) { return setData(data.normalizeScheme()); }

    public Intent setType(String type) {
        mData = null;
        mType = type;
        return this;
    }

    public Intent setTypeAndNormalize(String type) { return setType(normalizeMimeType(type)); }

    public Intent setDataAndType(Uri data, String type) {
        mData = data;
        mType = type;
        return this;
    }

    public Intent setDataAndTypeAndNormalize(Uri data, String type) { return setDataAndType(data.normalizeScheme(), normalizeMimeType(type)); }

    public static String normalizeMimeType(String type) {
        if (type == null) return null;
        type = type.trim().toLowerCase(Locale.ROOT);
        final int semicolonIndex = type.indexOf(';');
        if (semicolonIndex != -1) type = type.substring(0, semicolonIndex);
        return type;
    }

    public Intent setIdentifier(String identifier) {
        mIdentifier = identifier;
        return this;
    }

    public Intent addCategory(String category) {
        if (mCategories == null) mCategories = new HashSet<String>();
        mCategories.add(category.intern());
        return this;
    }

    public void removeCategory(String category) {
        if (mCategories != null) {
            mCategories.remove(category);
            if (mCategories.size() == 0) mCategories = null;
        }
    }

    public void setSelector(Intent selector) { mSelector = selector; }
    public void setClipData(ClipData clip) { mClipData = clip; }

    private Bundle extras() {
        if (mExtras == null) mExtras = new Bundle();
        return mExtras;
    }

    public Intent putExtra(String name, boolean value) { extras().putBoolean(name, value); return this; }
    public Intent putExtra(String name, byte value) { extras().putByte(name, value); return this; }
    public Intent putExtra(String name, char value) { extras().putChar(name, value); return this; }
    public Intent putExtra(String name, short value) { extras().putShort(name, value); return this; }
    public Intent putExtra(String name, int value) { extras().putInt(name, value); return this; }
    public Intent putExtra(String name, long value) { extras().putLong(name, value); return this; }
    public Intent putExtra(String name, float value) { extras().putFloat(name, value); return this; }
    public Intent putExtra(String name, double value) { extras().putDouble(name, value); return this; }
    public Intent putExtra(String name, String value) { extras().putString(name, value); return this; }
    public Intent putExtra(String name, CharSequence value) { extras().putCharSequence(name, value); return this; }
    public Intent putExtra(String name, Parcelable value) { extras().putParcelable(name, value); return this; }
    public Intent putExtra(String name, Parcelable[] value) { extras().putParcelableArray(name, value); return this; }
    public Intent putParcelableArrayListExtra(String name, ArrayList<? extends Parcelable> value) { extras().putParcelableArrayList(name, value); return this; }
    public Intent putIntegerArrayListExtra(String name, ArrayList<Integer> value) { extras().putIntegerArrayList(name, value); return this; }
    public Intent putStringArrayListExtra(String name, ArrayList<String> value) { extras().putStringArrayList(name, value); return this; }
    public Intent putCharSequenceArrayListExtra(String name, ArrayList<CharSequence> value) { extras().putCharSequenceArrayList(name, value); return this; }
    public Intent putExtra(String name, Serializable value) { extras().putSerializable(name, value); return this; }
    public Intent putExtra(String name, boolean[] value) { extras().putBooleanArray(name, value); return this; }
    public Intent putExtra(String name, byte[] value) { extras().putByteArray(name, value); return this; }
    public Intent putExtra(String name, short[] value) { extras().putShortArray(name, value); return this; }
    public Intent putExtra(String name, char[] value) { extras().putCharArray(name, value); return this; }
    public Intent putExtra(String name, int[] value) { extras().putIntArray(name, value); return this; }
    public Intent putExtra(String name, long[] value) { extras().putLongArray(name, value); return this; }
    public Intent putExtra(String name, float[] value) { extras().putFloatArray(name, value); return this; }
    public Intent putExtra(String name, double[] value) { extras().putDoubleArray(name, value); return this; }
    public Intent putExtra(String name, String[] value) { extras().putStringArray(name, value); return this; }
    public Intent putExtra(String name, CharSequence[] value) { extras().putCharSequenceArray(name, value); return this; }
    public Intent putExtra(String name, Bundle value) { extras().putBundle(name, value); return this; }

    public Intent putExtras(Intent src) {
        if (src.mExtras != null) extras().putAll(src.mExtras);
        return this;
    }

    public Intent putExtras(Bundle extras) {
        if (extras != null) extras().putAll(extras);
        return this;
    }

    public Intent replaceExtras(Intent src) {
        mExtras = src.mExtras != null ? new Bundle(src.mExtras) : null;
        return this;
    }

    public Intent replaceExtras(Bundle extras) {
        mExtras = extras != null ? new Bundle(extras) : null;
        return this;
    }

    public void removeExtra(String name) {
        if (mExtras != null) {
            mExtras.remove(name);
            if (mExtras.size() == 0) mExtras = null;
        }
    }

    public Intent setFlags(int flags) {
        mFlags = flags;
        return this;
    }

    public Intent addFlags(int flags) {
        mFlags |= flags;
        return this;
    }

    public void removeFlags(int flags) { mFlags &= ~flags; }

    public Intent setPackage(String packageName) {
        mPackage = packageName;
        return this;
    }

    public Intent setComponent(ComponentName component) {
        mComponent = component;
        return this;
    }

    public Intent setClassName(Context packageContext, String className) {
        mComponent = new ComponentName(packageContext, className);
        return this;
    }

    public Intent setClassName(String packageName, String className) {
        mComponent = new ComponentName(packageName, className);
        return this;
    }

    public Intent setClass(Context packageContext, Class<?> cls) {
        mComponent = new ComponentName(packageContext, cls);
        return this;
    }

    public void setSourceBounds(android.graphics.Rect r) { mSourceBounds = r != null ? new android.graphics.Rect(r) : null; }

    public int fillIn(Intent other, int flags) {
        int changes = 0;
        if (other.mAction != null && (mAction == null || (flags & FILL_IN_ACTION) != 0)) {
            mAction = other.mAction;
            changes |= FILL_IN_ACTION;
        }
        if ((other.mData != null || other.mType != null) && ((mData == null && mType == null) || (flags & FILL_IN_DATA) != 0)) {
            mData = other.mData;
            mType = other.mType;
            changes |= FILL_IN_DATA;
        }
        if (other.mCategories != null && (mCategories == null || (flags & FILL_IN_CATEGORIES) != 0)) {
            mCategories = new HashSet<String>(other.mCategories);
            changes |= FILL_IN_CATEGORIES;
        }
        if (other.mPackage != null && (mPackage == null || (flags & FILL_IN_PACKAGE) != 0)) {
            mPackage = other.mPackage;
            changes |= FILL_IN_PACKAGE;
        }
        if (other.mComponent != null && (flags & FILL_IN_COMPONENT) != 0) {
            mComponent = other.mComponent;
            changes |= FILL_IN_COMPONENT;
        }
        mFlags |= other.mFlags;
        if (other.mExtras != null) {
            if (mExtras == null) mExtras = new Bundle(other.mExtras);
            else {
                Bundle b = new Bundle(other.mExtras);
                b.putAll(mExtras);
                mExtras = b;
            }
        }
        return changes;
    }

    public boolean filterEquals(Intent other) {
        if (other == null) return false;
        return java.util.Objects.equals(mAction, other.mAction) && java.util.Objects.equals(mData, other.mData)
                && java.util.Objects.equals(mType, other.mType) && java.util.Objects.equals(mPackage, other.mPackage)
                && java.util.Objects.equals(mComponent, other.mComponent) && java.util.Objects.equals(mCategories, other.mCategories);
    }

    public int filterHashCode() {
        int code = 0;
        if (mAction != null) code += mAction.hashCode();
        if (mData != null) code += mData.hashCode();
        if (mType != null) code += mType.hashCode();
        if (mPackage != null) code += mPackage.hashCode();
        if (mComponent != null) code += mComponent.hashCode();
        if (mCategories != null) code += mCategories.hashCode();
        return code;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder(128);
        b.append("Intent { ");
        boolean first = true;
        if (mAction != null) {
            b.append("act=").append(mAction);
            first = false;
        }
        if (mCategories != null) {
            b.append(first ? "" : " ").append("cat=").append(mCategories);
            first = false;
        }
        if (mData != null) {
            b.append(first ? "" : " ").append("dat=").append(mData);
            first = false;
        }
        if (mType != null) {
            b.append(first ? "" : " ").append("typ=").append(mType);
            first = false;
        }
        if (mFlags != 0) {
            b.append(first ? "" : " ").append("flg=0x").append(Integer.toHexString(mFlags));
            first = false;
        }
        if (mPackage != null) {
            b.append(first ? "" : " ").append("pkg=").append(mPackage);
            first = false;
        }
        if (mComponent != null) {
            b.append(first ? "" : " ").append("cmp=").append(mComponent.flattenToShortString());
            first = false;
        }
        if (mExtras != null) b.append(first ? "" : " ").append("(has extras)");
        return b.append(" }").toString();
    }

    public String toUri(int flags) {
        StringBuilder uri = new StringBuilder(128);
        if (mData != null) uri.append(mData.toString());
        uri.append("#Intent;");
        if (mAction != null) uri.append("action=").append(Uri.encode(mAction)).append(';');
        if (mCategories != null) for (String c : mCategories) uri.append("category=").append(Uri.encode(c)).append(';');
        if (mType != null) uri.append("type=").append(Uri.encode(mType, "/")).append(';');
        if (mFlags != 0) uri.append("launchFlags=0x").append(Integer.toHexString(mFlags)).append(';');
        if (mPackage != null) uri.append("package=").append(Uri.encode(mPackage)).append(';');
        if (mComponent != null) uri.append("component=").append(Uri.encode(mComponent.flattenToShortString(), "/")).append(';');
        uri.append("end");
        return uri.toString();
    }

    @Deprecated
    public String toURI() { return toUri(0); }

    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { out.writeValue(new Intent(this)); }
    public void readFromParcel(Parcel in) {
        Intent o = (Intent) in.readValue(null);
        if (o == null) return;
        mAction = o.mAction;
        mData = o.mData;
        mType = o.mType;
        mFlags = o.mFlags;
        mPackage = o.mPackage;
        mComponent = o.mComponent;
        mCategories = o.mCategories;
        mExtras = o.mExtras;
    }

    public static final Parcelable.Creator<Intent> CREATOR = new Parcelable.Creator<Intent>() {
        public Intent createFromParcel(Parcel in) { return (Intent) in.readValue(null); }
        public Intent[] newArray(int size) { return new Intent[size]; }
    };

    public static final class ShortcutIconResource implements Parcelable {
        public String packageName;
        public String resourceName;

        public static ShortcutIconResource fromContext(Context context, int resourceId) {
            ShortcutIconResource icon = new ShortcutIconResource();
            icon.packageName = context.getPackageName();
            icon.resourceName = context.getResources().getResourceName(resourceId);
            return icon;
        }

        public int describeContents() { return 0; }
        public void writeToParcel(Parcel dest, int flags) { dest.writeString(packageName); dest.writeString(resourceName); }

        public static final Parcelable.Creator<ShortcutIconResource> CREATOR = new Parcelable.Creator<ShortcutIconResource>() {
            public ShortcutIconResource createFromParcel(Parcel source) {
                ShortcutIconResource icon = new ShortcutIconResource();
                icon.packageName = source.readString();
                icon.resourceName = source.readString();
                return icon;
            }
            public ShortcutIconResource[] newArray(int size) { return new ShortcutIconResource[size]; }
        };
    }

    public static final class FilterComparison {
        private final Intent mIntent;
        private final int mHashCode;

        public FilterComparison(Intent intent) {
            mIntent = intent;
            mHashCode = intent.filterHashCode();
        }

        public Intent getIntent() { return mIntent; }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof FilterComparison && mIntent.filterEquals(((FilterComparison) obj).mIntent);
        }

        @Override
        public int hashCode() { return mHashCode; }
    }
}
