package android.webkit;

import java.util.HashMap;

public class MimeTypeMap {
    private static final MimeTypeMap sInstance = new MimeTypeMap();
    private final HashMap<String, String> mMime = new HashMap<String, String>();
    private final HashMap<String, String> mExt = new HashMap<String, String>();

    private MimeTypeMap() {
        add("html", "text/html");
        add("htm", "text/html");
        add("txt", "text/plain");
        add("xml", "text/xml");
        add("json", "application/json");
        add("js", "application/javascript");
        add("css", "text/css");
        add("png", "image/png");
        add("jpg", "image/jpeg");
        add("jpeg", "image/jpeg");
        add("gif", "image/gif");
        add("webp", "image/webp");
        add("bmp", "image/bmp");
        add("svg", "image/svg+xml");
        add("mp3", "audio/mpeg");
        add("ogg", "audio/ogg");
        add("wav", "audio/wav");
        add("mp4", "video/mp4");
        add("webm", "video/webm");
        add("pdf", "application/pdf");
        add("zip", "application/zip");
        add("apk", "application/vnd.android.package-archive");
        add("bin", "application/octet-stream");
    }

    private void add(String ext, String mime) {
        mMime.put(ext, mime);
        if (!mExt.containsKey(mime)) mExt.put(mime, ext);
    }

    public static MimeTypeMap getSingleton() { return sInstance; }

    public static String getFileExtensionFromUrl(String url) {
        if (url == null || url.length() == 0) return "";
        int query = url.indexOf('?');
        if (query >= 0) url = url.substring(0, query);
        int slash = url.lastIndexOf('/');
        int dot = url.lastIndexOf('.');
        if (dot < 0 || dot < slash || dot >= url.length() - 1) return "";
        return url.substring(dot + 1).toLowerCase();
    }

    public String getMimeTypeFromExtension(String extension) {
        if (extension == null) return null;
        return mMime.get(extension.toLowerCase());
    }

    public String getExtensionFromMimeType(String mimeType) {
        if (mimeType == null) return null;
        return mExt.get(mimeType.toLowerCase());
    }

    public boolean hasMimeType(String mimeType) { return getExtensionFromMimeType(mimeType) != null; }
    public boolean hasExtension(String extension) { return getMimeTypeFromExtension(extension) != null; }
}
