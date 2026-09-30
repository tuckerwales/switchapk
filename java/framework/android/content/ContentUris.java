package android.content;

import android.net.Uri;

public class ContentUris {
    public ContentUris() {}
    public static long parseId(Uri contentUri) {
        String last = contentUri.getLastPathSegment();
        return last == null ? -1 : Long.parseLong(last);
    }
    public static Uri.Builder appendId(Uri.Builder builder, long id) { return builder.appendEncodedPath(String.valueOf(id)); }
    public static Uri withAppendedId(Uri contentUri, long id) { return appendId(contentUri.buildUpon(), id).build(); }
    public static Uri removeId(Uri contentUri) {
        String p = contentUri.getPath();
        int slash = p.lastIndexOf('/');
        return contentUri.buildUpon().path(slash >= 0 ? p.substring(0, slash) : p).build();
    }
}
