package java.text;

public abstract class Format implements java.io.Serializable, Cloneable {
    protected Format() {
    }

    public final String format(Object obj) {
        return format(obj, new StringBuffer(), new FieldPosition(0)).toString();
    }

    public abstract StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos);

    public abstract Object parseObject(String source, ParsePosition pos);

    public Object parseObject(String source) throws ParseException {
        ParsePosition pos = new ParsePosition(0);
        Object result = parseObject(source, pos);
        if (pos.index == 0) {
            throw new ParseException("Format.parseObject(String) failed", pos.errorIndex);
        }
        return result;
    }

    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    public static class Field extends java.text.AttributedCharacterIterator.Attribute {
        protected Field(String name) {
            super(name);
        }
    }
}
