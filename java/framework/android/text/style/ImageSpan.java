package android.text.style;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import java.io.InputStream;

/** Replaces text with an image (AOSP ImageSpan). */
public class ImageSpan extends DynamicDrawableSpan {
    private Drawable mDrawable;
    private Uri mContentUri;
    private int mResourceId;
    private Context mContext;
    private String mSource;

    @Deprecated
    public ImageSpan(Bitmap b) { this(null, b, ALIGN_BOTTOM); }

    @Deprecated
    public ImageSpan(Bitmap b, int verticalAlignment) { this(null, b, verticalAlignment); }

    public ImageSpan(Context context, Bitmap bitmap) { this(context, bitmap, ALIGN_BOTTOM); }

    public ImageSpan(Context context, Bitmap bitmap, int verticalAlignment) {
        super(verticalAlignment);
        mContext = context;
        mDrawable = context != null ? new BitmapDrawable(context.getResources(), bitmap) : new BitmapDrawable(bitmap);
        int width = mDrawable.getIntrinsicWidth();
        int height = mDrawable.getIntrinsicHeight();
        mDrawable.setBounds(0, 0, width > 0 ? width : 0, height > 0 ? height : 0);
    }

    public ImageSpan(Drawable drawable) { this(drawable, ALIGN_BOTTOM); }

    public ImageSpan(Drawable drawable, int verticalAlignment) {
        super(verticalAlignment);
        mDrawable = drawable;
    }

    public ImageSpan(Drawable drawable, String source) { this(drawable, source, ALIGN_BOTTOM); }

    public ImageSpan(Drawable drawable, String source, int verticalAlignment) {
        super(verticalAlignment);
        mDrawable = drawable;
        mSource = source;
    }

    public ImageSpan(Context context, Uri uri) { this(context, uri, ALIGN_BOTTOM); }

    public ImageSpan(Context context, Uri uri, int verticalAlignment) {
        super(verticalAlignment);
        mContext = context;
        mContentUri = uri;
        mSource = uri.toString();
    }

    public ImageSpan(Context context, int resourceId) { this(context, resourceId, ALIGN_BOTTOM); }

    public ImageSpan(Context context, int resourceId, int verticalAlignment) {
        super(verticalAlignment);
        mContext = context;
        mResourceId = resourceId;
    }

    @Override
    public Drawable getDrawable() {
        Drawable drawable = null;
        if (mDrawable != null) {
            drawable = mDrawable;
        } else if (mContentUri != null) {
            Bitmap bitmap = null;
            try {
                InputStream is = mContext.getContentResolver().openInputStream(mContentUri);
                bitmap = BitmapFactory.decodeStream(is);
                drawable = new BitmapDrawable(mContext.getResources(), bitmap);
                drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                is.close();
            } catch (Exception e) {
                Log.e("ImageSpan", "Failed to loaded content " + mContentUri, e);
            }
        } else {
            try {
                drawable = mContext.getDrawable(mResourceId);
                drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            } catch (Exception e) {
                Log.e("ImageSpan", "Unable to find resource: " + mResourceId);
            }
        }
        return drawable;
    }

    public String getSource() { return mSource; }

    @Override
    public String toString() { return "ImageSpan{drawable=" + getDrawable() + ", source='" + getSource() + "', verticalAlignment=" + getVerticalAlignment() + "}"; }
}
