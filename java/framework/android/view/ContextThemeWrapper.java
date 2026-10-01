package android.view;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;

public class ContextThemeWrapper extends ContextWrapper {
    private int mThemeResource;
    private Resources.Theme mTheme;
    private Resources mResources;
    private Configuration mOverrideConfiguration;

    public ContextThemeWrapper() { super(); }

    public ContextThemeWrapper(Context base, int themeResId) {
        super(base);
        mThemeResource = themeResId;
    }

    public ContextThemeWrapper(Context base, Resources.Theme theme) {
        super(base);
        mTheme = theme;
    }

    @Override
    protected void attachBaseContext(Context newBase) { super.attachBaseContext(newBase); }

    public void applyOverrideConfiguration(Configuration overrideConfiguration) {
        if (mResources != null) throw new IllegalStateException("getResources() has already been called");
        if (mOverrideConfiguration != null) {
            throw new IllegalStateException("Override configuration has already been set");
        }
        mOverrideConfiguration = new Configuration(overrideConfiguration);
    }

    @Override
    public AssetManager getAssets() { return getResources().getAssets(); }

    @Override
    public Resources getResources() {
        if (mResources == null && mOverrideConfiguration != null) {
            Resources base = getBaseContext().getResources();
            mResources = new Resources(base.getAssets(), base.getDisplayMetrics(), mOverrideConfiguration);
        }
        return mResources != null ? mResources : getBaseContext().getResources();
    }

    @Override
    public void setTheme(int resid) {
        if (mThemeResource != resid) {
            mThemeResource = resid;
            initializeTheme();
        }
    }

    public void setTheme(Resources.Theme theme) { mTheme = theme; }

    @Override
    public Resources.Theme getTheme() {
        if (mTheme == null) initializeTheme();
        return mTheme;
    }

    protected void onApplyThemeResource(Resources.Theme theme, int resId, boolean first) {
        theme.applyStyle(resId, true);
    }

    private void initializeTheme() {
        boolean first = mTheme == null;
        if (first) {
            mTheme = getResources().newTheme();
            Context base = getBaseContext();
            if (base != null && base.getTheme() != null) mTheme.setTo(base.getTheme());
        }
        onApplyThemeResource(mTheme, mThemeResource, first);
    }

    private LayoutInflater mInflater;

    @Override
    public Object getSystemService(String name) {
        if (LAYOUT_INFLATER_SERVICE.equals(name)) {
            if (mInflater == null) mInflater = LayoutInflater.from(getBaseContext()).cloneInContext(this);
            return mInflater;
        }
        return getBaseContext().getSystemService(name);
    }
}
