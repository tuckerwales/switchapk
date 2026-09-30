package android.content;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;

public class ClipData implements Parcelable {
    final ClipDescription mClipDescription;
    final ArrayList<Item> mItems = new ArrayList<Item>();

    public static class Item {
        final CharSequence mText;
        final String mHtmlText;
        final Intent mIntent;
        Uri mUri;

        public Item(CharSequence text) { this(text, null, null, null); }
        public Item(CharSequence text, String htmlText) { this(text, htmlText, null, null); }
        public Item(Intent intent) { this(null, null, intent, null); }
        public Item(Uri uri) { this(null, null, null, uri); }
        public Item(CharSequence text, Intent intent, Uri uri) { this(text, null, intent, uri); }

        public Item(CharSequence text, String htmlText, Intent intent, Uri uri) {
            mText = text;
            mHtmlText = htmlText;
            mIntent = intent;
            mUri = uri;
        }

        public CharSequence getText() { return mText; }
        public String getHtmlText() { return mHtmlText; }
        public Intent getIntent() { return mIntent; }
        public Uri getUri() { return mUri; }

        public CharSequence coerceToText(Context context) {
            if (mText != null) return mText;
            if (mHtmlText != null) return mHtmlText;
            if (mUri != null) return mUri.toString();
            if (mIntent != null) return mIntent.toUri(Intent.URI_INTENT_SCHEME);
            return "";
        }

        public CharSequence coerceToStyledText(Context context) { return coerceToText(context); }
        public String coerceToHtmlText(Context context) { return mHtmlText != null ? mHtmlText : String.valueOf(coerceToText(context)); }

        @Override
        public String toString() { return "ClipData.Item { " + (mText != null ? "T:" + mText : mUri != null ? "U:" + mUri : "NULL") + " }"; }
    }

    public ClipData(CharSequence label, String[] mimeTypes, Item item) {
        mClipDescription = new ClipDescription(label, mimeTypes);
        mItems.add(item);
    }

    public ClipData(ClipDescription description, Item item) {
        mClipDescription = description;
        mItems.add(item);
    }

    public ClipData(ClipData other) {
        mClipDescription = other.mClipDescription;
        mItems.addAll(other.mItems);
    }

    public static ClipData newPlainText(CharSequence label, CharSequence text) {
        return new ClipData(label, new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN}, new Item(text));
    }

    public static ClipData newHtmlText(CharSequence label, CharSequence text, String htmlText) {
        return new ClipData(label, new String[] {ClipDescription.MIMETYPE_TEXT_HTML}, new Item(text, htmlText));
    }

    public static ClipData newIntent(CharSequence label, Intent intent) {
        return new ClipData(label, new String[] {ClipDescription.MIMETYPE_TEXT_INTENT}, new Item(intent));
    }

    public static ClipData newUri(ContentResolver resolver, CharSequence label, Uri uri) { return newRawUri(label, uri); }

    public static ClipData newRawUri(CharSequence label, Uri uri) {
        return new ClipData(label, new String[] {ClipDescription.MIMETYPE_TEXT_URILIST}, new Item(uri));
    }

    public ClipDescription getDescription() { return mClipDescription; }
    public void addItem(Item item) { mItems.add(item); }
    public void addItem(ContentResolver resolver, Item item) { mItems.add(item); }
    public int getItemCount() { return mItems.size(); }
    public Item getItemAt(int index) { return mItems.get(index); }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel dest, int flags) { dest.writeValue(this); }

    public static final Parcelable.Creator<ClipData> CREATOR = new Parcelable.Creator<ClipData>() {
        public ClipData createFromParcel(Parcel source) { return (ClipData) source.readValue(null); }
        public ClipData[] newArray(int size) { return new ClipData[size]; }
    };
}
