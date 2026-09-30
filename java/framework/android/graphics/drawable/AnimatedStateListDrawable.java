package android.graphics.drawable;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/** State list whose transitions are shown as instant state changes. */
public class AnimatedStateListDrawable extends StateListDrawable {
    public AnimatedStateListDrawable() { super(); }

    public void addState(int[] stateSet, Drawable drawable, int id) { addState(stateSet, drawable); }

    public <T extends Drawable & Animatable> void addTransition(int fromId, int toId, T transition, boolean reversible) {}

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        final int innerDepth = parser.getDepth() + 1;
        int type, depth;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT && ((depth = parser.getDepth()) >= innerDepth || type != XmlPullParser.END_TAG)) {
            if (type != XmlPullParser.START_TAG || depth > innerDepth) continue;
            if (parser.getName().equals("item")) {
                final TypedArray a = obtainAttributes(r, theme, attrs, new int[] {android.R.attr.drawable});
                Drawable dr = a.getDrawable(0);
                a.recycle();
                final int[] states = extractStateSet(attrs);
                if (dr == null) {
                    while ((type = parser.next()) == XmlPullParser.TEXT) {}
                    if (type == XmlPullParser.START_TAG) dr = Drawable.createFromXmlInner(r, parser, attrs, theme);
                }
                if (dr != null) addState(states, dr);
            } else if (parser.getName().equals("transition")) {
                // skip the transition element and its children
                int tDepth = parser.getDepth();
                while ((type = parser.next()) != XmlPullParser.END_DOCUMENT && !(type == XmlPullParser.END_TAG && parser.getDepth() == tDepth)) {}
            }
        }
        onStateChange(getState());
    }
}
