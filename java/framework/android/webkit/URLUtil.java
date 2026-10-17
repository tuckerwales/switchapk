package android.webkit;

import android.net.Uri;

import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ported from AOSP (API 29 behaviour). */
public final class URLUtil {
    // "file:///android_asset/foo/bar.png" refers to assets/foo/bar.png.
    static final String ASSET_BASE = "file:///android_asset/";
    // "file:///android_res/drawable/bar.png" refers to res/drawable/bar.png.
    static final String RESOURCE_BASE = "file:///android_res/";
    static final String FILE_BASE = "file:";
    static final String PROXY_BASE = "file:///cookieless_proxy/";
    static final String CONTENT_BASE = "content:";

    public URLUtil() {
    }

    /** Cleans up (if possible) user-entered web addresses. */
    public static String guessUrl(String inUrl) {
        String retVal = inUrl;
        if (inUrl.length() == 0) return inUrl;
        if (inUrl.startsWith("about:")) return inUrl;
        // Do not try to interpret data, file or javascript scheme URLs.
        if (inUrl.startsWith("data:")) return inUrl;
        if (inUrl.startsWith("file:")) return inUrl;
        if (inUrl.startsWith("javascript:")) return inUrl;
        // Strip a period off the end of the URL.
        if (inUrl.endsWith(".")) inUrl = inUrl.substring(0, inUrl.length() - 1);

        WebAddress webAddress = WebAddress.parse(inUrl);
        if (webAddress == null) return retVal;
        // No dot: the user probably entered a bare domain, so try www.<it>.com.
        if (webAddress.mHost.indexOf('.') == -1) webAddress.mHost = "www." + webAddress.mHost + ".com";
        return webAddress.toString();
    }

    public static String composeSearchUrl(String inQuery, String template, String queryPlaceHolder) {
        int placeHolderIndex = template.indexOf(queryPlaceHolder);
        if (placeHolderIndex < 0) return null;
        StringBuilder buffer = new StringBuilder();
        buffer.append(template.substring(0, placeHolderIndex));
        try {
            buffer.append(java.net.URLEncoder.encode(inQuery, "utf-8"));
        } catch (UnsupportedEncodingException ex) {
            return null;
        }
        buffer.append(template.substring(placeHolderIndex + queryPlaceHolder.length()));
        return buffer.toString();
    }

    public static byte[] decode(byte[] url) throws IllegalArgumentException {
        if (url.length == 0) return new byte[0];
        byte[] tempData = new byte[url.length];
        int tempCount = 0;
        for (int i = 0; i < url.length; i++) {
            byte b = url[i];
            if (b == '%') {
                if (url.length - i > 2) {
                    b = (byte) (parseHex(url[i + 1]) * 16 + parseHex(url[i + 2]));
                    i += 2;
                } else {
                    throw new IllegalArgumentException("Invalid format");
                }
            }
            tempData[tempCount++] = b;
        }
        byte[] retData = new byte[tempCount];
        System.arraycopy(tempData, 0, retData, 0, tempCount);
        return retData;
    }

    private static int parseHex(byte b) {
        if (b >= '0' && b <= '9') return b - '0';
        if (b >= 'A' && b <= 'F') return b - 'A' + 10;
        if (b >= 'a' && b <= 'f') return b - 'a' + 10;
        throw new IllegalArgumentException("Invalid hex char '" + b + "'");
    }

    public static boolean isAssetUrl(String url) {
        return (null != url) && url.startsWith(ASSET_BASE);
    }

    /** Hidden on Android. */
    static boolean isResourceUrl(String url) {
        return (null != url) && url.startsWith(RESOURCE_BASE);
    }

    @Deprecated
    public static boolean isCookielessProxyUrl(String url) {
        return (null != url) && url.startsWith(PROXY_BASE);
    }

    public static boolean isFileUrl(String url) {
        return (null != url) && (url.startsWith(FILE_BASE) && !url.startsWith(ASSET_BASE)
                && !url.startsWith(PROXY_BASE));
    }

    public static boolean isAboutUrl(String url) {
        return (null != url) && url.startsWith("about:");
    }

    public static boolean isDataUrl(String url) {
        return (null != url) && url.startsWith("data:");
    }

    public static boolean isJavaScriptUrl(String url) {
        return (null != url) && url.startsWith("javascript:");
    }

    public static boolean isHttpUrl(String url) {
        return (null != url) && (url.length() > 6) && url.substring(0, 7).equalsIgnoreCase("http://");
    }

    public static boolean isHttpsUrl(String url) {
        return (null != url) && (url.length() > 7) && url.substring(0, 8).equalsIgnoreCase("https://");
    }

    public static boolean isNetworkUrl(String url) {
        if (url == null || url.length() == 0) return false;
        return isHttpUrl(url) || isHttpsUrl(url);
    }

    public static boolean isContentUrl(String url) {
        return (null != url) && url.startsWith(CONTENT_BASE);
    }

    public static boolean isValidUrl(String url) {
        if (url == null || url.length() == 0) return false;
        return isAssetUrl(url) || isResourceUrl(url) || isFileUrl(url) || isAboutUrl(url) || isHttpUrl(url)
                || isHttpsUrl(url) || isJavaScriptUrl(url) || isContentUrl(url);
    }

    public static String stripAnchor(String url) {
        int anchorIndex = url.indexOf('#');
        if (anchorIndex != -1) return url.substring(0, anchorIndex);
        return url;
    }

    /** Guesses the file name of a download from its URL, Content-Disposition header and MIME type. */
    public static final String guessFileName(String url, String contentDisposition, String mimeType) {
        String filename = null;
        String extension = null;

        if (contentDisposition != null) {
            filename = parseContentDisposition(contentDisposition);
            if (filename != null) {
                int index = filename.lastIndexOf('/') + 1;
                if (index > 0) filename = filename.substring(index);
            }
        }

        // Fall back to the last path segment of the URL.
        if (filename == null) {
            String decodedUrl = Uri.decode(url);
            if (decodedUrl != null) {
                int queryIndex = decodedUrl.indexOf('?');
                // Strip a query string, same as desktop browsers.
                if (queryIndex > 0) decodedUrl = decodedUrl.substring(0, queryIndex);
                if (!decodedUrl.endsWith("/")) {
                    int index = decodedUrl.lastIndexOf('/') + 1;
                    if (index > 0) filename = decodedUrl.substring(index);
                }
            }
        }

        if (filename == null) filename = "downloadfile";

        // Split filename between base and extension; add an extension if it has none.
        int dotIndex = filename.indexOf('.');
        if (dotIndex < 0) {
            if (mimeType != null) {
                extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                if (extension != null) extension = "." + extension;
            }
            if (extension == null) {
                if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("text/")) {
                    if (mimeType.equalsIgnoreCase("text/html")) {
                        extension = ".html";
                    } else {
                        extension = ".txt";
                    }
                } else {
                    extension = ".bin";
                }
            }
        } else {
            if (mimeType != null) {
                // Compare the last segment of the extension against the MIME type; on a mismatch, discard
                // the entire extension.
                int lastDotIndex = filename.lastIndexOf('.');
                String typeFromExt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        filename.substring(lastDotIndex + 1));
                if (typeFromExt != null && !typeFromExt.equalsIgnoreCase(mimeType)) {
                    extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                    if (extension != null) extension = "." + extension;
                }
            }
            if (extension == null) extension = filename.substring(dotIndex);
            filename = filename.substring(0, dotIndex);
        }

        return filename + extension;
    }

    // Matches "attachment; filename=..." with or without quotes, as AOSP.
    private static final Pattern CONTENT_DISPOSITION_PATTERN =
            Pattern.compile("attachment;\\s*filename\\s*=\\s*(\"?)([^\"]*)\\1\\s*$", Pattern.CASE_INSENSITIVE);

    /** Hidden on Android: the file name of an "attachment" Content-Disposition, or null. */
    static String parseContentDisposition(String contentDisposition) {
        try {
            Matcher m = CONTENT_DISPOSITION_PATTERN.matcher(contentDisposition);
            if (m.find()) return m.group(2);
        } catch (IllegalStateException ex) {
            // This function is defined as returning null when it can't parse the header.
        }
        return null;
    }

    /** Port of the hidden android.net.WebAddress parser, as used by guessUrl. */
    private static final class WebAddress {
        private static final String GOOD_IRI_CHAR = "a-zA-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF";
        private static final Pattern ADDRESS_PATTERN = Pattern.compile(
                /* scheme    */ "(?:(http|https|file)\\:\\/\\/)?"
                /* authority */ + "(?:([-A-Za-z0-9$_.+!*'(),;?&=]+(?:\\:[-A-Za-z0-9$_.+!*'(),;?&=]+)?)@)?"
                /* host      */ + "([" + GOOD_IRI_CHAR + "%_-][" + GOOD_IRI_CHAR + "%_\\.-]*|\\[[0-9a-fA-F:\\.]+\\])?"
                /* port      */ + "(?:\\:([0-9]*))?"
                /* path      */ + "(\\/?[^#]*)?"
                /* anchor    */ + ".*", Pattern.CASE_INSENSITIVE);

        String mScheme = "";
        String mHost = "";
        int mPort = -1;
        String mPath = "/";
        String mAuthInfo = "";

        static WebAddress parse(String address) {
            WebAddress a = new WebAddress();
            Matcher m = ADDRESS_PATTERN.matcher(address);
            if (!m.matches()) return null;
            String t = m.group(1);
            if (t != null) a.mScheme = t.toLowerCase(Locale.ROOT);
            t = m.group(2);
            if (t != null) a.mAuthInfo = t;
            t = m.group(3);
            if (t != null) a.mHost = t;
            t = m.group(4);
            if (t != null && t.length() > 0) {
                try {
                    a.mPort = Integer.parseInt(t);
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
            t = m.group(5);
            if (t != null && t.length() > 0) a.mPath = t.charAt(0) == '/' ? t : "/" + t;

            // Infer the scheme from the port and vice versa.
            if (a.mPort == 443 && a.mScheme.equals("")) {
                a.mScheme = "https";
            } else if (a.mPort == -1) {
                a.mPort = a.mScheme.equals("https") ? 443 : 80;
            }
            if (a.mScheme.equals("")) a.mScheme = "http";
            return a;
        }

        @Override
        public String toString() {
            String port = "";
            if ((mPort != 443 && mScheme.equals("https")) || (mPort != 80 && mScheme.equals("http"))) {
                port = ":" + Integer.toString(mPort);
            }
            String authInfo = mAuthInfo.length() > 0 ? mAuthInfo + "@" : "";
            return mScheme + "://" + authInfo + mHost + port + mPath;
        }
    }
}
