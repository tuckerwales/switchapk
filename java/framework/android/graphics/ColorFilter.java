package android.graphics;

public class ColorFilter {
    /** Renderer mode (Porter-Duff numbering) and color, or -1 for filters applied in Java. */
    int mMode = -1;
    int mColor;

    /** Applies the filter to one ARGB color (used when the renderer cannot express it). */
    int filter(int color) { return color; }
}
