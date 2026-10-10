package android.media;

import java.util.ArrayList;
import java.util.List;

/**
 * An audio device. The only one is the built-in speaker output ({@link #SPEAKER}): 48 kHz
 * stereo 16-bit and float PCM, which is what the mixer takes.
 */
public final class AudioDeviceInfo {
    public static final int TYPE_AUX_LINE = 19;
    public static final int TYPE_BLE_BROADCAST = 30;
    public static final int TYPE_BLE_HEADSET = 26;
    public static final int TYPE_BLE_SPEAKER = 27;
    public static final int TYPE_BLUETOOTH_A2DP = 8;
    public static final int TYPE_BLUETOOTH_SCO = 7;
    public static final int TYPE_BUILTIN_EARPIECE = 1;
    public static final int TYPE_BUILTIN_MIC = 15;
    public static final int TYPE_BUILTIN_SPEAKER = 2;
    public static final int TYPE_BUILTIN_SPEAKER_SAFE = 24;
    public static final int TYPE_BUS = 21;
    public static final int TYPE_DOCK = 13;
    public static final int TYPE_DOCK_ANALOG = 31;
    public static final int TYPE_FM = 14;
    public static final int TYPE_FM_TUNER = 16;
    public static final int TYPE_HDMI = 9;
    public static final int TYPE_HDMI_ARC = 10;
    public static final int TYPE_HDMI_EARC = 29;
    public static final int TYPE_HEARING_AID = 23;
    public static final int TYPE_IP = 20;
    public static final int TYPE_LINE_ANALOG = 5;
    public static final int TYPE_LINE_DIGITAL = 6;
    public static final int TYPE_REMOTE_SUBMIX = 25;
    public static final int TYPE_TELEPHONY = 18;
    public static final int TYPE_TV_TUNER = 17;
    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_USB_ACCESSORY = 12;
    public static final int TYPE_USB_DEVICE = 11;
    public static final int TYPE_USB_HEADSET = 22;
    public static final int TYPE_WIRED_HEADPHONES = 4;
    public static final int TYPE_WIRED_HEADSET = 3;

    /** framework-internal. The console speaker (TV or handheld speakers). */
    static final AudioDeviceInfo SPEAKER = new AudioDeviceInfo(2, TYPE_BUILTIN_SPEAKER, true);

    private final int mId;
    private final int mType;
    private final boolean mSink;

    AudioDeviceInfo(int id, int type, boolean sink) {
        mId = id;
        mType = type;
        mSink = sink;
    }

    @Override
    public boolean equals(Object o) { return o instanceof AudioDeviceInfo && ((AudioDeviceInfo) o).mId == mId; }

    @Override
    public int hashCode() { return mId; }

    public int getId() { return mId; }
    public CharSequence getProductName() { return android.os.Build.MODEL; }
    public String getAddress() { return ""; }
    public boolean isSource() { return !mSink; }
    public boolean isSink() { return mSink; }
    public int[] getSampleRates() { return new int[] {48000}; }
    public int[] getChannelMasks() { return new int[] {AudioFormat.CHANNEL_OUT_MONO, AudioFormat.CHANNEL_OUT_STEREO}; }
    public int[] getChannelIndexMasks() { return new int[0]; }
    public int[] getChannelCounts() { return new int[] {1, 2}; }
    public int[] getEncodings() { return new int[] {AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_FLOAT}; }
    public List getAudioProfiles() { return new ArrayList<Object>(); }
    public List getAudioDescriptors() { return new ArrayList<Object>(); }
    public int[] getEncapsulationModes() { return new int[0]; }
    public int[] getEncapsulationMetadataTypes() { return new int[0]; }
    public int getType() { return mType; }
}
