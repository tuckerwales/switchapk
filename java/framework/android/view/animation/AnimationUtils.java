package android.view.animation;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;

/**
 * AnimationUtils. Interpolators load here. XML tweens load {@code set}, {@code alpha}, {@code scale},
 * {@code rotate} and {@code translate}. Other tags load as an identity AlphaAnimation with the
 * declared duration, so callers get an animation that changes nothing.
 */
public class AnimationUtils {
    public AnimationUtils() {}

    public static long currentAnimationTimeMillis() { return SystemClock.uptimeMillis(); }

    public static Animation loadAnimation(Context context, int id) throws Resources.NotFoundException {
        XmlResourceParser parser = null;
        try {
            parser = context.getResources().getAnimation(id);
            // The parser starts before the first tag. Do not skip to the root here: the recursive
            // walk reads that start tag itself.
            AttributeSet attrs = Xml.asAttributeSet(parser);
            Animation anim = createAnimationFromXml(context, parser, null, attrs);
            if (anim == null) throw new Resources.NotFoundException("No animation in " + id);
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

    private static Animation createAnimationFromXml(Context c, XmlPullParser parser, AnimationSet parent,
            AttributeSet attrs) throws Exception {
        Animation anim = null;
        int type;
        int depth = parser.getDepth();
        while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                && type != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) continue;
            String name = parser.getName();
            if ("set".equals(name)) {
                anim = new AnimationSet(c, attrs);
                createAnimationFromXml(c, parser, (AnimationSet) anim, attrs);
            } else if ("alpha".equals(name)) {
                anim = new AlphaAnimation(c, attrs);
            } else if ("scale".equals(name)) {
                anim = new ScaleAnimation(c, attrs);
            } else if ("rotate".equals(name)) {
                anim = new RotateAnimation(c, attrs);
            } else if ("translate".equals(name)) {
                anim = new TranslateAnimation(c, attrs);
            } else {
                android.util.Log.w("AnimationUtils", "Unsupported animation " + name + ", using an identity alpha");
                anim = new AlphaAnimation(c, attrs);
            }
            if (parent != null) parent.addAnimation(anim);
        }
        return anim;
    }

    public static LayoutAnimationController loadLayoutAnimation(Context context, int id)
            throws Resources.NotFoundException {
        XmlResourceParser parser = null;
        try {
            parser = context.getResources().getAnimation(id);
            AttributeSet attrs = Xml.asAttributeSet(parser);
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {}
            if (type != XmlPullParser.START_TAG) {
                throw new Resources.NotFoundException("No layout animation in " + id);
            }
            if ("gridLayoutAnimation".equals(parser.getName())) {
                return new GridLayoutAnimationController(context, attrs);
            }
            return new LayoutAnimationController(context, attrs);
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
        return loadInterpolator(context.getResources(), context.getTheme(), id);
    }

    /** framework-internal. Animator XML is inflated from a Resources, without a Context. */
    public static Interpolator loadInterpolator(Resources resources, Resources.Theme theme, int id)
            throws Resources.NotFoundException {
        XmlResourceParser parser = null;
        try {
            parser = resources.getAnimation(id);
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {}
            if (type != XmlPullParser.START_TAG) throw new Resources.NotFoundException("No interpolator in " + id);
            AttributeSet attrs = Xml.asAttributeSet(parser);
            String name = parser.getName();
            if ("linearInterpolator".equals(name)) return new LinearInterpolator();
            if ("accelerateInterpolator".equals(name)) {
                TypedArray a = obtain(resources, theme, attrs, new int[] {android.R.attr.factor});
                float factor = a.getFloat(0, 1f);
                a.recycle();
                return new AccelerateInterpolator(factor);
            }
            if ("decelerateInterpolator".equals(name)) {
                TypedArray a = obtain(resources, theme, attrs, new int[] {android.R.attr.factor});
                float factor = a.getFloat(0, 1f);
                a.recycle();
                return new DecelerateInterpolator(factor);
            }
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

    private static TypedArray obtain(Resources resources, Resources.Theme theme, AttributeSet attrs, int[] style) {
        if (theme != null) return theme.obtainStyledAttributes(attrs, style, 0, 0);
        return resources.obtainAttributes(attrs, style);
    }
}
