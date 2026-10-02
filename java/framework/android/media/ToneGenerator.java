package android.media;

/** Sine and DTMF tones on the shared mixer. Unknown ids play a 400 Hz beep. */
public class ToneGenerator {
    public static final int MIN_VOLUME = 0;
    public static final int MAX_VOLUME = 100;
    public static final int TONE_DTMF_0 = 0;
    public static final int TONE_DTMF_1 = 1;
    public static final int TONE_DTMF_2 = 2;
    public static final int TONE_DTMF_3 = 3;
    public static final int TONE_DTMF_4 = 4;
    public static final int TONE_DTMF_5 = 5;
    public static final int TONE_DTMF_6 = 6;
    public static final int TONE_DTMF_7 = 7;
    public static final int TONE_DTMF_8 = 8;
    public static final int TONE_DTMF_9 = 9;
    public static final int TONE_DTMF_S = 10;
    public static final int TONE_DTMF_P = 11;
    public static final int TONE_DTMF_A = 12;
    public static final int TONE_DTMF_B = 13;
    public static final int TONE_DTMF_C = 14;
    public static final int TONE_DTMF_D = 15;
    public static final int TONE_SUP_DIAL = 16;
    public static final int TONE_SUP_BUSY = 17;
    public static final int TONE_SUP_CONGESTION = 18;
    public static final int TONE_SUP_RADIO_ACK = 19;
    public static final int TONE_SUP_RADIO_NOTAVAIL = 20;
    public static final int TONE_SUP_ERROR = 21;
    public static final int TONE_SUP_CALL_WAITING = 22;
    public static final int TONE_SUP_RINGTONE = 23;
    public static final int TONE_PROP_BEEP = 24;
    public static final int TONE_PROP_ACK = 25;
    public static final int TONE_PROP_NACK = 26;
    public static final int TONE_PROP_PROMPT = 27;
    public static final int TONE_PROP_BEEP2 = 28;
    public static final int TONE_SUP_INTERCEPT = 29;
    public static final int TONE_SUP_INTERCEPT_ABBREV = 30;
    public static final int TONE_SUP_CONGESTION_ABBREV = 31;
    public static final int TONE_SUP_CONFIRM = 32;
    public static final int TONE_SUP_PIP = 33;
    public static final int TONE_CDMA_DIAL_TONE_LITE = 34;
    public static final int TONE_CDMA_NETWORK_USA_RINGBACK = 35;
    public static final int TONE_CDMA_INTERCEPT = 36;
    public static final int TONE_CDMA_ABBR_INTERCEPT = 37;
    public static final int TONE_CDMA_REORDER = 38;
    public static final int TONE_CDMA_ABBR_REORDER = 39;
    public static final int TONE_CDMA_NETWORK_BUSY = 40;
    public static final int TONE_CDMA_CONFIRM = 41;
    public static final int TONE_CDMA_ANSWER = 42;
    public static final int TONE_CDMA_NETWORK_CALLWAITING = 43;
    public static final int TONE_CDMA_PIP = 44;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_NORMAL = 45;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_INTERGROUP = 46;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_SP_PRI = 47;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_PAT3 = 48;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_PING_RING = 49;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_PAT5 = 50;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_PAT6 = 51;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_PAT7 = 52;
    public static final int TONE_CDMA_HIGH_L = 53;
    public static final int TONE_CDMA_MED_L = 54;
    public static final int TONE_CDMA_LOW_L = 55;
    public static final int TONE_CDMA_HIGH_SS = 56;
    public static final int TONE_CDMA_MED_SS = 57;
    public static final int TONE_CDMA_LOW_SS = 58;
    public static final int TONE_CDMA_HIGH_SSL = 59;
    public static final int TONE_CDMA_MED_SSL = 60;
    public static final int TONE_CDMA_LOW_SSL = 61;
    public static final int TONE_CDMA_HIGH_SS_2 = 62;
    public static final int TONE_CDMA_MED_SS_2 = 63;
    public static final int TONE_CDMA_LOW_SS_2 = 64;
    public static final int TONE_CDMA_HIGH_SLS = 65;
    public static final int TONE_CDMA_MED_SLS = 66;
    public static final int TONE_CDMA_LOW_SLS = 67;
    public static final int TONE_CDMA_HIGH_S_X4 = 68;
    public static final int TONE_CDMA_MED_S_X4 = 69;
    public static final int TONE_CDMA_LOW_S_X4 = 70;
    public static final int TONE_CDMA_HIGH_PBX_L = 71;
    public static final int TONE_CDMA_MED_PBX_L = 72;
    public static final int TONE_CDMA_LOW_PBX_L = 73;
    public static final int TONE_CDMA_HIGH_PBX_SS = 74;
    public static final int TONE_CDMA_MED_PBX_SS = 75;
    public static final int TONE_CDMA_LOW_PBX_SS = 76;
    public static final int TONE_CDMA_HIGH_PBX_SSL = 77;
    public static final int TONE_CDMA_MED_PBX_SSL = 78;
    public static final int TONE_CDMA_LOW_PBX_SSL = 79;
    public static final int TONE_CDMA_HIGH_PBX_SLS = 80;
    public static final int TONE_CDMA_MED_PBX_SLS = 81;
    public static final int TONE_CDMA_LOW_PBX_SLS = 82;
    public static final int TONE_CDMA_HIGH_PBX_S_X4 = 83;
    public static final int TONE_CDMA_MED_PBX_S_X4 = 84;
    public static final int TONE_CDMA_LOW_PBX_S_X4 = 85;
    public static final int TONE_CDMA_ALERT_NETWORK_LITE = 86;
    public static final int TONE_CDMA_ALERT_AUTOREDIAL_LITE = 87;
    public static final int TONE_CDMA_ONE_MIN_BEEP = 88;
    public static final int TONE_CDMA_KEYPAD_VOLUME_KEY_LITE = 89;
    public static final int TONE_CDMA_PRESSHOLDKEY_LITE = 90;
    public static final int TONE_CDMA_ALERT_INCALL_LITE = 91;
    public static final int TONE_CDMA_EMERGENCY_RINGBACK = 92;
    public static final int TONE_CDMA_ALERT_CALL_GUARD = 93;
    public static final int TONE_CDMA_SOFT_ERROR_LITE = 94;
    public static final int TONE_CDMA_CALLDROP_LITE = 95;
    public static final int TONE_CDMA_NETWORK_BUSY_ONE_SHOT = 96;
    public static final int TONE_CDMA_ABBR_ALERT = 97;
    public static final int TONE_CDMA_SIGNAL_OFF = 98;

    private static final float[] DTMF_ROW = {697f, 770f, 852f, 941f};
    private static final float[] DTMF_COL = {1209f, 1336f, 1477f, 1633f};

    private final int mStream;
    private final float mGain;
    private int mVoice;
    private boolean mReleased;

    public ToneGenerator(int streamType, int volume) {
        if (volume < MIN_VOLUME || volume > MAX_VOLUME) throw new IllegalArgumentException("volume");
        mStream = streamType;
        mGain = volume / 100f;
    }

    public boolean startTone(int toneType) { return startTone(toneType, -1); }

    public boolean startTone(int toneType, int durationMs) {
        if (mReleased) return false;
        stopTone();
        if (toneType == TONE_CDMA_SIGNAL_OFF) return true;
        float a = 400f;
        float b = 0f;
        if (toneType >= TONE_DTMF_0 && toneType <= TONE_DTMF_D) {
            int row;
            int col;
            switch (toneType) {
                case TONE_DTMF_0: row = 3; col = 1; break;
                case TONE_DTMF_S: row = 3; col = 0; break;
                case TONE_DTMF_P: row = 3; col = 2; break;
                case TONE_DTMF_A: row = 0; col = 3; break;
                case TONE_DTMF_B: row = 1; col = 3; break;
                case TONE_DTMF_C: row = 2; col = 3; break;
                case TONE_DTMF_D: row = 3; col = 3; break;
                default:
                    row = (toneType - 1) / 3;
                    col = (toneType - 1) % 3;
                    break;
            }
            a = DTMF_ROW[row];
            b = DTMF_COL[col];
        } else if (toneType == TONE_PROP_BEEP2) {
            a = 800f;
        } else if (toneType == TONE_PROP_ACK) {
            a = 1200f;
        } else if (toneType == TONE_PROP_NACK) {
            a = 200f;
        } else if (toneType == TONE_PROP_PROMPT) {
            a = 600f;
        }
        mVoice = nativeTone(mStream, mGain, a, b, durationMs);
        return mVoice != 0;
    }

    public void stopTone() {
        if (mVoice != 0) {
            nativeRelease(mVoice);
            mVoice = 0;
        }
    }

    public void release() {
        stopTone();
        mReleased = true;
    }

    public int getAudioSessionId() { return 0; }

    private static native int nativeTone(int stream, float gain, float freqA, float freqB, int durationMs);
    private static native void nativeRelease(int id);
}
