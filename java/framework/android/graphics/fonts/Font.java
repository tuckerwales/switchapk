package android.graphics.fonts;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.LocaleList;
import android.os.ParcelFileDescriptor;
import android.util.TypedValue;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * One font file (or TTC face) with its style. Weight and slant come from the
 * builder or, as on Android, from the font's OS/2 table. Variation settings
 * are kept but switchapk draws the default instance.
 */
public final class Font {
    private final byte[] mData;
    private final File mFile;
    private final FontStyle mStyle;
    private final int mTtcIndex;
    private final FontVariationAxis[] mAxes;
    private long mNative;

    private Font(byte[] data, File file, FontStyle style, int ttcIndex, FontVariationAxis[] axes) {
        mData = data;
        mFile = file;
        mStyle = style;
        mTtcIndex = ttcIndex;
        mAxes = axes;
    }

    public ByteBuffer getBuffer() {
        return ByteBuffer.wrap(mData).asReadOnlyBuffer();
    }

    public File getFile() {
        return mFile;
    }

    public FontStyle getStyle() {
        return mStyle;
    }

    public int getTtcIndex() {
        return mTtcIndex;
    }

    public FontVariationAxis[] getAxes() {
        return mAxes == null ? null : mAxes.clone();
    }

    public LocaleList getLocaleList() {
        return LocaleList.getEmptyLocaleList();
    }

    /** framework-internal: the loaded font for drawing (0 if the bytes do not parse). */
    public synchronized long getNativePtr() {
        if (mNative == 0) mNative = android.graphics.Typeface.loadNativeFont(mData);
        return mNative;
    }

    public float getGlyphBounds(int glyphId, Paint paint, RectF outBoundingBox) {
        // Glyph ids are not exposed by the text engine; report an empty box.
        if (outBoundingBox != null) outBoundingBox.setEmpty();
        return 0;
    }

    public void getMetrics(Paint paint, Paint.FontMetrics outMetrics) {
        Paint p = new Paint(paint);
        p.setTypeface(android.graphics.Typeface.createFromFontForMetrics(this));
        p.getFontMetrics(outMetrics);
    }

    public int getSourceIdentifier() {
        return Arrays.hashCode(mData);
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Font)) return false;
        Font f = (Font) o;
        return f.mStyle.equals(mStyle) && f.mTtcIndex == mTtcIndex && Arrays.equals(f.mData, mData)
                && Arrays.equals(f.mAxes, mAxes);
    }

    public int hashCode() {
        return Arrays.hashCode(mData) * 31 + mStyle.hashCode();
    }

    public String toString() {
        return "Font {path=" + mFile + ", style=" + mStyle + ", ttcIndex=" + mTtcIndex + ", axes="
                + FontVariationAxis.toFontVariationSettings(mAxes) + "}";
    }

    /** Reads usWeightClass and the italic bit from the OS/2 table (head.macStyle as a fallback). */
    static FontStyle styleFromTables(byte[] d, int ttcIndex) {
        try {
            int base = 0;
            if (d.length >= 12 && d[0] == 't' && d[1] == 't' && d[2] == 'c' && d[3] == 'f') base = be32(d, 12 + 4 * ttcIndex);
            int numTables = be16(d, base + 4);
            int weight = FontStyle.FONT_WEIGHT_NORMAL;
            boolean italic = false;
            boolean haveOs2 = false;
            for (int i = 0; i < numTables; i++) {
                int rec = base + 12 + 16 * i;
                String tag = new String(d, rec, 4, "ISO-8859-1");
                int off = be32(d, rec + 8);
                if (tag.equals("OS/2")) {
                    weight = be16(d, off + 4);
                    italic = (be16(d, off + 62) & 1) != 0;
                    haveOs2 = true;
                } else if (tag.equals("head") && !haveOs2) {
                    italic = (be16(d, off + 44) & 2) != 0;
                }
            }
            if (weight < FontStyle.FONT_WEIGHT_MIN || weight > FontStyle.FONT_WEIGHT_MAX) weight = FontStyle.FONT_WEIGHT_NORMAL;
            return new FontStyle(weight, italic ? FontStyle.FONT_SLANT_ITALIC : FontStyle.FONT_SLANT_UPRIGHT);
        } catch (Exception e) {
            return new FontStyle();
        }
    }

    private static int be16(byte[] d, int o) {
        return ((d[o] & 0xff) << 8) | (d[o + 1] & 0xff);
    }

    private static int be32(byte[] d, int o) {
        return (be16(d, o) << 16) | be16(d, o + 2);
    }

    public static final class Builder {
        private byte[] mData;
        private File mFile;
        private IOException mError;
        private int mWeight = FontStyle.FONT_WEIGHT_UNSPECIFIED;
        private int mSlant = -1;
        private int mTtcIndex;
        private FontVariationAxis[] mAxes;

        public Builder(ByteBuffer buffer) {
            if (buffer == null) throw new NullPointerException("buffer can not be null");
            ByteBuffer b = buffer.duplicate();
            b.rewind();
            mData = new byte[b.remaining()];
            b.get(mData);
        }

        public Builder(File path) {
            mFile = path;
            try {
                mData = readAll(new FileInputStream(path));
            } catch (IOException e) {
                mError = e;
            }
        }

        public Builder(ParcelFileDescriptor fd) {
            this(fd, 0, -1);
        }

        public Builder(ParcelFileDescriptor fd, long offset, long size) {
            try {
                byte[] all = readAll(new FileInputStream(fd.getFileDescriptor()));
                int start = (int) Math.min(offset, all.length);
                int end = size < 0 ? all.length : (int) Math.min(all.length, offset + size);
                mData = Arrays.copyOfRange(all, start, end);
            } catch (IOException e) {
                mError = e;
            }
        }

        public Builder(AssetManager am, String path) {
            try {
                mData = readAll(am.open(path));
            } catch (IOException e) {
                mError = e;
            }
        }

        public Builder(Resources res, int resId) {
            try {
                TypedValue value = new TypedValue();
                res.getValue(resId, value, true);
                if (value.string == null) throw new IOException("Resource " + resId + " is not a font file");
                String file = value.string.toString();
                if (file.toLowerCase().endsWith(".xml")) throw new IOException("Resource " + resId + " is a font family XML, not a font file");
                mData = readAll(res.getAssets().openNonAsset(value.assetCookie, file));
            } catch (IOException e) {
                mError = e;
            } catch (Resources.NotFoundException e) {
                mError = new IOException(e);
            }
        }

        public Builder(Font font) {
            mData = font.mData;
            mFile = font.mFile;
            mWeight = font.mStyle.getWeight();
            mSlant = font.mStyle.getSlant();
            mTtcIndex = font.mTtcIndex;
            mAxes = font.mAxes;
        }

        private static byte[] readAll(InputStream in) throws IOException {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            } finally {
                in.close();
            }
        }

        public Builder setWeight(int weight) {
            if (weight < FontStyle.FONT_WEIGHT_MIN || weight > FontStyle.FONT_WEIGHT_MAX) throw new IllegalArgumentException("weight out of range: " + weight);
            mWeight = weight;
            return this;
        }

        public Builder setSlant(int slant) {
            mSlant = slant;
            return this;
        }

        public Builder setTtcIndex(int ttcIndex) {
            mTtcIndex = ttcIndex;
            return this;
        }

        public Builder setFontVariationSettings(String variationSettings) {
            mAxes = FontVariationAxis.fromFontVariationSettings(variationSettings);
            return this;
        }

        public Builder setFontVariationSettings(FontVariationAxis[] axes) {
            mAxes = axes == null ? null : axes.clone();
            return this;
        }

        public Font build() throws IOException {
            if (mError != null) throw mError;
            if (mData == null || mData.length == 0) throw new IOException("Font data is empty");
            FontStyle fromFile = styleFromTables(mData, mTtcIndex);
            FontStyle style = new FontStyle(mWeight != FontStyle.FONT_WEIGHT_UNSPECIFIED ? mWeight : fromFile.getWeight(),
                    mSlant >= 0 ? mSlant : fromFile.getSlant());
            Font f = new Font(mData, mFile, style, mTtcIndex, mAxes);
            if (f.getNativePtr() == 0) throw new IOException("Failed to read font contents");
            return f;
        }
    }
}
