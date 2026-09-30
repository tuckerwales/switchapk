package android.content;

import android.net.Uri;

public class ContentProviderResult {
    public final Uri uri;
    public final Integer count;

    public ContentProviderResult(Uri uri) { this.uri = uri; this.count = null; }
    public ContentProviderResult(int count) { this.count = count; this.uri = null; }
}
