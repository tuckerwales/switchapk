package java.text;

public class FieldPosition {
    int field;
    int beginIndex;
    int endIndex;

    public FieldPosition(int field) {
        this.field = field;
    }

    public FieldPosition(Format.Field attribute) {
        this.field = 0;
    }

    public int getField() {
        return field;
    }

    public int getBeginIndex() {
        return beginIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public void setBeginIndex(int bi) {
        beginIndex = bi;
    }

    public void setEndIndex(int ei) {
        endIndex = ei;
    }
}
