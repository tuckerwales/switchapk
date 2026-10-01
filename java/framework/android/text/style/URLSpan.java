package android.text.style;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

/** A clickable span that opens its URL with ACTION_VIEW (AOSP URLSpan). */
public class URLSpan extends ClickableSpan implements ParcelableSpan {
    private final String mURL;

    public URLSpan(String url) { mURL = url; }

    public URLSpan(Parcel src) { mURL = src.readString(); }

    public int getSpanTypeId() { return getSpanTypeIdInternal(); }

    /** Hidden AOSP API. */
    public int getSpanTypeIdInternal() { return TextUtils.URL_SPAN; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }

    /** Hidden AOSP API. */
    public void writeToParcelInternal(Parcel dest, int flags) { dest.writeString(mURL); }

    public String getURL() { return mURL; }

    @Override
    public void onClick(View widget) {
        Uri uri = Uri.parse(getURL());
        Context context = widget.getContext();
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.putExtra("com.android.browser.application_id", context.getPackageName());
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.w("URLSpan", "Actvity was not found for intent, " + intent.toString());
        }
    }

    @Override
    public String toString() { return "URLSpan{URL='" + getURL() + "'}"; }
}
