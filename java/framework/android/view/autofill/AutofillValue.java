package android.view.autofill;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;

/** A value filled into a view: text, toggle, list index or date. */
public final class AutofillValue implements Parcelable {
    private final int mType;
    private final Object mValue;

    private AutofillValue(int type, Object value) {
        mType = type;
        mValue = value;
    }

    public CharSequence getTextValue() {
        if (!isText()) throw new IllegalStateException("value is not text: " + this);
        return (CharSequence) mValue;
    }
    public boolean isText() { return mType == View.AUTOFILL_TYPE_TEXT; }

    public boolean getToggleValue() {
        if (!isToggle()) throw new IllegalStateException("value is not a toggle: " + this);
        return (Boolean) mValue;
    }
    public boolean isToggle() { return mType == View.AUTOFILL_TYPE_TOGGLE; }

    public int getListValue() {
        if (!isList()) throw new IllegalStateException("value is not a list: " + this);
        return (Integer) mValue;
    }
    public boolean isList() { return mType == View.AUTOFILL_TYPE_LIST; }

    public long getDateValue() {
        if (!isDate()) throw new IllegalStateException("value is not a date: " + this);
        return (Long) mValue;
    }
    public boolean isDate() { return mType == View.AUTOFILL_TYPE_DATE; }

    @Override
    public int hashCode() { return mType + mValue.hashCode(); }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AutofillValue)) return false;
        AutofillValue other = (AutofillValue) obj;
        if (mType != other.mType) return false;
        if (isText()) return mValue.toString().equals(other.mValue.toString());
        return mValue.equals(other.mValue);
    }

    @Override
    public String toString() { return "[type=" + mType + ", value=" + mValue + "]"; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mType);
        switch (mType) {
            case View.AUTOFILL_TYPE_TEXT: out.writeCharSequence((CharSequence) mValue); break;
            case View.AUTOFILL_TYPE_TOGGLE: out.writeInt((Boolean) mValue ? 1 : 0); break;
            case View.AUTOFILL_TYPE_LIST: out.writeInt((Integer) mValue); break;
            case View.AUTOFILL_TYPE_DATE: out.writeLong((Long) mValue); break;
        }
    }

    public static final Parcelable.Creator<AutofillValue> CREATOR = new Parcelable.Creator<AutofillValue>() {
        public AutofillValue createFromParcel(Parcel in) {
            int type = in.readInt();
            switch (type) {
                case View.AUTOFILL_TYPE_TEXT: return forText(in.readCharSequence());
                case View.AUTOFILL_TYPE_TOGGLE: return forToggle(in.readInt() != 0);
                case View.AUTOFILL_TYPE_LIST: return forList(in.readInt());
                case View.AUTOFILL_TYPE_DATE: return forDate(in.readLong());
                default: throw new IllegalArgumentException("bad autofill type " + type);
            }
        }
        public AutofillValue[] newArray(int size) { return new AutofillValue[size]; }
    };

    public static AutofillValue forText(CharSequence value) {
        return value == null ? null : new AutofillValue(View.AUTOFILL_TYPE_TEXT, value.toString());
    }
    public static AutofillValue forToggle(boolean value) {
        return new AutofillValue(View.AUTOFILL_TYPE_TOGGLE, Boolean.valueOf(value));
    }
    public static AutofillValue forList(int value) {
        return new AutofillValue(View.AUTOFILL_TYPE_LIST, Integer.valueOf(value));
    }
    public static AutofillValue forDate(long value) {
        return new AutofillValue(View.AUTOFILL_TYPE_DATE, Long.valueOf(value));
    }
}
