package java.text;

public final class StringCharacterIterator implements CharacterIterator {
    private String text;
    private int begin, end, pos;

    public StringCharacterIterator(String text) {
        this(text, 0);
    }

    public StringCharacterIterator(String text, int pos) {
        this(text, 0, text.length(), pos);
    }

    public StringCharacterIterator(String text, int begin, int end, int pos) {
        this.text = text;
        this.begin = begin;
        this.end = end;
        this.pos = pos;
    }

    public void setText(String text) {
        this.text = text;
        begin = 0;
        end = text.length();
        pos = 0;
    }

    public char first() {
        pos = begin;
        return current();
    }

    public char last() {
        pos = end > begin ? end - 1 : end;
        return current();
    }

    public char setIndex(int p) {
        pos = p;
        return current();
    }

    public char current() {
        return (pos >= begin && pos < end) ? text.charAt(pos) : DONE;
    }

    public char next() {
        if (pos < end - 1) {
            pos++;
            return text.charAt(pos);
        }
        pos = end;
        return DONE;
    }

    public char previous() {
        if (pos > begin) {
            pos--;
            return text.charAt(pos);
        }
        return DONE;
    }

    public int getBeginIndex() {
        return begin;
    }

    public int getEndIndex() {
        return end;
    }

    public int getIndex() {
        return pos;
    }

    public Object clone() {
        return new StringCharacterIterator(text, begin, end, pos);
    }
}
