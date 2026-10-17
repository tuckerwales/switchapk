package com.example.web;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.List;

/**
 * WebView without a browser engine: an asset page with a link (tapped by the script), back navigation, an app
 * scheme link the WebViewClient takes, loadDataWithBaseURL, cookies, URLUtil and MimeTypeMap; then AuthActivity
 * runs an AppAuth-style login through the system browser twice (redirect, then cancel).
 */
public class MainActivity extends Activity {
    static final String TAG = "WEB";
    private WebView web;
    private int finished;
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        setContentView(web);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                Log.i(TAG, "override " + u + " gesture=" + request.hasGesture());
                return "com.example.web".equals(u.getScheme());
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Log.i(TAG, "started " + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.i(TAG, "finished " + url + " title=" + view.getTitle() + " back=" + view.canGoBack());
                step(url);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                Log.i(TAG, "title " + title);
            }
        });
        web.getSettings().setJavaScriptEnabled(true);
        Log.i(TAG, "user agent has Android: " + web.getSettings().getUserAgentString().contains("Android"));
        checkApis();
        web.loadUrl("file:///android_asset/index.html");
    }

    private void checkApis() {
        CookieManager cm = CookieManager.getInstance();
        cm.setCookie("https://example.com/", "a=1; Path=/");
        cm.setCookie("https://example.com/", "b=2; Path=/docs");
        cm.setCookie("https://example.com/", "c=3; Secure");
        Log.i(TAG, "cookies root=" + cm.getCookie("https://example.com/") + " docs=" + cm.getCookie("https://example.com/docs/x")
                + " http=" + cm.getCookie("http://example.com/"));
        Log.i(TAG, "urlutil network=" + URLUtil.isNetworkUrl("https://a.b/") + " asset=" + URLUtil.isAssetUrl("file:///android_asset/x")
                + " name=" + URLUtil.guessFileName("https://a.b/files/report.pdf", null, "application/pdf"));
        Log.i(TAG, "mime png=" + MimeTypeMap.getSingleton().getMimeTypeFromExtension("png") + " ext="
                + MimeTypeMap.getFileExtensionFromUrl("https://a.b/c/page.html?q=1"));
        List<ResolveInfo> browsers = getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com")), 0);
        Log.i(TAG, "browsers " + browsers.size() + " " + (browsers.isEmpty() ? "" : browsers.get(0).activityInfo.packageName));
    }

    private void step(String url) {
        finished++;
        if (url.endsWith("page2.html") && finished == 2) {
            handler.postDelayed(new Runnable() {
                public void run() {
                    web.goBack();
                }
            }, 300);
        } else if (url.endsWith("index.html") && finished == 3) {
            // back on the first page: the app-scheme link is taken by shouldOverrideUrlLoading
            web.loadDataWithBaseURL("file:///android_asset/", "<html><head><title>Inline</title></head><body>"
                    + "<p>Inline page, base on assets.</p></body></html>", "text/html", "UTF-8", null);
        } else if (finished == 4) {
            Log.i(TAG, "webview done");
            handler.postDelayed(new Runnable() {
                public void run() {
                    startActivity(new Intent(MainActivity.this, AuthActivity.class));
                }
            }, 500);
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
