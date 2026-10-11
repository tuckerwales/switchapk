package org.xmlpull.v1;

import java.util.HashMap;

public class XmlPullParserFactory {
    public static final String PROPERTY_NAME = "org.xmlpull.v1.XmlPullParserFactory";
    protected HashMap<String, Boolean> features = new HashMap<>();

    protected XmlPullParserFactory() {}

    public static XmlPullParserFactory newInstance() throws XmlPullParserException { return new XmlPullParserFactory(); }
    public static XmlPullParserFactory newInstance(String classNames, Class context) throws XmlPullParserException { return new XmlPullParserFactory(); }

    public void setFeature(String name, boolean state) throws XmlPullParserException { features.put(name, state); }
    public boolean getFeature(String name) { Boolean v = features.get(name); return v != null && v; }
    public void setNamespaceAware(boolean awareness) { features.put(XmlPullParser.FEATURE_PROCESS_NAMESPACES, awareness); }
    public boolean isNamespaceAware() { return getFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES); }
    public void setValidating(boolean validating) { features.put(XmlPullParser.FEATURE_VALIDATION, validating); }
    public boolean isValidating() { return getFeature(XmlPullParser.FEATURE_VALIDATION); }

    public XmlPullParser newPullParser() throws XmlPullParserException {
        XmlPullParser p = new SimpleXmlPullParser();
        for (java.util.Map.Entry<String, Boolean> e : features.entrySet()) {
            try {
                p.setFeature(e.getKey(), e.getValue());
            } catch (XmlPullParserException ignored) {}
        }
        return p;
    }

    public XmlSerializer newSerializer() throws XmlPullParserException { return new SimpleXmlSerializer(); }
}
