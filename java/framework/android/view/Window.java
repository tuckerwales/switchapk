package android.view;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager.LayoutParams;

/** Base window. PhoneWindow supplies the decor. Full features are TODO(WS1). */
public class Window {
    public static final int FEATURE_OPTIONS_PANEL = 0;
    public static final int FEATURE_NO_TITLE = 1;
    public static final int FEATURE_PROGRESS = 2;
    public static final int FEATURE_LEFT_ICON = 3;
    public static final int FEATURE_RIGHT_ICON = 4;
    public static final int FEATURE_INDETERMINATE_PROGRESS = 5;
    public static final int FEATURE_CONTEXT_MENU = 6;
    public static final int FEATURE_CUSTOM_TITLE = 7;
    public static final int FEATURE_ACTION_BAR = 8;
    public static final int FEATURE_ACTION_BAR_OVERLAY = 9;
    public static final int FEATURE_ACTION_MODE_OVERLAY = 10;
    public static final int FEATURE_SWIPE_TO_DISMISS = 11;
    public static final int FEATURE_CONTENT_TRANSITIONS = 12;
    public static final int FEATURE_ACTIVITY_TRANSITIONS = 13;

    private final Context mContext;
    private final LayoutParams mAttrs = new LayoutParams();
    private int mFeatures;
    private CharSequence mTitle;
    private int mVolumeStream;
    private Drawable mBackground;

    public Window(Context context) { mContext = context; }

    public final Context getContext() { return mContext; }
    public final LayoutParams getAttributes() { return mAttrs; }
    public WindowManager getWindowManager() { return WindowManagerImpl.getDefault(); }

    public final boolean requestFeature(int featureId) {
        mFeatures |= 1 << featureId;
        return true;
    }

    public final boolean hasFeature(int featureId) { return (mFeatures & (1 << featureId)) != 0; }

    public void setContentView(int layoutResID) {}
    public void setContentView(View view) {}
    public void setContentView(View view, ViewGroup.LayoutParams params) {}
    public void addContentView(View view, ViewGroup.LayoutParams params) {}
    public View getDecorView() { return null; }
    public View peekDecorView() { return null; }

    @SuppressWarnings("unchecked")
    public <T extends View> T findViewById(int id) {
        View decor = getDecorView();
        return decor != null ? (T) decor.findViewById(id) : null;
    }

    public boolean superDispatchKeyEvent(KeyEvent event) { return false; }
    public boolean superDispatchKeyShortcutEvent(KeyEvent event) { return false; }
    public boolean superDispatchTouchEvent(MotionEvent event) { return false; }
    public boolean superDispatchTrackballEvent(MotionEvent event) { return false; }
    public boolean superDispatchGenericMotionEvent(MotionEvent event) { return false; }

    public void setTitle(CharSequence title) { mTitle = title; }
    public void setTitleColor(int textColor) {}
    public CharSequence getTitle() { return mTitle; }
    public void setBackgroundDrawable(Drawable drawable) { mBackground = drawable; }
    public View getCurrentFocus() { return getDecorView(); }
    public boolean isFloating() { return false; }
    public void setVolumeControlStream(int streamType) { mVolumeStream = streamType; }
    public int getVolumeControlStream() { return mVolumeStream; }
    public void onConfigurationChanged(Configuration newConfig) {}
    public void takeKeyEvents(boolean get) {}
    public void setFeatureDrawableResource(int featureId, int resId) {}
    public void setFeatureDrawableUri(int featureId, Uri uri) {}
    public void setFeatureDrawable(int featureId, Drawable drawable) {}
    public void setFeatureDrawableAlpha(int featureId, int alpha) {}
    public void setFeatureInt(int featureId, int value) {}
    public void closePanel(int featureId) {}
    public void closeAllPanels() {}
    public void openPanel(int featureId, KeyEvent event) {}
    public void togglePanel(int featureId, KeyEvent event) {}
    public void invalidatePanelMenu(int featureId) {}
    public boolean performPanelShortcut(int featureId, int keyCode, KeyEvent event, int flags) { return false; }
    public boolean performPanelIdentifierAction(int featureId, int id, int flags) { return false; }
    public boolean performContextMenuIdentifierAction(int id, int flags) { return false; }
    public Bundle saveHierarchyState() { return null; }
    public void restoreHierarchyState(Bundle savedInstanceState) {}
    public int getStatusBarColor() { return 0; }
    public void setStatusBarColor(int color) {}
    public int getNavigationBarColor() { return 0; }
    public void setNavigationBarColor(int color) {}
}
