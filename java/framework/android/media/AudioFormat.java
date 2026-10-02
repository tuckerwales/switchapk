package android.media;

import android.os.Parcel;
import android.os.Parcelable;

/** PCM format description. Encodings other than PCM are not played. */
public final class AudioFormat implements Parcelable {
    public static final int CHANNEL_INVALID = 0;
    public static final int CHANNEL_CONFIGURATION_DEFAULT = 1;
    public static final int CHANNEL_CONFIGURATION_INVALID = 0;
    public static final int CHANNEL_CONFIGURATION_MONO = 2;
    public static final int CHANNEL_CONFIGURATION_STEREO = 3;
    public static final int CHANNEL_OUT_DEFAULT = 1;
    public static final int CHANNEL_OUT_FRONT_LEFT = 4;
    public static final int CHANNEL_OUT_FRONT_RIGHT = 8;
    public static final int CHANNEL_OUT_FRONT_CENTER = 16;
    public static final int CHANNEL_OUT_MONO = 4;
    public static final int CHANNEL_OUT_STEREO = 12;
    public static final int ENCODING_INVALID = 0;
    public static final int ENCODING_DEFAULT = 1;
    public static final int ENCODING_PCM_16BIT = 2;
    public static final int ENCODING_PCM_8BIT = 3;
    public static final int ENCODING_PCM_FLOAT = 4;
    public static final int ENCODING_PCM_32BIT = 22;
    public static final int SAMPLE_RATE_UNSPECIFIED = 0;

    private int mEncoding = ENCODING_INVALID;
    private int mSampleRate;
    private int mChannelMask;
    private int mChannelIndexMask;

    private AudioFormat() {}

    public int getEncoding() { return mEncoding; }
    public int getSampleRate() { return mSampleRate; }
    public int getChannelMask() { return mChannelMask; }
    public int getChannelIndexMask() { return mChannelIndexMask; }

    public int getChannelCount() {
        int mask = mChannelMask != 0 ? mChannelMask : mChannelIndexMask;
        if (mask == 0) return 0;
        if (mask == CHANNEL_OUT_DEFAULT) return 2;
        return Integer.bitCount(mask);
    }

    public int getFrameSizeInBytes() {
        int bps;
        switch (mEncoding) {
            case ENCODING_PCM_8BIT: bps = 1; break;
            case ENCODING_PCM_16BIT:
            case ENCODING_DEFAULT: bps = 2; break;
            case ENCODING_PCM_FLOAT:
            case ENCODING_PCM_32BIT: bps = 4; break;
            default: throw new IllegalArgumentException("bad encoding " + mEncoding);
        }
        int ch = getChannelCount();
        return ch == 0 ? 0 : bps * ch;
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mEncoding);
        dest.writeInt(mSampleRate);
        dest.writeInt(mChannelMask);
        dest.writeInt(mChannelIndexMask);
    }

    public static final Creator<AudioFormat> CREATOR = new Creator<AudioFormat>() {
        public AudioFormat createFromParcel(Parcel in) {
            AudioFormat f = new AudioFormat();
            f.mEncoding = in.readInt();
            f.mSampleRate = in.readInt();
            f.mChannelMask = in.readInt();
            f.mChannelIndexMask = in.readInt();
            return f;
        }
        public AudioFormat[] newArray(int size) { return new AudioFormat[size]; }
    };

    public static class Builder {
        private int mEncoding = ENCODING_INVALID;
        private int mSampleRate;
        private int mChannelMask;
        private int mChannelIndexMask;

        public Builder() {}

        public Builder(AudioFormat format) {
            mEncoding = format.mEncoding;
            mSampleRate = format.mSampleRate;
            mChannelMask = format.mChannelMask;
            mChannelIndexMask = format.mChannelIndexMask;
        }

        public Builder setEncoding(int encoding) {
            if (!validEncoding(encoding)) throw new IllegalArgumentException("encoding " + encoding);
            mEncoding = encoding;
            return this;
        }

        public Builder setSampleRate(int sampleRate) {
            if (sampleRate != SAMPLE_RATE_UNSPECIFIED && (sampleRate < 4000 || sampleRate > 192000)) {
                throw new IllegalArgumentException("sampleRate " + sampleRate);
            }
            mSampleRate = sampleRate;
            return this;
        }

        public Builder setChannelMask(int channelMask) {
            mChannelMask = channelMask;
            return this;
        }

        public Builder setChannelIndexMask(int channelIndexMask) {
            mChannelIndexMask = channelIndexMask;
            return this;
        }

        public AudioFormat build() {
            AudioFormat f = new AudioFormat();
            f.mEncoding = mEncoding;
            f.mSampleRate = mSampleRate;
            f.mChannelMask = mChannelMask;
            f.mChannelIndexMask = mChannelIndexMask;
            return f;
        }
    }

    static boolean validEncoding(int encoding) {
        return encoding == ENCODING_DEFAULT || encoding == ENCODING_PCM_16BIT || encoding == ENCODING_PCM_8BIT
                || encoding == ENCODING_PCM_FLOAT || encoding == ENCODING_PCM_32BIT;
    }
}
