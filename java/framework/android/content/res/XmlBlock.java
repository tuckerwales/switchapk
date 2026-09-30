package android.content.res;

import android.util.TypedValue;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import org.xmlpull.v1.XmlPullParserException;

/**
 * A compiled XML document flattened by the native side into event records:
 *   events: {type, line, name, ns, attrStart, attrCount} per event
 *   attrs:  {ns, name, resId, raw, type, data} per attribute
 * String fields are indices into strings (-1 = null).
 */
public final class XmlBlock {
    static final int EV_SIZE = 6;
    static final int ATTR_SIZE = 6;

    final int[] mEvents;
    final int[] mAttrs;
    final String[] mStrings;

    XmlBlock(int[] events, int[] attrs, String[] strings) {
        mEvents = events;
        mAttrs = attrs;
        mStrings = strings;
    }

    public Parser newParser() { return new Parser(this); }

    public final class Parser implements XmlResourceParser {
        private final XmlBlock mBlock;
        private int mEv = -1;         // index of current event record
        private int mEventType = START_DOCUMENT;
        private int mDepth;
        private boolean mDecNextDepth;
        private int mStartOfTag = -1; // record index of the enclosing START_TAG (for END_TAG attr queries)

        Parser(XmlBlock block) { mBlock = block; }

        private String str(int i) { return i < 0 || i >= mStrings.length ? null : mStrings[i]; }

        public void setFeature(String name, boolean state) throws XmlPullParserException {
            if (FEATURE_PROCESS_NAMESPACES.equals(name) && state) return;
            if (FEATURE_REPORT_NAMESPACE_ATTRIBUTES.equals(name) && state) return;
            throw new XmlPullParserException("Unsupported feature: " + name);
        }

        public boolean getFeature(String name) {
            return FEATURE_PROCESS_NAMESPACES.equals(name) || FEATURE_REPORT_NAMESPACE_ATTRIBUTES.equals(name);
        }

        public void setProperty(String name, Object value) throws XmlPullParserException {
            throw new XmlPullParserException("setProperty() not supported");
        }

        public Object getProperty(String name) { return null; }
        public void setInput(Reader in) throws XmlPullParserException { throw new XmlPullParserException("setInput() not supported"); }
        public void setInput(InputStream inputStream, String inputEncoding) throws XmlPullParserException { throw new XmlPullParserException("setInput() not supported"); }
        public void defineEntityReplacementText(String entityName, String replacementText) throws XmlPullParserException { throw new XmlPullParserException("defineEntityReplacementText() not supported"); }
        public String getNamespacePrefix(int pos) throws XmlPullParserException { return "android"; }
        public String getInputEncoding() { return null; }
        public String getNamespace(String prefix) { return "android".equals(prefix) ? "http://schemas.android.com/apk/res/android" : null; }
        public int getNamespaceCount(int depth) throws XmlPullParserException { return 0; }
        public String getNamespaceUri(int pos) throws XmlPullParserException { return null; }

        public String getPositionDescription() { return "Binary XML file line #" + getLineNumber(); }
        public int getLineNumber() { return mEv >= 0 && mEv * EV_SIZE < mEvents.length ? mEvents[mEv * EV_SIZE + 1] : -1; }
        public int getColumnNumber() { return -1; }

        public boolean isWhitespace() throws XmlPullParserException {
            String t = getText();
            if (t == null) return false;
            for (int i = 0; i < t.length(); i++) if (!Character.isWhitespace(t.charAt(i))) return false;
            return true;
        }

        public String getText() {
            if (mEventType != TEXT || mEv < 0) return null;
            return str(mEvents[mEv * EV_SIZE + 2]);
        }

        public char[] getTextCharacters(int[] holderForStartAndLength) {
            String txt = getText();
            if (txt == null) return null;
            holderForStartAndLength[0] = 0;
            holderForStartAndLength[1] = txt.length();
            return txt.toCharArray();
        }

        public String getNamespace() {
            if (mEventType != START_TAG && mEventType != END_TAG) return null;
            String ns = str(mEvents[mEv * EV_SIZE + 3]);
            return ns != null ? ns : "";
        }

        public String getName() {
            if (mEventType != START_TAG && mEventType != END_TAG) return null;
            return str(mEvents[mEv * EV_SIZE + 2]);
        }

        public String getPrefix() { throw new RuntimeException("getPrefix not supported"); }
        public boolean isEmptyElementTag() throws XmlPullParserException { return false; }

        private int attrBase(int index) {
            if (mEventType != START_TAG) throw new IndexOutOfBoundsException("Not on a start tag");
            int count = mEvents[mEv * EV_SIZE + 5];
            if (index < 0 || index >= count) throw new IndexOutOfBoundsException(String.valueOf(index));
            return (mEvents[mEv * EV_SIZE + 4] + index) * ATTR_SIZE;
        }

        public int getAttributeCount() { return mEventType == START_TAG ? mEvents[mEv * EV_SIZE + 5] : -1; }

        public String getAttributeNamespace(int index) {
            String ns = str(mAttrs[attrBase(index)]);
            return ns != null ? ns : "";
        }

        public String getAttributeName(int index) {
            String n = str(mAttrs[attrBase(index) + 1]);
            if (n == null) throw new IndexOutOfBoundsException(String.valueOf(index));
            return n;
        }

        public String getAttributePrefix(int index) { throw new RuntimeException("getAttributePrefix not supported"); }
        public String getAttributeType(int index) { return "CDATA"; }
        public boolean isAttributeDefault(int index) { return false; }

        public int getAttributeNameResource(int index) { return mAttrs[attrBase(index) + 2]; }
        /** Resolved value type (TypedValue.TYPE_*) of an attribute. */
        public int getAttributeDataType(int index) { return mAttrs[attrBase(index) + 4]; }
        /** Raw data of an attribute; for strings, the string index. */
        public int getAttributeData(int index) { return mAttrs[attrBase(index) + 5]; }

        public String getAttributeValue(int index) {
            int b = attrBase(index);
            String raw = str(mAttrs[b + 3]);
            if (raw != null) return raw;
            int type = mAttrs[b + 4];
            int data = mAttrs[b + 5];
            if (type == TypedValue.TYPE_STRING) return str(data);
            String s = TypedValue.coerceToString(type, data);
            return s;
        }

        /** CharSequence value for TypedArray (string attributes only). */
        CharSequence getAttributeStringValue(int index) {
            int b = attrBase(index);
            if (mAttrs[b + 4] == TypedValue.TYPE_STRING) return str(mAttrs[b + 5]);
            return str(mAttrs[b + 3]);
        }

        public String getAttributeValue(String namespace, String name) {
            int idx = getAttributeIndex(namespace, name);
            return idx >= 0 ? getAttributeValue(idx) : null;
        }

        int getAttributeIndex(String namespace, String name) {
            int n = getAttributeCount();
            for (int i = 0; i < n; i++) {
                int b = attrBase(i);
                if (!name.equals(str(mAttrs[b + 1]))) continue;
                String ns = str(mAttrs[b]);
                if (namespace == null || namespace.isEmpty() ? (ns == null || ns.isEmpty()) : namespace.equals(ns)) return i;
            }
            return -1;
        }

        int getAttributeIndexForResource(int resId) {
            int n = getAttributeCount();
            for (int i = 0; i < n; i++) if (mAttrs[attrBase(i) + 2] == resId) return i;
            return -1;
        }

        public int getEventType() throws XmlPullParserException { return mEventType; }

        public int next() throws XmlPullParserException, IOException {
            if (mDecNextDepth) {
                mDepth--;
                mDecNextDepth = false;
            }
            if (mEventType == END_DOCUMENT) return END_DOCUMENT;
            mEv++;
            if (mEv * EV_SIZE >= mEvents.length) {
                mEventType = END_DOCUMENT;
                return mEventType;
            }
            int t = mEvents[mEv * EV_SIZE];
            mEventType = t;
            if (t == START_TAG) mDepth++;
            else if (t == END_TAG) mDecNextDepth = true;
            return t;
        }

        public int nextToken() throws XmlPullParserException, IOException { return next(); }

        public void require(int type, String namespace, String name) throws XmlPullParserException, IOException {
            if (type != getEventType() || (namespace != null && !namespace.equals(getNamespace())) || (name != null && !name.equals(getName())))
                throw new XmlPullParserException("expected " + TYPES[type] + " " + getPositionDescription());
        }

        public String nextText() throws XmlPullParserException, IOException {
            if (getEventType() != START_TAG) throw new XmlPullParserException(getPositionDescription() + ": parser must be on START_TAG to read next text", this, null);
            int eventType = next();
            if (eventType == TEXT) {
                String result = getText();
                eventType = next();
                if (eventType != END_TAG) throw new XmlPullParserException(getPositionDescription() + ": event TEXT it must be immediately followed by END_TAG", this, null);
                return result;
            } else if (eventType == END_TAG) {
                return "";
            } else {
                throw new XmlPullParserException(getPositionDescription() + ": parser must be on START_TAG or TEXT to read text", this, null);
            }
        }

        public int nextTag() throws XmlPullParserException, IOException {
            int eventType = next();
            if (eventType == TEXT && isWhitespace()) eventType = next();
            if (eventType != START_TAG && eventType != END_TAG)
                throw new XmlPullParserException(getPositionDescription() + ": expected start or end tag", this, null);
            return eventType;
        }

        public int getDepth() { return mDepth; }

        public int getAttributeListValue(String namespace, String attribute, String[] options, int defaultValue) {
            int idx = getAttributeIndex(namespace, attribute);
            return idx >= 0 ? getAttributeListValue(idx, options, defaultValue) : defaultValue;
        }

        public boolean getAttributeBooleanValue(String namespace, String attribute, boolean defaultValue) {
            int idx = getAttributeIndex(namespace, attribute);
            return idx >= 0 ? getAttributeBooleanValue(idx, defaultValue) : defaultValue;
        }

        public int getAttributeResourceValue(String namespace, String attribute, int defaultValue) {
            int idx = getAttributeIndex(namespace, attribute);
            return idx >= 0 ? getAttributeResourceValue(idx, defaultValue) : defaultValue;
        }

        public int getAttributeIntValue(String namespace, String attribute, int defaultValue) {
            int idx = getAttributeIndex(namespace, attribute);
            return idx >= 0 ? getAttributeIntValue(idx, defaultValue) : defaultValue;
        }

        public int getAttributeUnsignedIntValue(String namespace, String attribute, int defaultValue) {
            int idx = getAttributeIndex(namespace, attribute);
            return idx >= 0 ? getAttributeUnsignedIntValue(idx, defaultValue) : defaultValue;
        }

        public float getAttributeFloatValue(String namespace, String attribute, float defaultValue) {
            int idx = getAttributeIndex(namespace, attribute);
            return idx >= 0 ? getAttributeFloatValue(idx, defaultValue) : defaultValue;
        }

        public int getAttributeListValue(int idx, String[] options, int defaultValue) {
            int t = getAttributeDataType(idx);
            int v = getAttributeData(idx);
            if (t == TypedValue.TYPE_STRING) {
                String s = str(v);
                for (int i = 0; i < options.length; i++) if (options[i].equals(s)) return i;
                return defaultValue;
            }
            return v;
        }

        public boolean getAttributeBooleanValue(int idx, boolean defaultValue) {
            int t = getAttributeDataType(idx);
            if (t >= TypedValue.TYPE_FIRST_INT && t <= TypedValue.TYPE_LAST_INT) return getAttributeData(idx) != 0;
            if (t == TypedValue.TYPE_STRING) return Boolean.parseBoolean(str(getAttributeData(idx)));
            return defaultValue;
        }

        public int getAttributeResourceValue(int idx, int defaultValue) {
            int t = getAttributeDataType(idx);
            if (t == TypedValue.TYPE_REFERENCE) return getAttributeData(idx);
            return defaultValue;
        }

        public int getAttributeIntValue(int idx, int defaultValue) {
            int t = getAttributeDataType(idx);
            if (t >= TypedValue.TYPE_FIRST_INT && t <= TypedValue.TYPE_LAST_INT) return getAttributeData(idx);
            if (t == TypedValue.TYPE_STRING) {
                try {
                    return Integer.decode(str(getAttributeData(idx)));
                } catch (Exception e) {
                    return defaultValue;
                }
            }
            return defaultValue;
        }

        public int getAttributeUnsignedIntValue(int idx, int defaultValue) { return getAttributeIntValue(idx, defaultValue); }

        public float getAttributeFloatValue(int idx, float defaultValue) {
            int t = getAttributeDataType(idx);
            if (t == TypedValue.TYPE_FLOAT) return Float.intBitsToFloat(getAttributeData(idx));
            if (t == TypedValue.TYPE_STRING) {
                try {
                    return Float.parseFloat(str(getAttributeData(idx)));
                } catch (Exception e) {
                    return defaultValue;
                }
            }
            if (t >= TypedValue.TYPE_FIRST_INT && t <= TypedValue.TYPE_LAST_INT) return getAttributeData(idx);
            return defaultValue;
        }

        public String getIdAttribute() {
            int i = getAttributeIndex(null, "id");
            return i >= 0 ? getAttributeValue(i) : null;
        }

        public String getClassAttribute() {
            int i = getAttributeIndex(null, "class");
            return i >= 0 ? getAttributeValue(i) : null;
        }

        public int getIdAttributeResourceValue(int defaultValue) {
            int i = getAttributeIndex(null, "id");
            return i >= 0 ? getAttributeResourceValue(i, defaultValue) : defaultValue;
        }

        public int getStyleAttribute() {
            int i = getAttributeIndex(null, "style");
            if (i < 0) return 0;
            int t = getAttributeDataType(i);
            return t == TypedValue.TYPE_REFERENCE || t == TypedValue.TYPE_ATTRIBUTE ? getAttributeData(i) : 0;
        }

        /** True if the style="" attribute is a theme attribute reference (?attr/...). */
        boolean isStyleAttributeThemeRef() {
            int i = getAttributeIndex(null, "style");
            return i >= 0 && getAttributeDataType(i) == TypedValue.TYPE_ATTRIBUTE;
        }

        public void close() {}

        XmlBlock block() { return mBlock; }
    }
}
