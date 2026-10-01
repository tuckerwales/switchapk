package android.text;

import android.view.View;
import java.util.Locale;

/** Standard text direction heuristics (port of AOSP TextDirectionHeuristics). */
public class TextDirectionHeuristics {
    public static final TextDirectionHeuristic LTR = new TextDirectionHeuristicInternal(null, false);
    public static final TextDirectionHeuristic RTL = new TextDirectionHeuristicInternal(null, true);
    public static final TextDirectionHeuristic FIRSTSTRONG_LTR = new TextDirectionHeuristicInternal(FirstStrong.INSTANCE, false);
    public static final TextDirectionHeuristic FIRSTSTRONG_RTL = new TextDirectionHeuristicInternal(FirstStrong.INSTANCE, true);
    public static final TextDirectionHeuristic ANYRTL_LTR = new TextDirectionHeuristicInternal(AnyStrong.INSTANCE_RTL, false);
    public static final TextDirectionHeuristic LOCALE = TextDirectionHeuristicLocale.INSTANCE;

    private static final int STATE_TRUE = 0;
    private static final int STATE_FALSE = 1;
    private static final int STATE_UNKNOWN = 2;

    public TextDirectionHeuristics() {}

    private static int isRtlCodePoint(int codePoint) {
        switch (Character.getDirectionality(codePoint)) {
            case Character.DIRECTIONALITY_LEFT_TO_RIGHT:
                return STATE_FALSE;
            case Character.DIRECTIONALITY_RIGHT_TO_LEFT:
            case Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC:
                return STATE_TRUE;
            case Character.DIRECTIONALITY_UNDEFINED:
                if ((0x0590 <= codePoint && codePoint <= 0x08FF) || (0xFB1D <= codePoint && codePoint <= 0xFDCF)
                        || (0xFDF0 <= codePoint && codePoint <= 0xFDFF) || (0xFE70 <= codePoint && codePoint <= 0xFEFF)
                        || (0x10800 <= codePoint && codePoint <= 0x10FFF)
                        || (0x1E800 <= codePoint && codePoint <= 0x1EFFF)) {
                    return STATE_TRUE;
                } else if ((0x2065 <= codePoint && codePoint <= 0x2069) || (0xFFF0 <= codePoint && codePoint <= 0xFFF8)
                        || (0xE0000 <= codePoint && codePoint <= 0xE0FFF) || (0xFDD0 <= codePoint && codePoint <= 0xFDEF)
                        || ((codePoint & 0xFFFE) == 0xFFFE) || (0x2060 <= codePoint && codePoint <= 0x206F)) {
                    return STATE_UNKNOWN;
                } else {
                    return STATE_FALSE;
                }
            default:
                return STATE_UNKNOWN;
        }
    }

    private abstract static class TextDirectionHeuristicImpl implements TextDirectionHeuristic {
        private final TextDirectionAlgorithm mAlgorithm;

        TextDirectionHeuristicImpl(TextDirectionAlgorithm algorithm) { mAlgorithm = algorithm; }

        abstract protected boolean defaultIsRtl();

        public boolean isRtl(char[] array, int start, int count) { return isRtl(new String(array), start, count); }

        public boolean isRtl(CharSequence cs, int start, int count) {
            if (cs == null || start < 0 || count < 0 || cs.length() - count < start) throw new IllegalArgumentException();
            if (mAlgorithm == null) return defaultIsRtl();
            return doCheck(cs, start, count);
        }

        private boolean doCheck(CharSequence cs, int start, int count) {
            switch (mAlgorithm.checkRtl(cs, start, count)) {
                case STATE_TRUE: return true;
                case STATE_FALSE: return false;
                default: return defaultIsRtl();
            }
        }
    }

    private static class TextDirectionHeuristicInternal extends TextDirectionHeuristicImpl {
        private final boolean mDefaultIsRtl;

        private TextDirectionHeuristicInternal(TextDirectionAlgorithm algorithm, boolean defaultIsRtl) {
            super(algorithm);
            mDefaultIsRtl = defaultIsRtl;
        }

        @Override
        protected boolean defaultIsRtl() { return mDefaultIsRtl; }
    }

    private interface TextDirectionAlgorithm {
        int checkRtl(CharSequence cs, int start, int count);
    }

    private static class FirstStrong implements TextDirectionAlgorithm {
        static final FirstStrong INSTANCE = new FirstStrong();

        public int checkRtl(CharSequence cs, int start, int count) {
            int result = STATE_UNKNOWN;
            final int end = start + count;
            for (int i = start; i < end && result == STATE_UNKNOWN;) {
                final int cp = Character.codePointAt(cs, i);
                result = isRtlCodePoint(cp);
                i += Character.charCount(cp);
            }
            return result;
        }
    }

    private static class AnyStrong implements TextDirectionAlgorithm {
        static final AnyStrong INSTANCE_RTL = new AnyStrong(true);
        private final boolean mLookForRtl;

        private AnyStrong(boolean lookForRtl) { mLookForRtl = lookForRtl; }

        public int checkRtl(CharSequence cs, int start, int count) {
            boolean haveUnlookedFor = false;
            final int end = start + count;
            for (int i = start; i < end;) {
                final int cp = Character.codePointAt(cs, i);
                switch (isRtlCodePoint(cp)) {
                    case STATE_TRUE:
                        if (mLookForRtl) return STATE_TRUE;
                        haveUnlookedFor = true;
                        break;
                    case STATE_FALSE:
                        if (!mLookForRtl) return STATE_FALSE;
                        haveUnlookedFor = true;
                        break;
                    default:
                        break;
                }
                i += Character.charCount(cp);
            }
            if (haveUnlookedFor) return mLookForRtl ? STATE_FALSE : STATE_TRUE;
            return STATE_UNKNOWN;
        }
    }

    private static class TextDirectionHeuristicLocale extends TextDirectionHeuristicImpl {
        static final TextDirectionHeuristicLocale INSTANCE = new TextDirectionHeuristicLocale();

        TextDirectionHeuristicLocale() { super(null); }

        @Override
        protected boolean defaultIsRtl() {
            return TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL;
        }
    }
}
