package android.webkit;

public abstract class CookieManager {
    private static CookieManager sInstance;

    public CookieManager() {
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException("doesn't implement Cloneable");
    }

    public static synchronized CookieManager getInstance() {
        if (sInstance == null) sInstance = new CookieManagerImpl();
        return sInstance;
    }

    public abstract void setAcceptCookie(boolean accept);

    public abstract boolean acceptCookie();

    public abstract void setAcceptThirdPartyCookies(WebView webview, boolean accept);

    public abstract boolean acceptThirdPartyCookies(WebView webview);

    public abstract void setCookie(String url, String value);

    public abstract void setCookie(String url, String value, ValueCallback<Boolean> callback);

    public abstract String getCookie(String url);

    @Deprecated
    public abstract void removeSessionCookie();

    public abstract void removeSessionCookies(ValueCallback<Boolean> callback);

    @Deprecated
    public abstract void removeAllCookie();

    public abstract void removeAllCookies(ValueCallback<Boolean> callback);

    public abstract boolean hasCookies();

    @Deprecated
    public abstract void removeExpiredCookie();

    public abstract void flush();

    public static boolean allowFileSchemeCookies() {
        return CookieManagerImpl.allowFileSchemeCookiesImpl();
    }

    @Deprecated
    public static void setAcceptFileSchemeCookies(boolean accept) {
        CookieManagerImpl.setAcceptFileSchemeCookiesImpl(accept);
    }
}
