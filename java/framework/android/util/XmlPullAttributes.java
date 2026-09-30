package android.util;

import org.xmlpull.v1.XmlPullParser;

/** AttributeSet over a plain XmlPullParser (no resource resolution). */
class XmlPullAttributes implements AttributeSet {
    final XmlPullParser mParser;

    XmlPullAttributes(XmlPullParser parser) { mParser = parser; }

    public int getAttributeCount() { return mParser.getAttributeCount(); }
    public String getAttributeNamespace(int index) { return mParser.getAttributeNamespace(index); }
    public String getAttributeName(int index) { return mParser.getAttributeName(index); }
    public String getAttributeValue(int index) { return mParser.getAttributeValue(index); }
    public String getAttributeValue(String namespace, String name) { return mParser.getAttributeValue(namespace, name); }
    public String getPositionDescription() { return mParser.getPositionDescription(); }
    public int getAttributeNameResource(int index) { return 0; }

    public int getAttributeListValue(String namespace, String attribute, String[] options, int defaultValue) {
        return list(getAttributeValue(namespace, attribute), options, defaultValue);
    }

    public boolean getAttributeBooleanValue(String namespace, String attribute, boolean defaultValue) {
        String v = getAttributeValue(namespace, attribute);
        return v == null ? defaultValue : Boolean.parseBoolean(v);
    }

    public int getAttributeResourceValue(String namespace, String attribute, int defaultValue) { return defaultValue; }

    public int getAttributeIntValue(String namespace, String attribute, int defaultValue) {
        return toInt(getAttributeValue(namespace, attribute), defaultValue);
    }

    public int getAttributeUnsignedIntValue(String namespace, String attribute, int defaultValue) {
        return toInt(getAttributeValue(namespace, attribute), defaultValue);
    }

    public float getAttributeFloatValue(String namespace, String attribute, float defaultValue) {
        String v = getAttributeValue(namespace, attribute);
        return v == null ? defaultValue : Float.parseFloat(v);
    }

    public int getAttributeListValue(int index, String[] options, int defaultValue) {
        return list(getAttributeValue(index), options, defaultValue);
    }

    public boolean getAttributeBooleanValue(int index, boolean defaultValue) {
        String v = getAttributeValue(index);
        return v == null ? defaultValue : Boolean.parseBoolean(v);
    }

    public int getAttributeResourceValue(int index, int defaultValue) { return defaultValue; }
    public int getAttributeIntValue(int index, int defaultValue) { return toInt(getAttributeValue(index), defaultValue); }
    public int getAttributeUnsignedIntValue(int index, int defaultValue) { return toInt(getAttributeValue(index), defaultValue); }

    public float getAttributeFloatValue(int index, float defaultValue) {
        String v = getAttributeValue(index);
        return v == null ? defaultValue : Float.parseFloat(v);
    }

    public String getIdAttribute() { return getAttributeValue(null, "id"); }
    public String getClassAttribute() { return getAttributeValue(null, "class"); }
    public int getIdAttributeResourceValue(int defaultValue) { return defaultValue; }
    public int getStyleAttribute() { return 0; }

    static int list(String v, String[] options, int def) {
        if (v == null) return def;
        for (int i = 0; i < options.length; i++) if (options[i].equals(v)) return i;
        return def;
    }

    static int toInt(String v, int def) {
        if (v == null) return def;
        try {
            if (v.startsWith("0x") || v.startsWith("0X")) return (int) Long.parseLong(v.substring(2), 16);
            if (v.startsWith("#")) return (int) Long.parseLong(v.substring(1), 16);
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
