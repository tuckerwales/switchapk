package android.app;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.net.Uri;
import android.util.Log;

/**
 * framework-internal. The browser other apps would provide: ACTION_VIEW on an http or https URL that nothing in the
 * app handles (Custom Tabs, AppAuth logins, "open in browser") opens the platform browser (the Switch's web
 * applet; a script on the host). The app is paused and stopped while it is open, as for another app's activity.
 * When the browser reaches the app's redirect URL it closes and that URL comes back as a BROWSABLE ACTION_VIEW
 * intent (OAuth redirects); otherwise the app simply resumes.
 */
final class SystemBrowser {
    private static final String TAG = "SystemBrowser";
    static final String PACKAGE = "com.android.browser";
    static final String ACTIVITY = "com.android.browser.BrowserActivity";

    private SystemBrowser() {
    }

    /** Whether intent is one the system browser takes: a web URL, for no package or the browser's. */
    static boolean handles(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return false;
        String scheme = intent.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
        String pkg = intent.getPackage();
        if (intent.getComponent() != null) pkg = intent.getComponent().getPackageName();
        return pkg == null || PACKAGE.equals(pkg);
    }

    /**
     * The URL prefix that ends the browser: the request's redirect_uri when the app handles it (OAuth), else the
     * first non-web scheme among the app's BROWSABLE ACTION_VIEW filters ("scheme:"), else null.
     */
    static String callbackFor(Uri url) {
        String redirect = null;
        try {
            redirect = url.getQueryParameter("redirect_uri");
        } catch (RuntimeException ignored) {
            // not a hierarchical URI
        }
        if (redirect != null && redirect.length() > 0 && appHandles(Uri.parse(redirect))) return redirect;
        for (int i = 0; i < ActivityThread.sActivities.size(); i++) {
            ActivityThread.ParsedActivity pa = ActivityThread.sActivities.get(i);
            for (int j = 0; j < pa.filters.size(); j++) {
                IntentFilter f = pa.filters.get(j);
                if (!f.hasAction(Intent.ACTION_VIEW) || !f.hasCategory(Intent.CATEGORY_BROWSABLE)) continue;
                for (int k = 0; k < f.countDataSchemes(); k++) {
                    String s = f.getDataScheme(k);
                    if (!"http".equals(s) && !"https".equals(s)) return s + ":";
                }
            }
        }
        return null;
    }

    static boolean appHandles(Uri uri) {
        return redirectIntent(uri.toString()) != null;
    }

    /** The intent a callback URL comes back as, or null when nothing in the app takes it. */
    static Intent redirectIntent(String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.addCategory(Intent.CATEGORY_BROWSABLE);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        for (int k = 0; k < ActivityThread.sActivities.size(); k++) {
            if (ActivityThread.matches(ActivityThread.sActivities.get(k), i)) return i;
        }
        return null;
    }

    interface Listener {
        /** On a browser thread: the callback URL the browser stopped at, or null when it was closed. */
        void closed(String callbackUrl, boolean available);
    }

    /** Opens url on a new thread (the platform call blocks until the browser closes). */
    static void open(final String url, final String callback, final Listener listener) {
        Log.i(TAG, "opening " + url + (callback != null ? " (returns at " + callback + ")" : ""));
        Thread t = new Thread(new Runnable() {
            public void run() {
                String[] out = new String[1];
                int r = nOpenUrl(url, callback, out);
                if (r < 0) Log.w(TAG, "no browser available for " + url);
                listener.closed(r > 0 ? out[0] : null, r >= 0);
            }
        }, "browser");
        t.setDaemon(true);
        t.start();
    }

    // ---- what PackageManager reports, so apps that look for a browser find one ----

    static ResolveInfo resolveInfo() {
        ResolveInfo ri = new ResolveInfo();
        ri.activityInfo = activityInfo();
        IntentFilter f = new IntentFilter(Intent.ACTION_VIEW);
        f.addCategory(Intent.CATEGORY_DEFAULT);
        f.addCategory(Intent.CATEGORY_BROWSABLE);
        f.addDataScheme("http");
        f.addDataScheme("https");
        ri.filter = f;
        ri.match = IntentFilter.MATCH_CATEGORY_SCHEME;
        ri.isDefault = true;
        return ri;
    }

    static ActivityInfo activityInfo() {
        ActivityInfo ai = new ActivityInfo();
        ai.name = ACTIVITY;
        ai.packageName = PACKAGE;
        ai.exported = true;
        ai.enabled = true;
        ai.applicationInfo = applicationInfo();
        return ai;
    }

    static ApplicationInfo applicationInfo() {
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = PACKAGE;
        ai.processName = PACKAGE;
        ai.enabled = true;
        ai.flags = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        ai.targetSdkVersion = 29;
        return ai;
    }

    static PackageInfo packageInfo() {
        PackageInfo pi = new PackageInfo();
        pi.packageName = PACKAGE;
        pi.versionName = "1.0";
        pi.versionCode = 1;
        pi.applicationInfo = applicationInfo();
        // apps that choose browsers by signer (AppAuth) need a certificate to look at
        pi.signatures = new Signature[] {new Signature(new byte[] {0x30, 0x03, 0x02, 0x01, 0x00})};
        return pi;
    }

    private static native int nOpenUrl(String url, String callback, String[] out);
}
