package android.graphics.drawable;

/** Maps XML tags to drawable classes. */
final class DrawableInflater {
    static Drawable inflateFromTag(String name) {
        switch (name) {
            case "selector": return new StateListDrawable();
            case "animated-selector": return new AnimatedStateListDrawable();
            case "level-list": return new LevelListDrawable();
            case "layer-list": return new LayerDrawable();
            case "transition": return new TransitionDrawable();
            case "ripple": return new RippleDrawable();
            case "adaptive-icon": return new AdaptiveIconDrawable();
            case "color": return new ColorDrawable();
            case "shape": return new GradientDrawable();
            case "vector": return new VectorDrawable();
            case "animated-vector": return new AnimatedVectorDrawable();
            case "scale": return new ScaleDrawable();
            case "clip": return new ClipDrawable();
            case "rotate": return new RotateDrawable();
            case "animated-rotate": return new AnimatedRotateDrawable();
            case "animation-list": return new AnimationDrawable();
            case "inset": return new InsetDrawable();
            case "bitmap": return new BitmapDrawable();
            case "nine-patch": return new NinePatchDrawable();
            case "animated-image": return new AnimatedImageDrawable();
            default: return null;
        }
    }

    private DrawableInflater() {}
}
