package android.webkit;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Picture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.http.SslCertificate;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsoluteLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A WebView without a browser engine: pages are fetched (assets, files, data: and content: URLs, http and https
 * through HttpURLConnection with the CookieManager's cookies) and their HTML is shown as styled text (headings,
 * paragraphs, lists, links, emphasis, preformatted text, tables as rows, images from assets, files and data: URLs).
 * There is no JavaScript and no CSS layout. The WebViewClient and WebChromeClient callbacks come in Android's order
 * (shouldOverrideUrlLoading for link clicks and redirects, onPageStarted, progress, title, onPageFinished,
 * onReceivedError), so apps that load help pages, licences, or watch for an OAuth redirect inside a WebView work.
 */
public class WebView extends AbsoluteLayout implements android.view.ViewTreeObserver.OnGlobalFocusChangeListener,
        ViewGroup.OnHierarchyChangeListener {
    private static final String TAG = "WebView";

    public static final int RENDERER_PRIORITY_WAIVED = 0;
    public static final int RENDERER_PRIORITY_BOUND = 1;
    public static final int RENDERER_PRIORITY_IMPORTANT = 2;
    public static final String SCHEME_TEL = "tel:";
    public static final String SCHEME_MAILTO = "mailto:";
    public static final String SCHEME_GEO = "geo:0,0?q=";

    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DROP = Pattern.compile(
            "<!--.*?-->|<(script|style|head|template|svg|iframe|object|noembed)\\b[^>]*>.*?</\\1\\s*>|<!DOCTYPE[^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PRE = Pattern.compile("<pre\\b[^>]*>(.*?)</pre\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern OL = Pattern.compile("<ol\\b[^>]*>(.*?)</ol\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern LI = Pattern.compile("<li\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_REFRESH = Pattern.compile(
            "<meta[^>]+http-equiv\\s*=\\s*[\"']?refresh[\"']?[^>]+content\\s*=\\s*[\"']\\s*\\d+\\s*;\\s*url\\s*=\\s*([^\"'>]+)",
            Pattern.CASE_INSENSITIVE);

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ScrollView mScroller;
    private final TextView mContent;
    private final WebSettingsImpl mSettings = new WebSettingsImpl();
    private final BackForwardListImpl mHistory = new BackForwardListImpl();
    private final Map<String, Object> mJsInterfaces = new HashMap<String, Object>();
    private WebViewClient mClient = new WebViewClient();
    private WebChromeClient mChromeClient;
    private DownloadListener mDownloadListener;
    private FindListener mFindListener;
    private String mUrl;
    private String mOriginalUrl;
    private String mTitle;
    private String mBaseUrl;
    private int mProgress = 100;
    private int mLoadId;
    private boolean mDestroyed;
    private String mLastLink;
    private SslCertificate mCertificate;
    private static boolean sNetworkAvailable = true;

    public WebView(Context context) {
        this(context, null);
    }

    public WebView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.webViewStyle);
    }

    public WebView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public WebView(Context context, AttributeSet attrs, int defStyleAttr, boolean privateBrowsing) {
        this(context, attrs, defStyleAttr, 0);
    }

    public WebView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setWillNotDraw(false);
        setBackgroundColor(Color.WHITE);
        mSettings.attach(this);
        mScroller = new ScrollView(context);
        mScroller.setFillViewport(true);
        mContent = new TextView(context);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        mContent.setPadding(pad, pad, pad, pad);
        mContent.setTextColor(Color.BLACK);
        mContent.setLinkTextColor(0xff1a0dab);
        mContent.setTextIsSelectable(false);
        mContent.setMovementMethod(LinkMovementMethod.getInstance());
        applyTextSize();
        mScroller.addView(mContent, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        super.addView(mScroller, -1, new AbsoluteLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0));
    }

    private void applyTextSize() {
        mContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, mSettings.getDefaultFontSize() * mSettings.getTextZoom() / 100f);
    }

    // ---- loading -----------------------------------------------------------------------------------------------

    public void loadUrl(String url, Map<String, String> additionalHttpHeaders) {
        if (url == null) return;
        if (url.startsWith("javascript:")) return; // no JavaScript
        load(url, additionalHttpHeaders, null, null, true);
    }

    public void loadUrl(String url) {
        loadUrl(url, null);
    }

    public void postUrl(String url, byte[] postData) {
        if (URLUtil.isNetworkUrl(url)) load(url, null, postData, null, true);
        else loadUrl(url);
    }

    public void loadData(String data, String mimeType, String encoding) {
        String body;
        if ("base64".equalsIgnoreCase(encoding)) {
            body = new String(Base64.decode(data, Base64.DEFAULT), java.nio.charset.StandardCharsets.UTF_8);
        } else {
            try {
                body = URLDecoder.decode(data.replace("+", "%2B"), "UTF-8");
            } catch (Exception e) {
                body = data;
            }
        }
        show("data:" + (mimeType != null ? mimeType : "text/html"), null, body, mimeType, true);
    }

    public void loadDataWithBaseURL(String baseUrl, String data, String mimeType, String encoding, String historyUrl) {
        String url = historyUrl != null ? historyUrl : (baseUrl != null ? baseUrl : "about:blank");
        show(url, baseUrl, data != null ? data : "", mimeType, true);
    }

    public void evaluateJavascript(String script, final ValueCallback<String> resultCallback) {
        if (resultCallback == null) return;
        mHandler.post(new Runnable() {
            public void run() {
                resultCallback.onReceiveValue("null");
            }
        });
    }

    public void saveWebArchive(String filename) {
    }

    public void saveWebArchive(String basename, boolean autoname, ValueCallback<String> callback) {
        if (callback != null) callback.onReceiveValue(null);
    }

    public void stopLoading() {
        mLoadId++;
    }

    public void reload() {
        if (mUrl != null) load(mUrl, null, null, null, false);
    }

    /** A navigation the page asked for (link click, redirect): the app's WebViewClient may take it instead. */
    private void navigate(String url, boolean userGesture) {
        url = resolve(url);
        if (url == null) return;
        if (mClient != null && mClient.shouldOverrideUrlLoading(this, new Request(url, userGesture, true))) return;
        String scheme = Uri.parse(url).getScheme();
        if (scheme != null && !isLoadableScheme(scheme)) {
            final String failing = url;
            notifyError(failing, WebViewClient.ERROR_UNSUPPORTED_SCHEME, "net::ERR_UNKNOWN_URL_SCHEME");
            return;
        }
        load(url, null, null, null, true);
    }

    private static boolean isLoadableScheme(String scheme) {
        String s = scheme.toLowerCase(Locale.US);
        return s.equals("http") || s.equals("https") || s.equals("file") || s.equals("data") || s.equals("about")
                || s.equals("content");
    }

    private String resolve(String url) {
        if (url == null) return null;
        String base = mBaseUrl != null ? mBaseUrl : mUrl;
        if (base == null || Uri.parse(url).getScheme() != null) return url;
        try {
            return new URL(new URL(base), url).toString();
        } catch (Exception e) {
            return url;
        }
    }

    private void load(final String url, final Map<String, String> headers, final byte[] post, String unused,
            final boolean addToHistory) {
        final int id = ++mLoadId;
        mOriginalUrl = url;
        pageStarted(url);
        final Request request = new Request(url, false, true);
        request.mHeaders = headers != null ? headers : Collections.<String, String>emptyMap();
        request.mMethod = post != null ? "POST" : "GET";
        Thread t = new Thread(new Runnable() {
            public void run() {
                final Page page = fetch(request, post);
                mHandler.post(new Runnable() {
                    public void run() {
                        if (id != mLoadId || mDestroyed) return;
                        finishLoad(page, addToHistory);
                    }
                });
            }
        }, "WebView load");
        t.setDaemon(true);
        t.start();
    }

    private void pageStarted(String url) {
        mUrl = url;
        mProgress = 10;
        if (mClient != null) mClient.onPageStarted(this, url, null);
        if (mChromeClient != null) mChromeClient.onProgressChanged(this, 10);
    }

    /** What a load produced: HTML or text to show, a redirect, a download, or an error. */
    private static final class Page {
        String url;
        String body;
        String mime;
        String redirect;
        int error;
        String errorText;
        int status;
        String contentDisposition;
        long length;
        boolean download;
    }

    /** Runs off the main thread. */
    private Page fetch(Request request, byte[] post) {
        Page p = new Page();
        p.url = request.mUrl;
        if (mClient != null) {
            WebResourceResponse intercepted = mClient.shouldInterceptRequest(this, request);
            if (intercepted != null) {
                p.mime = intercepted.getMimeType();
                try {
                    p.body = read(intercepted.getData(), intercepted.getEncoding());
                } catch (IOException e) {
                    p.error = WebViewClient.ERROR_IO;
                    p.errorText = "net::ERR_FAILED";
                }
                return p;
            }
        }
        String url = request.mUrl;
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.US) : "";
        try {
            if (url.equals("about:blank") || scheme.equals("about")) {
                p.body = "";
                p.mime = "text/html";
            } else if (scheme.equals("file")) {
                String path = uri.getPath();
                if (path != null && path.startsWith("/android_asset/")) {
                    p.body = read(getContext().getAssets().open(path.substring("/android_asset/".length())), null);
                } else if (path != null && path.startsWith("/android_res/")) {
                    p.body = read(openResource(path.substring("/android_res/".length())), null);
                } else {
                    p.body = read(new java.io.FileInputStream(path), null);
                }
                p.mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        MimeTypeMap.getFileExtensionFromUrl(url));
            } else if (scheme.equals("content")) {
                p.body = read(getContext().getContentResolver().openInputStream(uri), null);
                p.mime = getContext().getContentResolver().getType(uri);
            } else if (scheme.equals("data")) {
                String s = url.substring(5);
                int comma = s.indexOf(',');
                String meta = comma >= 0 ? s.substring(0, comma) : "";
                String data = comma >= 0 ? s.substring(comma + 1) : s;
                p.mime = meta.split(";")[0];
                if (meta.endsWith(";base64")) {
                    p.body = new String(Base64.decode(data, Base64.DEFAULT), java.nio.charset.StandardCharsets.UTF_8);
                } else {
                    p.body = URLDecoder.decode(data.replace("+", "%2B"), "UTF-8");
                }
            } else if (scheme.equals("http") || scheme.equals("https")) {
                if (!sNetworkAvailable) {
                    p.error = WebViewClient.ERROR_HOST_LOOKUP;
                    p.errorText = "net::ERR_INTERNET_DISCONNECTED";
                    return p;
                }
                fetchHttp(request, post, p);
            } else {
                p.error = WebViewClient.ERROR_UNSUPPORTED_SCHEME;
                p.errorText = "net::ERR_UNKNOWN_URL_SCHEME";
            }
        } catch (java.net.UnknownHostException e) {
            p.error = WebViewClient.ERROR_HOST_LOOKUP;
            p.errorText = "net::ERR_NAME_NOT_RESOLVED";
        } catch (java.net.SocketTimeoutException e) {
            p.error = WebViewClient.ERROR_TIMEOUT;
            p.errorText = "net::ERR_TIMED_OUT";
        } catch (javax.net.ssl.SSLException e) {
            p.error = WebViewClient.ERROR_FAILED_SSL_HANDSHAKE;
            p.errorText = "net::ERR_SSL_PROTOCOL_ERROR";
        } catch (java.io.FileNotFoundException e) {
            p.error = WebViewClient.ERROR_FILE_NOT_FOUND;
            p.errorText = "net::ERR_FILE_NOT_FOUND";
        } catch (IOException e) {
            p.error = WebViewClient.ERROR_CONNECT;
            p.errorText = "net::ERR_CONNECTION_REFUSED";
        } catch (RuntimeException e) {
            p.error = WebViewClient.ERROR_UNKNOWN;
            p.errorText = "net::ERR_FAILED";
        }
        return p;
    }

    private InputStream openResource(String path) throws IOException {
        // file:///android_res/<type>/<name>.<ext>
        int slash = path.indexOf('/');
        if (slash < 0) throw new java.io.FileNotFoundException(path);
        String type = path.substring(0, slash);
        String name = path.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        int id = getResources().getIdentifier(name, type, getContext().getPackageName());
        if (id == 0) throw new java.io.FileNotFoundException(path);
        return getResources().openRawResource(id);
    }

    private void fetchHttp(Request request, byte[] post, Page p) throws IOException {
        String url = request.mUrl;
        for (int hops = 0; hops < 20; hops++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(30000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", mSettings.getUserAgentString());
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            c.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag() + ",en;q=0.8");
            CookieManager cm = CookieManager.getInstance();
            String cookie = cm.acceptCookie() ? cm.getCookie(url) : null;
            if (cookie != null) c.setRequestProperty("Cookie", cookie);
            for (Map.Entry<String, String> h : request.mHeaders.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
            if (post != null && hops == 0) {
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                c.getOutputStream().write(post);
            }
            int code = c.getResponseCode();
            if (cm.acceptCookie()) {
                List<String> set = c.getHeaderFields().get("Set-Cookie");
                if (set != null) {
                    for (String s : set) cm.setCookie(url, s);
                }
            }
            if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                final String next = new URL(new URL(url), c.getHeaderField("Location")).toString();
                c.disconnect();
                // a server redirect is a navigation the app may take over (OAuth redirect URIs)
                final boolean[] taken = new boolean[1];
                final Object lock = new Object();
                final Request redirect = new Request(next, false, true);
                redirect.mRedirect = true;
                runOnMainAndWait(new Runnable() {
                    public void run() {
                        taken[0] = mClient != null && mClient.shouldOverrideUrlLoading(WebView.this, redirect);
                    }
                });
                if (taken[0]) {
                    p.redirect = "";
                    return;
                }
                String scheme = Uri.parse(next).getScheme();
                if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                    p.error = WebViewClient.ERROR_UNSUPPORTED_SCHEME;
                    p.errorText = "net::ERR_UNKNOWN_URL_SCHEME";
                    p.url = next;
                    return;
                }
                url = next;
                p.url = next;
                continue;
            }
            p.status = code;
            String type = c.getContentType();
            p.mime = type != null ? type.split(";")[0].trim() : "text/html";
            String charset = null;
            if (type != null) {
                Matcher m = Pattern.compile("charset=\"?([^;\"]+)", Pattern.CASE_INSENSITIVE).matcher(type);
                if (m.find()) charset = m.group(1);
            }
            p.contentDisposition = c.getHeaderField("Content-Disposition");
            p.length = c.getContentLengthLong();
            boolean attachment = p.contentDisposition != null && p.contentDisposition.toLowerCase(Locale.US).startsWith("attachment");
            if (attachment || !(p.mime.startsWith("text/") || p.mime.contains("html") || p.mime.contains("xml")
                    || p.mime.equals("application/json"))) {
                p.download = true;
                c.disconnect();
                return;
            }
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            p.body = in != null ? read(in, charset) : "";
            c.disconnect();
            return;
        }
        throw new java.net.ProtocolException("Too many redirects");
    }

    private void runOnMainAndWait(final Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
            return;
        }
        final Object lock = new Object();
        final boolean[] done = new boolean[1];
        mHandler.post(new Runnable() {
            public void run() {
                try {
                    r.run();
                } finally {
                    synchronized (lock) {
                        done[0] = true;
                        lock.notifyAll();
                    }
                }
            }
        });
        synchronized (lock) {
            while (!done[0]) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private static String read(InputStream in, String charset) throws IOException {
        if (in == null) return "";
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            byte[] b = out.toByteArray();
            String cs = charset;
            if (cs == null) {
                String head = new String(b, 0, Math.min(b.length, 2048), java.nio.charset.StandardCharsets.ISO_8859_1);
                Matcher m = Pattern.compile("<meta[^>]+charset=[\"']?([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE).matcher(head);
                cs = m.find() ? m.group(1) : "UTF-8";
            }
            try {
                return new String(b, cs);
            } catch (java.io.UnsupportedEncodingException e) {
                return new String(b, java.nio.charset.StandardCharsets.UTF_8);
            }
        } finally {
            in.close();
        }
    }

    private void finishLoad(Page p, boolean addToHistory) {
        if (p.redirect != null) return; // the app took the navigation
        if (p.download) {
            mProgress = 100;
            if (mDownloadListener != null) {
                mDownloadListener.onDownloadStart(p.url, mSettings.getUserAgentString(), p.contentDisposition,
                        p.mime, p.length);
            }
            return;
        }
        if (p.error != 0) {
            notifyError(p.url, p.error, p.errorText);
            return;
        }
        if (p.status >= 400 && mClient != null) {
            mClient.onReceivedHttpError(this, new Request(p.url, false, true), new WebResourceResponse(p.mime, "UTF-8",
                    p.status, "Error", Collections.<String, String>emptyMap(), null));
        }
        show(p.url, null, p.body, p.mime, addToHistory);
    }

    private void notifyError(String url, int code, String description) {
        mProgress = 100;
        if (mClient != null) {
            mClient.onReceivedError(this, new Request(url, false, true), new Error(code, description));
            mClient.onReceivedError(this, code, description, url);
        }
        render(url, null, "<h2>Webpage not available</h2><p>The webpage at <b>" + Html.escapeHtml(url)
                + "</b> could not be loaded because:</p><p>" + Html.escapeHtml(description) + "</p>", "text/html");
        mTitle = url;
        if (mClient != null) mClient.onPageFinished(this, url);
    }

    /** Shows a page: history, title, the styled text, then onPageFinished. */
    private void show(String url, String baseUrl, String body, String mime, boolean addToHistory) {
        if (mUrl == null || !url.equals(mUrl) || mProgress == 100) pageStarted(url);
        mUrl = url;
        mBaseUrl = baseUrl;
        Matcher t = TITLE.matcher(body);
        mTitle = t.find() ? Html.fromHtml(t.group(1).trim()).toString() : null;
        if (addToHistory) mHistory.add(url, mTitle);
        render(url, baseUrl, body, mime);
        mProgress = 100;
        if (mChromeClient != null) {
            mChromeClient.onProgressChanged(this, 100);
            if (mTitle != null) mChromeClient.onReceivedTitle(this, mTitle);
        }
        if (mClient != null) {
            mClient.onPageCommitVisible(this, url);
            mClient.onPageFinished(this, url);
        }
        Matcher refresh = META_REFRESH.matcher(body);
        if (refresh.find()) {
            final String next = refresh.group(1).trim();
            mHandler.post(new Runnable() {
                public void run() {
                    navigate(next, false);
                }
            });
        }
    }

    private void render(String url, String baseUrl, String body, String mime) {
        CharSequence text;
        if (mime != null && mime.startsWith("text/") && !mime.contains("html")) {
            text = body;
        } else {
            text = toSpanned(body);
        }
        mContent.setText(text);
        mScroller.scrollTo(0, 0);
    }

    private CharSequence toSpanned(String html) {
        String s = DROP.matcher(html).replaceAll("");
        s = preformatted(s);
        s = orderedLists(s);
        s = s.replaceAll("(?i)<hr\\b[^>]*>", "<br><br>");
        s = s.replaceAll("(?i)</t[dh]\\s*>", "&nbsp;&nbsp;&nbsp;");
        s = s.replaceAll("(?i)</tr\\s*>", "<br>");
        s = s.replaceAll("(?i)<(/?)(section|article|header|footer|nav|main|aside|table|form|figure|dl|center)\\b[^>]*>", "<$1div>");
        s = s.replaceAll("(?i)<dt\\b[^>]*>", "<br><b>").replaceAll("(?i)</dt\\s*>", "</b><br>");
        s = s.replaceAll("(?i)<code\\b[^>]*>", "<tt>").replaceAll("(?i)</code\\s*>", "</tt>");
        s = s.replaceAll("(?i)<(button|input|select|textarea)\\b[^>]*>", "");
        Spanned sp = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY, new Html.ImageGetter() {
            public Drawable getDrawable(String source) {
                return image(source);
            }
        }, null);
        SpannableStringBuilder b = new SpannableStringBuilder(sp);
        for (URLSpan u : b.getSpans(0, b.length(), URLSpan.class)) {
            int start = b.getSpanStart(u), end = b.getSpanEnd(u);
            b.removeSpan(u);
            b.setSpan(new Link(u.getURL()), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int end = b.length(), start = 0;
        while (end > 0 && Character.isWhitespace(b.charAt(end - 1))) end--;
        while (start < end && Character.isWhitespace(b.charAt(start))) start++;
        return b.subSequence(start, end);
    }

    private static String preformatted(String s) {
        Matcher m = PRE.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String inner = m.group(1).replace("\r\n", "\n");
            StringBuilder b = new StringBuilder("<tt>");
            for (int i = 0; i < inner.length(); i++) {
                char c = inner.charAt(i);
                if (c == '\n') b.append("<br>");
                else if (c == ' ') b.append("&nbsp;");
                else if (c == '\t') b.append("&nbsp;&nbsp;&nbsp;&nbsp;");
                else b.append(c);
            }
            b.append("</tt><br>");
            m.appendReplacement(out, Matcher.quoteReplacement(b.toString()));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String orderedLists(String s) {
        Matcher m = OL.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            Matcher li = LI.matcher(m.group(1));
            StringBuffer items = new StringBuffer();
            int n = 0;
            while (li.find()) li.appendReplacement(items, "<br>" + (++n) + ".&nbsp;");
            li.appendTail(items);
            m.appendReplacement(out, Matcher.quoteReplacement("<div>" + items.toString().replaceAll("(?i)</li\\s*>", "")
                    + "</div>"));
        }
        m.appendTail(out);
        return out.toString();
    }

    private Drawable image(String source) {
        if (source == null || !mSettings.getLoadsImagesAutomatically()) return null;
        try {
            String url = resolve(source);
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            Bitmap bmp = null;
            if ("data".equals(scheme)) {
                int comma = url.indexOf(',');
                if (comma > 0 && url.substring(0, comma).endsWith(";base64")) {
                    byte[] d = Base64.decode(url.substring(comma + 1), Base64.DEFAULT);
                    bmp = BitmapFactory.decodeByteArray(d, 0, d.length);
                }
            } else if ("file".equals(scheme) && uri.getPath() != null) {
                String path = uri.getPath();
                InputStream in = path.startsWith("/android_asset/")
                        ? getContext().getAssets().open(path.substring("/android_asset/".length()))
                        : new java.io.FileInputStream(path);
                try {
                    bmp = BitmapFactory.decodeStream(in);
                } finally {
                    in.close();
                }
            }
            if (bmp == null) return null; // network images are not fetched
            BitmapDrawable d = new BitmapDrawable(getResources(), bmp);
            float density = getResources().getDisplayMetrics().density;
            int w = (int) (bmp.getWidth() * density), h = (int) (bmp.getHeight() * density);
            int max = Math.max(1, mContent.getWidth() - mContent.getPaddingLeft() - mContent.getPaddingRight());
            if (mContent.getWidth() > 0 && w > max) {
                h = h * max / w;
                w = max;
            }
            d.setBounds(0, 0, w, h);
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    /** A link in the page: a click is a navigation (shouldOverrideUrlLoading first). */
    private final class Link extends ClickableSpan {
        final String href;

        Link(String href) {
            this.href = href;
        }

        public void onClick(View widget) {
            mLastLink = resolve(href);
            if (href.startsWith("#")) return;
            if (href.startsWith("javascript:")) return;
            navigate(href, true);
        }

        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
        }
    }

    // ---- WebResourceRequest / WebResourceError ----

    private static final class Request implements WebResourceRequest {
        final String mUrl;
        final boolean mGesture;
        final boolean mMainFrame;
        boolean mRedirect;
        String mMethod = "GET";
        Map<String, String> mHeaders = Collections.emptyMap();

        Request(String url, boolean gesture, boolean mainFrame) {
            mUrl = url;
            mGesture = gesture;
            mMainFrame = mainFrame;
        }

        public Uri getUrl() {
            return Uri.parse(mUrl);
        }

        public boolean isForMainFrame() {
            return mMainFrame;
        }

        public boolean isRedirect() {
            return mRedirect;
        }

        public boolean hasGesture() {
            return mGesture;
        }

        public String getMethod() {
            return mMethod;
        }

        public Map<String, String> getRequestHeaders() {
            return mHeaders;
        }
    }

    private static final class Error extends WebResourceError {
        private final int code;
        private final String description;

        Error(int code, String description) {
            this.code = code;
            this.description = description;
        }

        public int getErrorCode() {
            return code;
        }

        public CharSequence getDescription() {
            return description;
        }
    }

    // ---- history ----

    public boolean canGoBack() {
        return mHistory.getCurrentIndex() > 0;
    }

    public void goBack() {
        goBackOrForward(-1);
    }

    public boolean canGoForward() {
        return mHistory.getCurrentIndex() < mHistory.getSize() - 1;
    }

    public void goForward() {
        goBackOrForward(1);
    }

    public boolean canGoBackOrForward(int steps) {
        int i = mHistory.getCurrentIndex() + steps;
        return i >= 0 && i < mHistory.getSize();
    }

    public void goBackOrForward(int steps) {
        if (!canGoBackOrForward(steps)) return;
        int i = mHistory.getCurrentIndex() + steps;
        mHistory.setCurrentIndex(i);
        load(mHistory.getItemAtIndex(i).getUrl(), null, null, null, false);
    }

    public WebBackForwardList copyBackForwardList() {
        return mHistory.copy();
    }

    public void clearHistory() {
        mHistory.clear(mUrl, mTitle);
    }

    public WebBackForwardList saveState(Bundle outState) {
        if (outState == null) return null;
        ArrayList<String> urls = new ArrayList<String>();
        for (int i = 0; i < mHistory.getSize(); i++) urls.add(mHistory.getItemAtIndex(i).getUrl());
        outState.putStringArrayList("WEBVIEW_CHROMIUM_STATE_URLS", urls);
        outState.putInt("WEBVIEW_CHROMIUM_STATE_INDEX", mHistory.getCurrentIndex());
        return copyBackForwardList();
    }

    public WebBackForwardList restoreState(Bundle inState) {
        if (inState == null) return null;
        ArrayList<String> urls = inState.getStringArrayList("WEBVIEW_CHROMIUM_STATE_URLS");
        if (urls == null || urls.isEmpty()) return null;
        for (String u : urls) mHistory.add(u, null);
        int index = Math.max(0, Math.min(urls.size() - 1, inState.getInt("WEBVIEW_CHROMIUM_STATE_INDEX")));
        mHistory.setCurrentIndex(index);
        load(urls.get(index), null, null, null, false);
        return copyBackForwardList();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return super.onKeyDown(keyCode, event);
    }

    // ---- state and settings ----

    public String getUrl() {
        return mUrl;
    }

    public String getOriginalUrl() {
        return mOriginalUrl != null ? mOriginalUrl : mUrl;
    }

    public String getTitle() {
        return mTitle;
    }

    public Bitmap getFavicon() {
        return null;
    }

    public int getProgress() {
        return mProgress;
    }

    public int getContentHeight() {
        float density = getResources().getDisplayMetrics().density;
        return (int) (mContent.getHeight() / (density > 0 ? density : 1));
    }

    public WebSettings getSettings() {
        return mSettings;
    }

    /** framework-internal: WebSettingsImpl calls this when text size settings change. */
    void settingsChanged() {
        applyTextSize();
    }

    public void setWebViewClient(WebViewClient client) {
        mClient = client != null ? client : new WebViewClient();
    }

    public WebViewClient getWebViewClient() {
        return mClient;
    }

    public void setWebChromeClient(WebChromeClient client) {
        mChromeClient = client;
    }

    public WebChromeClient getWebChromeClient() {
        return mChromeClient;
    }

    public void setDownloadListener(DownloadListener listener) {
        mDownloadListener = listener;
    }

    public void setPictureListener(PictureListener listener) {
    }

    public void addJavascriptInterface(Object object, String name) {
        // kept for the app's sake; with no JavaScript nothing calls it
        if (object != null && name != null) mJsInterfaces.put(name, object);
    }

    public void removeJavascriptInterface(String name) {
        mJsInterfaces.remove(name);
    }

    public WebMessagePort[] createWebMessageChannel() {
        return new WebMessagePort[0];
    }

    public void postWebMessage(WebMessage message, Uri targetOrigin) {
    }

    public static void setWebContentsDebuggingEnabled(boolean enabled) {
    }

    public static void setDataDirectorySuffix(String suffix) {
    }

    public static void disableWebView() {
    }

    public static PackageInfo getCurrentWebViewPackage() {
        PackageInfo pi = new PackageInfo();
        pi.packageName = "com.google.android.webview";
        pi.versionName = "120.0.0.0";
        pi.versionCode = 1;
        return pi;
    }

    public static ClassLoader getWebViewClassLoader() {
        return WebView.class.getClassLoader();
    }

    public Looper getWebViewLooper() {
        return Looper.getMainLooper();
    }

    public void destroy() {
        mDestroyed = true;
        mLoadId++;
    }

    public void setNetworkAvailable(boolean networkUp) {
        sNetworkAvailable = networkUp;
    }

    public SslCertificate getCertificate() {
        return mCertificate;
    }

    public void setCertificate(SslCertificate certificate) {
        mCertificate = certificate;
    }

    public void savePassword(String host, String username, String password) {
    }

    public void setHttpAuthUsernamePassword(String host, String realm, String username, String password) {
    }

    public String[] getHttpAuthUsernamePassword(String host, String realm) {
        return null;
    }

    public void setHorizontalScrollbarOverlay(boolean overlay) {
    }

    public void setVerticalScrollbarOverlay(boolean overlay) {
    }

    public boolean overlayHorizontalScrollbar() {
        return true;
    }

    public boolean overlayVerticalScrollbar() {
        return false;
    }

    public boolean isPrivateBrowsingEnabled() {
        return false;
    }

    public boolean pageUp(boolean top) {
        return top ? mScroller.fullScroll(View.FOCUS_UP) : mScroller.pageScroll(View.FOCUS_UP);
    }

    public boolean pageDown(boolean bottom) {
        return bottom ? mScroller.fullScroll(View.FOCUS_DOWN) : mScroller.pageScroll(View.FOCUS_DOWN);
    }

    public void postVisualStateCallback(final long requestId, final VisualStateCallback callback) {
        mHandler.post(new Runnable() {
            public void run() {
                callback.onComplete(requestId);
            }
        });
    }

    public void clearView() {
        mContent.setText("");
    }

    public Picture capturePicture() {
        Picture p = new Picture();
        android.graphics.Canvas c = p.beginRecording(Math.max(1, getWidth()), Math.max(1, mContent.getHeight()));
        mContent.draw(c);
        p.endRecording();
        return p;
    }

    public float getScale() {
        return getResources().getDisplayMetrics().density;
    }

    public void setInitialScale(int scaleInPercent) {
    }

    public void invokeZoomPicker() {
    }

    public HitTestResult getHitTestResult() {
        HitTestResult r = new HitTestResult();
        if (mLastLink != null) {
            r.setType(HitTestResult.SRC_ANCHOR_TYPE);
            r.setExtra(mLastLink);
        }
        return r;
    }

    public void requestFocusNodeHref(Message hrefMsg) {
        if (hrefMsg == null) return;
        Bundle data = hrefMsg.getData();
        data.putString("url", mLastLink);
        data.putString("title", null);
        data.putString("src", null);
        hrefMsg.setData(data);
        hrefMsg.sendToTarget();
    }

    public void requestImageRef(Message msg) {
        if (msg == null) return;
        msg.getData().putString("url", null);
        msg.sendToTarget();
    }

    public void pauseTimers() {
    }

    public void resumeTimers() {
    }

    public void onPause() {
    }

    public void onResume() {
    }

    public void freeMemory() {
    }

    public void clearCache(boolean includeDiskFiles) {
    }

    public void clearFormData() {
    }

    public void clearSslPreferences() {
    }

    public static void clearClientCertPreferences(Runnable onCleared) {
        if (onCleared != null) new Handler(Looper.getMainLooper()).post(onCleared);
    }

    public static void startSafeBrowsing(Context context, final ValueCallback<Boolean> callback) {
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    callback.onReceiveValue(Boolean.FALSE);
                }
            });
        }
    }

    public static void setSafeBrowsingWhitelist(List<String> hosts, final ValueCallback<Boolean> callback) {
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    callback.onReceiveValue(Boolean.TRUE);
                }
            });
        }
    }

    public static Uri getSafeBrowsingPrivacyPolicyUrl() {
        return Uri.parse("https://www.google.com/chrome/privacy/");
    }

    public void setFindListener(FindListener listener) {
        mFindListener = listener;
    }

    public void findNext(boolean forward) {
    }

    public int findAll(String find) {
        int count = 0;
        if (find != null && !find.isEmpty()) {
            String text = mContent.getText().toString().toLowerCase(Locale.ROOT);
            String f = find.toLowerCase(Locale.ROOT);
            for (int i = text.indexOf(f); i >= 0; i = text.indexOf(f, i + f.length())) count++;
        }
        if (mFindListener != null) mFindListener.onFindResultReceived(count > 0 ? 0 : 0, count, true);
        return count;
    }

    public void findAllAsync(final String find) {
        mHandler.post(new Runnable() {
            public void run() {
                findAll(find);
            }
        });
    }

    public boolean showFindDialog(String text, boolean showIme) {
        return false;
    }

    public static String findAddress(String addr) {
        return null;
    }

    public static void enableSlowWholeDocumentDraw() {
    }

    public void clearMatches() {
    }

    public void documentHasImages(Message response) {
        if (response == null) return;
        response.arg1 = 0;
        response.sendToTarget();
    }

    public WebViewRenderProcess getWebViewRenderProcess() {
        return null;
    }

    public void setWebViewRenderProcessClient(Executor executor, WebViewRenderProcessClient client) {
    }

    public void setWebViewRenderProcessClient(WebViewRenderProcessClient client) {
    }

    public WebViewRenderProcessClient getWebViewRenderProcessClient() {
        return null;
    }

    public void onChildViewAdded(View parent, View child) {
    }

    public void onChildViewRemoved(View p, View child) {
    }

    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
    }

    public void setMapTrackballToArrowKeys(boolean setMap) {
    }

    public void flingScroll(int vx, int vy) {
        mScroller.fling(vy);
    }

    public boolean canZoomIn() {
        return false;
    }

    public boolean canZoomOut() {
        return false;
    }

    public void zoomBy(float zoomFactor) {
    }

    public boolean zoomIn() {
        return false;
    }

    public boolean zoomOut() {
        return false;
    }

    public void setRendererPriorityPolicy(int rendererRequestedPriority, boolean waivedWhenNotVisible) {
    }

    public int getRendererRequestedPriority() {
        return RENDERER_PRIORITY_IMPORTANT;
    }

    public boolean getRendererPriorityWaivedWhenNotVisible() {
        return false;
    }

    public CharSequence getAccessibilityClassName() {
        return WebView.class.getName();
    }

    public void setBackgroundColor(int color) {
        super.setBackgroundColor(color);
    }

    public boolean onCheckIsTextEditor() {
        return false;
    }

    // ---- nested types ----

    public class WebViewTransport {
        private WebView mWebview;

        public WebViewTransport() {
        }

        public synchronized void setWebView(WebView webview) {
            mWebview = webview;
        }

        public synchronized WebView getWebView() {
            return mWebview;
        }
    }

    public static class HitTestResult {
        public static final int UNKNOWN_TYPE = 0;
        public static final int ANCHOR_TYPE = 1;
        public static final int PHONE_TYPE = 2;
        public static final int GEO_TYPE = 3;
        public static final int EMAIL_TYPE = 4;
        public static final int IMAGE_TYPE = 5;
        public static final int IMAGE_ANCHOR_TYPE = 6;
        public static final int SRC_ANCHOR_TYPE = 7;
        public static final int SRC_IMAGE_ANCHOR_TYPE = 8;
        public static final int EDIT_TEXT_TYPE = 9;

        private int mType = UNKNOWN_TYPE;
        private String mExtra;

        HitTestResult() {
        }

        void setType(int type) {
            mType = type;
        }

        void setExtra(String extra) {
            mExtra = extra;
        }

        public int getType() {
            return mType;
        }

        public String getExtra() {
            return mExtra;
        }
    }

    public interface FindListener {
        void onFindResultReceived(int activeMatchOrdinal, int numberOfMatches, boolean isDoneCounting);
    }

    @Deprecated
    public interface PictureListener {
        @Deprecated
        void onNewPicture(WebView view, Picture picture);
    }

    public abstract static class VisualStateCallback {
        public VisualStateCallback() {
        }

        public abstract void onComplete(long requestId);
    }
}
