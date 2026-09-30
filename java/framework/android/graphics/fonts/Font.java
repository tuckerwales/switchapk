package android.graphics.fonts;

public final class Font {
    public static final class Builder {
        public Builder(java.io.File path) {}
        public Builder(android.content.res.AssetManager am, String path) {}
        public Builder(android.content.res.Resources res, int resId) {}
        public Builder setWeight(int weight) { return this; }
        public Builder setSlant(int slant) { return this; }
        public Font build() { return new Font(); }
    }
}
