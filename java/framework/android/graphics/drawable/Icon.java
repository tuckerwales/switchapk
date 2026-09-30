package android.graphics.drawable;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

public final class Icon implements Parcelable {
    public static final int TYPE_BITMAP = 1;
    public static final int TYPE_RESOURCE = 2;
    public static final int TYPE_DATA = 3;
    public static final int TYPE_URI = 4;
    public static final int TYPE_ADAPTIVE_BITMAP = 5;

    private final int mType;
    private Bitmap mBitmap;
    private int mResId;
    private String mPackage;
    private byte[] mData;
    private Uri mUri;
    private ColorStateList mTint;
    private PorterDuff.Mode mTintMode = PorterDuff.Mode.SRC_IN;

    private Icon(int type) { mType = type; }

    public int getType() { return mType; }
    public Bitmap getBitmap() { return mBitmap; }
    public int getResId() { return mResId; }
    public String getResPackage() { return mPackage; }
    public Uri getUri() { return mUri; }

    public static Icon createWithResource(Context context, int resId) {
        Icon i = new Icon(TYPE_RESOURCE);
        i.mResId = resId;
        i.mPackage = context.getPackageName();
        return i;
    }

    public static Icon createWithResource(Resources res, int resId) {
        Icon i = new Icon(TYPE_RESOURCE);
        i.mResId = resId;
        return i;
    }

    public static Icon createWithResource(String resPackage, int resId) {
        Icon i = new Icon(TYPE_RESOURCE);
        i.mResId = resId;
        i.mPackage = resPackage;
        return i;
    }

    public static Icon createWithBitmap(Bitmap bits) {
        Icon i = new Icon(TYPE_BITMAP);
        i.mBitmap = bits;
        return i;
    }

    public static Icon createWithAdaptiveBitmap(Bitmap bits) {
        Icon i = new Icon(TYPE_ADAPTIVE_BITMAP);
        i.mBitmap = bits;
        return i;
    }

    public static Icon createWithData(byte[] data, int offset, int length) {
        Icon i = new Icon(TYPE_DATA);
        i.mData = new byte[length];
        System.arraycopy(data, offset, i.mData, 0, length);
        return i;
    }

    public static Icon createWithContentUri(String uri) { return createWithContentUri(Uri.parse(uri)); }

    public static Icon createWithContentUri(Uri uri) {
        Icon i = new Icon(TYPE_URI);
        i.mUri = uri;
        return i;
    }

    public static Icon createWithFilePath(String path) { return createWithContentUri(Uri.fromFile(new java.io.File(path))); }

    public Icon setTint(int tint) { return setTintList(ColorStateList.valueOf(tint)); }
    public Icon setTintList(ColorStateList tintList) { mTint = tintList; return this; }
    public Icon setTintMode(PorterDuff.Mode mode) { mTintMode = mode; return this; }

    public Drawable loadDrawable(Context context) {
        Drawable d = null;
        switch (mType) {
            case TYPE_BITMAP:
            case TYPE_ADAPTIVE_BITMAP:
                d = new BitmapDrawable(context.getResources(), mBitmap);
                break;
            case TYPE_RESOURCE:
                d = context.getResources().getDrawable(mResId, context.getTheme());
                break;
            case TYPE_DATA:
                d = new BitmapDrawable(context.getResources(), BitmapFactory.decodeByteArray(mData, 0, mData.length));
                break;
            case TYPE_URI:
                try {
                    d = new BitmapDrawable(context.getResources(), BitmapFactory.decodeStream(context.getContentResolver().openInputStream(mUri)));
                } catch (Exception e) {
                    d = null;
                }
                break;
        }
        if (d != null && mTint != null) {
            d.mutate();
            d.setTintList(mTint);
            d.setTintMode(mTintMode);
        }
        return d;
    }

    public void loadDrawableAsync(Context context, final OnDrawableLoadedListener listener, android.os.Handler handler) {
        final Drawable d = loadDrawable(context);
        handler.post(new Runnable() {
            public void run() { listener.onDrawableLoaded(d); }
        });
    }

    public interface OnDrawableLoadedListener {
        void onDrawableLoaded(Drawable d);
    }

    public int describeContents() { return 0; }
    public void writeToParcel(Parcel dest, int flags) { dest.writeValue(this); }

    public static final Parcelable.Creator<Icon> CREATOR = new Parcelable.Creator<Icon>() {
        public Icon createFromParcel(Parcel in) { return (Icon) in.readValue(null); }
        public Icon[] newArray(int size) { return new Icon[size]; }
    };
}
