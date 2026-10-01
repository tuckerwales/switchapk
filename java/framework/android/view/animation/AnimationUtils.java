package android.view.animation;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;

/**
 * AnimationUtils. Interpolators load here; of the XML tween animations only {@code <alpha>} parses so
 * far (WS5 adds translate, scale, rotate and set). Other tags load as an identity AlphaAnimation with
 * the declared duration, so callers get an animation that changes nothing.
 */
public class AnimationUtils {
    public AnimationUtils() {}

    public static long currentAnimationTimeMillis() { return SystemClock.uptimeMillis(); }

    public static Animation loadAnimation(Context context, int id) throws Resources.NotFoundException {
        XmlResourceParser parser = null;
        try {
            parser = context.getResources().getAnimation(id);
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {}
            if (type != XmlPullParser.START_TAG) throw new Resources.NotFoundException("No animation in " + id);
            AttributeSet attrs = Xml.asAttributeSet(parser);
            String name = parser.getName();
            if ("alpha".equals(name)) return new AlphaAnimation(context, attrs);
            // TODO(WS5) translate, scale, rotate, set, cliprect, extend.
            android.util.Log.w("AnimationUtils", "Unsupported animation " + name + ", using an identity alpha");
            Animation anim = new AlphaAnimation(context, attrs);
            return anim;
        } catch (Resources.NotFoundException e) {
            throw e;
        } catch (Exception e) {
            Resources.NotFoundException rnf = new Resources.NotFoundException("Can't load animation resource ID #0x"
                    + Integer.toHexString(id));
            rnf.initCause(e);
            throw rnf;
        } finally {
            if (parser != null) parser.close();
        }
    }

    public static Interpolator loadInterpolator(Context context, int id) throws Resources.NotFoundException {
        XmlResourceParser parser = null;
        try {
            parser = context.getResources().getAnimation(id);
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {}
            if (type != XmlPullParser.START_TAG) throw new Resources.NotFoundException("No interpolator in " + id);
            AttributeSet attrs = Xml.asAttributeSet(parser);
            String name = parser.getName();
            if ("linearInterpolator".equals(name)) return new LinearInterpolator();
            if ("accelerateInterpolator".equals(name)) return new AccelerateInterpolator(context, attrs);
            if ("decelerateInterpolator".equals(name)) return new DecelerateInterpolator(context, attrs);
            if ("accelerateDecelerateInterpolator".equals(name)) return new AccelerateDecelerateInterpolator();
            // TODO(WS5) cycle, anticipate, overshoot, bounce and path interpolators.
            android.util.Log.w("AnimationUtils", "Unsupported interpolator " + name + ", using linear");
            return new LinearInterpolator();
        } catch (Resources.NotFoundException e) {
            throw e;
        } catch (Exception e) {
            Resources.NotFoundException rnf = new Resources.NotFoundException("Can't load animation resource ID #0x"
                    + Integer.toHexString(id));
            rnf.initCause(e);
            throw rnf;
        } finally {
            if (parser != null) parser.close();
        }
    }
}
