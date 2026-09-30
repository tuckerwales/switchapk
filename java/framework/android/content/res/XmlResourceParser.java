package android.content.res;

import android.util.AttributeSet;
import org.xmlpull.v1.XmlPullParser;

public interface XmlResourceParser extends XmlPullParser, AttributeSet, AutoCloseable {
    String getAttributeNamespace(int index);
    void close();
}
