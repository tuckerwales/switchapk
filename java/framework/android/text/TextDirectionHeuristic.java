package android.text;

/** Decides the paragraph direction of a run of text. */
public interface TextDirectionHeuristic {
    boolean isRtl(char[] array, int start, int count);

    boolean isRtl(CharSequence cs, int start, int count);
}
