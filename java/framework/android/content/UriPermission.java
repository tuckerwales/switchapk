package android.content;

import android.net.Uri;

public final class UriPermission {
    public static final long INVALID_TIME = Long.MIN_VALUE;
    private final Uri mUri;
    public UriPermission(Uri uri) { mUri = uri; }
    public Uri getUri() { return mUri; }
    public boolean isReadPermission() { return true; }
    public boolean isWritePermission() { return true; }
    public long getPersistedTime() { return 0; }
}
