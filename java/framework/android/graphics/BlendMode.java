package android.graphics;

public enum BlendMode {
    CLEAR(0), SRC(1), DST(2), SRC_OVER(3), DST_OVER(4), SRC_IN(5), DST_IN(6), SRC_OUT(7), DST_OUT(8), SRC_ATOP(9),
    DST_ATOP(10), XOR(11), PLUS(12), MODULATE(13), SCREEN(14), OVERLAY(15), DARKEN(16), LIGHTEN(17), COLOR_DODGE(18),
    COLOR_BURN(19), HARD_LIGHT(20), SOFT_LIGHT(21), DIFFERENCE(22), EXCLUSION(23), MULTIPLY(24), HUE(25), SATURATION(26),
    COLOR(27), LUMINOSITY(28);

    final int mode;

    BlendMode(int mode) { this.mode = mode; }

    /** Maps to the renderer's Porter-Duff mode numbering. */
    int toPorterDuff() {
        switch (this) {
            case PLUS: return PorterDuff.Mode.ADD.nativeInt;
            case MODULATE: case MULTIPLY: return PorterDuff.Mode.MULTIPLY.nativeInt;
            case SCREEN: return PorterDuff.Mode.SCREEN.nativeInt;
            case OVERLAY: return PorterDuff.Mode.OVERLAY.nativeInt;
            case DARKEN: return PorterDuff.Mode.DARKEN.nativeInt;
            case LIGHTEN: return PorterDuff.Mode.LIGHTEN.nativeInt;
            default: return mode <= 11 ? mode : PorterDuff.Mode.SRC_OVER.nativeInt;
        }
    }

    static BlendMode fromPorterDuff(PorterDuff.Mode m) {
        switch (m) {
            case ADD: return PLUS;
            case MULTIPLY: return MODULATE;
            case SCREEN: return SCREEN;
            case OVERLAY: return OVERLAY;
            case DARKEN: return DARKEN;
            case LIGHTEN: return LIGHTEN;
            default: return values()[m.nativeInt];
        }
    }
}
