package android.view.inputmethod;

import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Extra information an input method attaches to committed or composing text. */
public final class TextAttribute implements Parcelable {
    private final List<String> mTextConversionSuggestions;
    private final PersistableBundle mExtras;

    private TextAttribute(List<String> suggestions, PersistableBundle extras) {
        mTextConversionSuggestions = suggestions;
        mExtras = extras;
    }

    public List<String> getTextConversionSuggestions() { return mTextConversionSuggestions; }

    public PersistableBundle getExtras() { return mExtras; }

    public static final class Builder {
        private List<String> mSuggestions = new ArrayList<String>();
        private PersistableBundle mExtras = new PersistableBundle();

        public Builder() {}

        public Builder setTextConversionSuggestions(List<String> suggestions) {
            mSuggestions = Collections.unmodifiableList(suggestions);
            return this;
        }

        public Builder setExtras(PersistableBundle extras) {
            mExtras = extras;
            return this;
        }

        public TextAttribute build() { return new TextAttribute(mSuggestions, mExtras); }
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(mTextConversionSuggestions);
        dest.writeValue(mExtras);
    }

    public static final Parcelable.Creator<TextAttribute> CREATOR = new Parcelable.Creator<TextAttribute>() {
        @SuppressWarnings("unchecked")
        public TextAttribute createFromParcel(Parcel source) {
            return new TextAttribute((List<String>) source.readValue(null), (PersistableBundle) source.readValue(null));
        }

        public TextAttribute[] newArray(int size) { return new TextAttribute[size]; }
    };
}
