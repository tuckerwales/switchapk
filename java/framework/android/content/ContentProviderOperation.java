package android.content;

import android.net.Uri;

public class ContentProviderOperation {
    private final Uri mUri;

    ContentProviderOperation(Uri uri) { mUri = uri; }

    public Uri getUri() { return mUri; }
    public static Builder newInsert(Uri uri) { return new Builder(uri); }
    public static Builder newUpdate(Uri uri) { return new Builder(uri); }
    public static Builder newDelete(Uri uri) { return new Builder(uri); }
    public static Builder newAssertQuery(Uri uri) { return new Builder(uri); }

    public static class Builder {
        private final Uri mUri;
        Builder(Uri uri) { mUri = uri; }
        public ContentProviderOperation build() { return new ContentProviderOperation(mUri); }
        public Builder withValues(ContentValues values) { return this; }
        public Builder withValue(String key, Object value) { return this; }
        public Builder withSelection(String selection, String[] selectionArgs) { return this; }
        public Builder withExpectedCount(int count) { return this; }
        public Builder withYieldAllowed(boolean yieldAllowed) { return this; }
        public Builder withValueBackReference(String key, int previousResult) { return this; }
    }
}
