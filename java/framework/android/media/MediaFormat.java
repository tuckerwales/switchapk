package android.media;

import java.util.HashMap;

/**
 * A bag of media-key values (placeholder until WS7). Only the string keys
 * VideoView's subtitle path needs are stored.
 */
public final class MediaFormat {
    public static final String KEY_MIME = "mime";
    public static final String KEY_LANGUAGE = "language";

    private final HashMap<String, String> mStrings = new HashMap<String, String>();

    public MediaFormat() {}

    public MediaFormat(MediaFormat other) {
        if (other != null) mStrings.putAll(other.mStrings);
    }

    public static MediaFormat createSubtitleFormat(String mime, String language) {
        MediaFormat format = new MediaFormat();
        format.setString(KEY_MIME, mime);
        format.setString(KEY_LANGUAGE, language);
        return format;
    }

    public void setString(String name, String value) {
        mStrings.put(name, value);
    }

    public String getString(String name) {
        return mStrings.get(name);
    }

    public String getString(String name, String defaultValue) {
        String value = mStrings.get(name);
        return value != null ? value : defaultValue;
    }
}
