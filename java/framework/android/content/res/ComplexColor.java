package android.content.res;

public abstract class ComplexColor {
    private int mChangingConfigurations;

    public boolean isStateful() { return false; }
    public abstract int getDefaultColor();
    public int getChangingConfigurations() { return mChangingConfigurations; }
    void setBaseChangingConfigurations(int changingConfigurations) { mChangingConfigurations = changingConfigurations; }
    public abstract boolean canApplyTheme();
    public abstract ComplexColor obtainForTheme(Resources.Theme t);
}
