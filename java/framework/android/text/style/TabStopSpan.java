package android.text.style;

/** Paragraph style that sets a tab stop offset (AOSP TabStopSpan). */
public interface TabStopSpan extends ParagraphStyle {
    int getTabStop();

    class Standard implements TabStopSpan {
        private int mTabOffset;

        public Standard(int offset) { mTabOffset = offset; }

        public int getTabStop() { return mTabOffset; }

        @Override
        public String toString() { return "TabStopSpan.Standard{tabOffset=" + mTabOffset + "}"; }
    }
}
