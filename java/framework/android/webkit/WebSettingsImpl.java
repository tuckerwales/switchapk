package android.webkit;

/**
 * Framework-internal: the settings object of one WebView. Stores every setting with the defaults of the
 * Chromium WebView for an app targeting API 29. Thread-safe.
 */
final class WebSettingsImpl extends WebSettings {
    static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; Switch) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Version/4.0 Chrome/120.0.0.0 Mobile Safari/537.36";

    private WebView mWebView;

    private boolean mSupportZoom = true;
    private boolean mMediaPlaybackRequiresUserGesture = true;
    private boolean mBuiltInZoomControls;
    private boolean mDisplayZoomControls = true;
    private boolean mAllowFileAccess = true;
    private boolean mAllowContentAccess = true;
    private boolean mLoadWithOverviewMode;
    private boolean mEnableSmoothTransition;
    private boolean mSaveFormData;
    private boolean mSavePassword;
    private int mTextZoom = 100;
    private ZoomDensity mDefaultZoom = ZoomDensity.MEDIUM;
    private boolean mLightTouchEnabled;
    private boolean mUseWideViewPort;
    private boolean mSupportMultipleWindows;
    private LayoutAlgorithm mLayoutAlgorithm = LayoutAlgorithm.NARROW_COLUMNS;
    private String mStandardFontFamily = "sans-serif";
    private String mFixedFontFamily = "monospace";
    private String mSansSerifFontFamily = "sans-serif";
    private String mSerifFontFamily = "serif";
    private String mCursiveFontFamily = "cursive";
    private String mFantasyFontFamily = "fantasy";
    private int mMinimumFontSize = 8;
    private int mMinimumLogicalFontSize = 8;
    private int mDefaultFontSize = 16;
    private int mDefaultFixedFontSize = 13;
    private boolean mLoadsImagesAutomatically = true;
    private boolean mBlockNetworkImage;
    private boolean mBlockNetworkLoads;
    private boolean mJavaScriptEnabled;
    private boolean mAllowUniversalAccessFromFileURLs;
    private boolean mAllowFileAccessFromFileURLs;
    private PluginState mPluginState = PluginState.OFF;
    private String mDatabasePath = "";
    private String mGeolocationDatabasePath = "";
    private boolean mDatabaseEnabled;
    private boolean mDomStorageEnabled;
    private boolean mGeolocationEnabled = true;
    private boolean mJavaScriptCanOpenWindowsAutomatically;
    private String mDefaultTextEncodingName = "UTF-8";
    private String mUserAgent = DEFAULT_USER_AGENT;
    private boolean mNeedInitialFocus = true;
    private RenderPriority mRenderPriority = RenderPriority.NORMAL;
    private int mCacheMode = LOAD_DEFAULT;
    private int mMixedContentMode = MIXED_CONTENT_NEVER_ALLOW;
    private boolean mOffscreenPreRaster;
    private boolean mSafeBrowsingEnabled = true;
    private int mForceDark = FORCE_DARK_AUTO;
    private boolean mAlgorithmicDarkeningAllowed;
    private int mDisabledActionModeMenuItems = MENU_ITEM_NONE;

    WebSettingsImpl() {
    }

    /** Text size setters notify this WebView through settingsChanged(). */
    void attach(WebView view) {
        synchronized (this) {
            mWebView = view;
        }
    }

    // Must be called without holding the lock.
    private void changed() {
        WebView v;
        synchronized (this) {
            v = mWebView;
        }
        if (v != null) v.settingsChanged();
    }

    // As Chromium's AwSettings.clipFontSize.
    private static int clipFontSize(int size) {
        if (size < 1) return 1;
        if (size > 72) return 72;
        return size;
    }

    /** Framework-internal: whether network loads should be served from the cache only. */
    synchronized boolean isCacheOnly() {
        return mCacheMode == LOAD_CACHE_ONLY;
    }

    synchronized boolean getNeedInitialFocus() {
        return mNeedInitialFocus;
    }

    synchronized boolean getGeolocationEnabled() {
        return mGeolocationEnabled;
    }

    @Override public synchronized void setSupportZoom(boolean support) { mSupportZoom = support; }
    @Override public synchronized boolean supportZoom() { return mSupportZoom; }

    @Override
    public synchronized void setMediaPlaybackRequiresUserGesture(boolean require) {
        mMediaPlaybackRequiresUserGesture = require;
    }

    @Override
    public synchronized boolean getMediaPlaybackRequiresUserGesture() {
        return mMediaPlaybackRequiresUserGesture;
    }

    @Override public synchronized void setBuiltInZoomControls(boolean enabled) { mBuiltInZoomControls = enabled; }
    @Override public synchronized boolean getBuiltInZoomControls() { return mBuiltInZoomControls; }
    @Override public synchronized void setDisplayZoomControls(boolean enabled) { mDisplayZoomControls = enabled; }
    @Override public synchronized boolean getDisplayZoomControls() { return mDisplayZoomControls; }
    @Override public synchronized void setAllowFileAccess(boolean allow) { mAllowFileAccess = allow; }
    @Override public synchronized boolean getAllowFileAccess() { return mAllowFileAccess; }
    @Override public synchronized void setAllowContentAccess(boolean allow) { mAllowContentAccess = allow; }
    @Override public synchronized boolean getAllowContentAccess() { return mAllowContentAccess; }
    @Override public synchronized void setLoadWithOverviewMode(boolean overview) { mLoadWithOverviewMode = overview; }
    @Override public synchronized boolean getLoadWithOverviewMode() { return mLoadWithOverviewMode; }
    @Override public synchronized void setEnableSmoothTransition(boolean enable) { mEnableSmoothTransition = enable; }
    @Override public synchronized boolean enableSmoothTransition() { return mEnableSmoothTransition; }
    // Form data and passwords are never saved (as Android O+), but the flags read back.
    @Override public synchronized void setSaveFormData(boolean save) { mSaveFormData = save; }
    @Override public synchronized boolean getSaveFormData() { return mSaveFormData; }
    @Override public synchronized void setSavePassword(boolean save) { mSavePassword = save; }
    @Override public synchronized boolean getSavePassword() { return mSavePassword; }

    @Override
    public void setTextZoom(int textZoom) {
        synchronized (this) {
            if (mTextZoom == textZoom) return;
            mTextZoom = textZoom;
        }
        changed();
    }

    @Override public synchronized int getTextZoom() { return mTextZoom; }

    @Override
    public void setTextSize(TextSize t) {
        // Not synchronized like the base class: setTextZoom notifies the WebView outside the lock.
        setTextZoom(t.value);
    }

    @Override public synchronized void setDefaultZoom(ZoomDensity zoom) { mDefaultZoom = zoom; }
    @Override public synchronized ZoomDensity getDefaultZoom() { return mDefaultZoom; }
    @Override public synchronized void setLightTouchEnabled(boolean enabled) { mLightTouchEnabled = enabled; }
    @Override public synchronized boolean getLightTouchEnabled() { return mLightTouchEnabled; }
    @Override public synchronized void setUseWideViewPort(boolean use) { mUseWideViewPort = use; }
    @Override public synchronized boolean getUseWideViewPort() { return mUseWideViewPort; }
    @Override public synchronized void setSupportMultipleWindows(boolean support) { mSupportMultipleWindows = support; }
    @Override public synchronized boolean supportMultipleWindows() { return mSupportMultipleWindows; }
    @Override public synchronized void setLayoutAlgorithm(LayoutAlgorithm l) { if (l != null) mLayoutAlgorithm = l; }
    @Override public synchronized LayoutAlgorithm getLayoutAlgorithm() { return mLayoutAlgorithm; }

    @Override
    public synchronized void setStandardFontFamily(String font) {
        if (font != null) mStandardFontFamily = font;
    }

    @Override public synchronized String getStandardFontFamily() { return mStandardFontFamily; }
    @Override public synchronized void setFixedFontFamily(String font) { if (font != null) mFixedFontFamily = font; }
    @Override public synchronized String getFixedFontFamily() { return mFixedFontFamily; }

    @Override
    public synchronized void setSansSerifFontFamily(String font) {
        if (font != null) mSansSerifFontFamily = font;
    }

    @Override public synchronized String getSansSerifFontFamily() { return mSansSerifFontFamily; }
    @Override public synchronized void setSerifFontFamily(String font) { if (font != null) mSerifFontFamily = font; }
    @Override public synchronized String getSerifFontFamily() { return mSerifFontFamily; }

    @Override
    public synchronized void setCursiveFontFamily(String font) {
        if (font != null) mCursiveFontFamily = font;
    }

    @Override public synchronized String getCursiveFontFamily() { return mCursiveFontFamily; }

    @Override
    public synchronized void setFantasyFontFamily(String font) {
        if (font != null) mFantasyFontFamily = font;
    }

    @Override public synchronized String getFantasyFontFamily() { return mFantasyFontFamily; }

    @Override
    public void setMinimumFontSize(int size) {
        synchronized (this) {
            mMinimumFontSize = clipFontSize(size);
        }
        changed();
    }

    @Override public synchronized int getMinimumFontSize() { return mMinimumFontSize; }

    @Override
    public synchronized void setMinimumLogicalFontSize(int size) {
        mMinimumLogicalFontSize = clipFontSize(size);
    }

    @Override public synchronized int getMinimumLogicalFontSize() { return mMinimumLogicalFontSize; }

    @Override
    public void setDefaultFontSize(int size) {
        synchronized (this) {
            size = clipFontSize(size);
            if (mDefaultFontSize == size) return;
            mDefaultFontSize = size;
        }
        changed();
    }

    @Override public synchronized int getDefaultFontSize() { return mDefaultFontSize; }

    @Override
    public void setDefaultFixedFontSize(int size) {
        synchronized (this) {
            mDefaultFixedFontSize = clipFontSize(size);
        }
        changed();
    }

    @Override public synchronized int getDefaultFixedFontSize() { return mDefaultFixedFontSize; }
    @Override public synchronized void setLoadsImagesAutomatically(boolean flag) { mLoadsImagesAutomatically = flag; }
    @Override public synchronized boolean getLoadsImagesAutomatically() { return mLoadsImagesAutomatically; }
    @Override public synchronized void setBlockNetworkImage(boolean flag) { mBlockNetworkImage = flag; }
    @Override public synchronized boolean getBlockNetworkImage() { return mBlockNetworkImage; }
    @Override public synchronized void setBlockNetworkLoads(boolean flag) { mBlockNetworkLoads = flag; }
    @Override public synchronized boolean getBlockNetworkLoads() { return mBlockNetworkLoads; }
    @Override public synchronized void setJavaScriptEnabled(boolean flag) { mJavaScriptEnabled = flag; }

    @Override
    public synchronized void setAllowUniversalAccessFromFileURLs(boolean flag) {
        mAllowUniversalAccessFromFileURLs = flag;
    }

    @Override
    public synchronized void setAllowFileAccessFromFileURLs(boolean flag) {
        mAllowFileAccessFromFileURLs = flag;
    }

    @Override public synchronized void setPluginState(PluginState state) { if (state != null) mPluginState = state; }
    @Override public synchronized void setDatabasePath(String databasePath) { mDatabasePath = databasePath; }

    @Override
    public synchronized void setGeolocationDatabasePath(String databasePath) {
        mGeolocationDatabasePath = databasePath;
    }

    @Override public synchronized void setDatabaseEnabled(boolean flag) { mDatabaseEnabled = flag; }
    @Override public synchronized void setDomStorageEnabled(boolean flag) { mDomStorageEnabled = flag; }
    @Override public synchronized boolean getDomStorageEnabled() { return mDomStorageEnabled; }
    @Override public synchronized String getDatabasePath() { return mDatabasePath; }
    @Override public synchronized boolean getDatabaseEnabled() { return mDatabaseEnabled; }
    @Override public synchronized void setGeolocationEnabled(boolean flag) { mGeolocationEnabled = flag; }
    @Override public synchronized boolean getJavaScriptEnabled() { return mJavaScriptEnabled; }

    @Override
    public synchronized boolean getAllowUniversalAccessFromFileURLs() {
        return mAllowUniversalAccessFromFileURLs;
    }

    @Override
    public synchronized boolean getAllowFileAccessFromFileURLs() {
        return mAllowFileAccessFromFileURLs;
    }

    @Override public synchronized PluginState getPluginState() { return mPluginState; }

    @Override
    public synchronized void setJavaScriptCanOpenWindowsAutomatically(boolean flag) {
        mJavaScriptCanOpenWindowsAutomatically = flag;
    }

    @Override
    public synchronized boolean getJavaScriptCanOpenWindowsAutomatically() {
        return mJavaScriptCanOpenWindowsAutomatically;
    }

    @Override
    public synchronized void setDefaultTextEncodingName(String encoding) {
        if (encoding != null && encoding.length() > 0) mDefaultTextEncodingName = encoding;
    }

    @Override public synchronized String getDefaultTextEncodingName() { return mDefaultTextEncodingName; }

    @Override
    public synchronized void setUserAgentString(String ua) {
        // As Chromium: null or empty restores the default.
        mUserAgent = ua == null || ua.length() == 0 ? DEFAULT_USER_AGENT : ua;
    }

    @Override public synchronized String getUserAgentString() { return mUserAgent; }
    @Override public synchronized void setNeedInitialFocus(boolean flag) { mNeedInitialFocus = flag; }

    @Override
    public synchronized void setRenderPriority(RenderPriority priority) {
        if (priority != null) mRenderPriority = priority;
    }

    @Override public synchronized void setCacheMode(int mode) { mCacheMode = mode; }
    @Override public synchronized int getCacheMode() { return mCacheMode; }
    @Override public synchronized void setMixedContentMode(int mode) { mMixedContentMode = mode; }
    @Override public synchronized int getMixedContentMode() { return mMixedContentMode; }
    @Override public synchronized void setOffscreenPreRaster(boolean enabled) { mOffscreenPreRaster = enabled; }
    @Override public synchronized boolean getOffscreenPreRaster() { return mOffscreenPreRaster; }
    @Override public synchronized void setSafeBrowsingEnabled(boolean enabled) { mSafeBrowsingEnabled = enabled; }
    @Override public synchronized boolean getSafeBrowsingEnabled() { return mSafeBrowsingEnabled; }
    @Override public synchronized void setForceDark(int forceDark) { mForceDark = forceDark; }
    @Override public synchronized int getForceDark() { return mForceDark; }

    @Override
    public synchronized void setAlgorithmicDarkeningAllowed(boolean allow) {
        mAlgorithmicDarkeningAllowed = allow;
    }

    @Override public synchronized boolean isAlgorithmicDarkeningAllowed() { return mAlgorithmicDarkeningAllowed; }

    @Override
    public synchronized void setDisabledActionModeMenuItems(int menuItems) {
        mDisabledActionModeMenuItems = menuItems;
    }

    @Override public synchronized int getDisabledActionModeMenuItems() { return mDisabledActionModeMenuItems; }
}
