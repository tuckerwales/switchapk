package android.animation;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Log;
import android.util.StateSet;
import android.util.TypedValue;
import android.util.Xml;
import android.view.animation.AnimationUtils;
import java.util.ArrayList;
import org.xmlpull.v1.XmlPullParser;

/**
 * Loads animator, objectAnimator, set and state-list animator XML.
 * A pathData value is not morphed: the animator runs its timing and does not call the setter.
 */
public class AnimatorInflater {
    private static final String TAG = "AnimatorInflater";
    private static final int VALUE_TYPE_FLOAT = 0;
    private static final int VALUE_TYPE_INT = 1;
    private static final int VALUE_TYPE_COLOR = 3;
    private static final int TOGETHER = 0;

    public AnimatorInflater() {}

    public static Animator loadAnimator(Context context, int id) throws Resources.NotFoundException {
        return loadAnimator(context.getResources(), context.getTheme(), id);
    }

    /** Hidden AOSP overload. AnimatedVectorDrawable inflates with a Resources, not a Context. */
    public static Animator loadAnimator(Resources resources, Resources.Theme theme, int id)
            throws Resources.NotFoundException {
        XmlResourceParser parser = null;
        try {
            parser = resources.getAnimation(id);
            AttributeSet attrs = Xml.asAttributeSet(parser);
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {}
            if (type != XmlPullParser.START_TAG) {
                throw new Resources.NotFoundException("No animator in " + id);
            }
            return parseAnimator(resources, theme, parser, attrs);
        } catch (Resources.NotFoundException e) {
            throw e;
        } catch (Exception e) {
            Resources.NotFoundException rnf = new Resources.NotFoundException("Can't load animator resource ID #0x"
                    + Integer.toHexString(id));
            rnf.initCause(e);
            throw rnf;
        } finally {
            if (parser != null) parser.close();
        }
    }

    public static StateListAnimator loadStateListAnimator(Context context, int id) throws Resources.NotFoundException {
        return loadStateListAnimator(context.getResources(), context.getTheme(), id);
    }

    /** Hidden AOSP overload. */
    public static StateListAnimator loadStateListAnimator(Resources resources, Resources.Theme theme, int id)
            throws Resources.NotFoundException {
        XmlResourceParser parser = null;
        try {
            parser = resources.getAnimation(id);
            AttributeSet attrs = Xml.asAttributeSet(parser);
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {}
            if (type != XmlPullParser.START_TAG) {
                throw new Resources.NotFoundException("No state list animator in " + id);
            }
            StateListAnimator sla = new StateListAnimator();
            int depth = parser.getDepth();
            while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                    && type != XmlPullParser.END_DOCUMENT) {
                if (type != XmlPullParser.START_TAG || !"item".equals(parser.getName())) continue;
                int itemDepth = parser.getDepth();
                int[] spec = extractStateSet(attrs);
                Animator anim = null;
                while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > itemDepth)
                        && type != XmlPullParser.END_DOCUMENT) {
                    if (type != XmlPullParser.START_TAG) continue;
                    anim = parseAnimator(resources, theme, parser, attrs);
                }
                if (anim != null) sla.addState(spec, anim);
            }
            return sla;
        } catch (Resources.NotFoundException e) {
            throw e;
        } catch (Exception e) {
            Resources.NotFoundException rnf = new Resources.NotFoundException(
                    "Can't load state list animator resource ID #0x" + Integer.toHexString(id));
            rnf.initCause(e);
            throw rnf;
        } finally {
            if (parser != null) parser.close();
        }
    }

    private static Animator parseAnimator(Resources res, Resources.Theme theme, XmlPullParser parser,
            AttributeSet attrs) throws Exception {
        String name = parser.getName();
        int depth = parser.getDepth();
        if ("set".equals(name)) return parseSet(res, theme, parser, attrs, depth);
        if ("objectAnimator".equals(name) || "animator".equals(name)) {
            return parseValues(res, theme, parser, attrs, depth, "objectAnimator".equals(name));
        }
        Log.w(TAG, "Skipping animator tag " + name);
        consume(parser, depth);
        return null;
    }

    private static Animator parseSet(Resources res, Resources.Theme theme, XmlPullParser parser, AttributeSet attrs,
            int depth) throws Exception {
        TypedArray a = styled(res, theme, attrs, new int[] {android.R.attr.ordering, android.R.attr.duration,
                android.R.attr.startOffset, android.R.attr.interpolator});
        int ordering = a.getInt(0, TOGETHER);
        boolean hasDuration = a.hasValue(1);
        int duration = a.getInt(1, 0);
        int startOffset = a.getInt(2, 0);
        int interp = a.getResourceId(3, 0);
        a.recycle();
        ArrayList<Animator> children = new ArrayList<Animator>();
        int type;
        while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                && type != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) continue;
            Animator child = parseAnimator(res, theme, parser, attrs);
            if (child != null) children.add(child);
        }
        AnimatorSet set = new AnimatorSet();
        Animator[] arr = children.toArray(new Animator[children.size()]);
        if (ordering != TOGETHER) set.playSequentially(arr);
        else set.playTogether(arr);
        if (hasDuration) set.setDuration(duration);
        if (startOffset != 0) set.setStartDelay(startOffset);
        if (interp != 0) set.setInterpolator(AnimationUtils.loadInterpolator(res, theme, interp));
        return set;
    }

    private static Animator parseValues(Resources res, Resources.Theme theme, XmlPullParser parser,
            AttributeSet attrs, int depth, boolean object) throws Exception {
        TypedArray a = styled(res, theme, attrs, new int[] {
                android.R.attr.propertyName, android.R.attr.valueFrom, android.R.attr.valueTo, android.R.attr.valueType,
                android.R.attr.duration, android.R.attr.startOffset, android.R.attr.repeatCount,
                android.R.attr.repeatMode,
                android.R.attr.interpolator});
        String property = a.getString(0);
        int valueType = a.hasValue(3) ? a.getInt(3, VALUE_TYPE_FLOAT) : -1;
        boolean fromColor = false;
        boolean toColor = false;
        Object from = readValue(a, 1, valueType);
        Object to = readValue(a, 2, valueType);
        if (a.hasValue(1)) fromColor = isColor(a, 1, valueType);
        if (a.hasValue(2)) toColor = isColor(a, 2, valueType);
        boolean hasDuration = a.hasValue(4);
        int duration = a.getInt(4, 300);
        int startOffset = a.getInt(5, 0);
        int repeatCount = a.getInt(6, 0);
        int repeatMode = a.getInt(7, ValueAnimator.RESTART);
        int interp = a.getResourceId(8, 0);
        a.recycle();

        ArrayList<PropertyValuesHolder> holders = new ArrayList<PropertyValuesHolder>();
        int type;
        while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                && type != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) continue;
            if ("propertyValuesHolder".equals(parser.getName())) {
                PropertyValuesHolder holder = parseHolder(res, theme, parser, attrs);
                if (holder != null) holders.add(holder);
            } else {
                consume(parser, parser.getDepth());
            }
        }

        ValueAnimator anim = object ? new ObjectAnimator() : new ValueAnimator();
        if (object && property != null && holders.isEmpty()) ((ObjectAnimator) anim).setPropertyName(property);
        if (!holders.isEmpty()) {
            anim.setValues(holders.toArray(new PropertyValuesHolder[holders.size()]));
        } else if (from instanceof String || to instanceof String) {
            Log.w(TAG, "pathData animation is not applied");
            anim.setFloatValues(0f, 1f);
        } else {
            boolean color = valueType == VALUE_TYPE_COLOR || fromColor || toColor;
            applyEndpoints(anim, from, to, color || valueType == VALUE_TYPE_INT ? false : true, color);
        }
        if (hasDuration) anim.setDuration(duration);
        if (startOffset != 0) anim.setStartDelay(startOffset);
        if (repeatCount != 0) anim.setRepeatCount(repeatCount);
        if (repeatMode != ValueAnimator.RESTART) anim.setRepeatMode(repeatMode);
        if (interp != 0) anim.setInterpolator(AnimationUtils.loadInterpolator(res, theme, interp));
        return anim;
    }

    private static void applyEndpoints(ValueAnimator anim, Object from, Object to, boolean asFloat, boolean color) {
        if (color) {
            int end = to instanceof Number ? ((Number) to).intValue() : 0;
            if (from instanceof Number) anim.setIntValues(((Number) from).intValue(), end);
            else if (to != null) anim.setIntValues(end);
            anim.setEvaluator(new ArgbEvaluator());
            return;
        }
        boolean ints = from instanceof Integer || to instanceof Integer;
        ints = ints && !(from instanceof Float || to instanceof Float);
        if (!asFloat && ints) {
            int end = to instanceof Number ? ((Number) to).intValue() : 0;
            if (from instanceof Number) anim.setIntValues(((Number) from).intValue(), end);
            else if (to != null) anim.setIntValues(end);
            return;
        }
        float end = to instanceof Number ? ((Number) to).floatValue() : 0f;
        if (from instanceof Number) anim.setFloatValues(((Number) from).floatValue(), end);
        else if (to != null) anim.setFloatValues(end);
    }

    private static PropertyValuesHolder parseHolder(Resources res, Resources.Theme theme, XmlPullParser parser,
            AttributeSet attrs) throws Exception {
        int depth = parser.getDepth();
        TypedArray a = styled(res, theme, attrs, new int[] {
                android.R.attr.propertyName, android.R.attr.valueFrom, android.R.attr.valueTo,
                android.R.attr.valueType});
        String property = a.getString(0);
        int valueType = a.hasValue(3) ? a.getInt(3, VALUE_TYPE_FLOAT) : -1;
        Object from = readValue(a, 1, valueType);
        Object to = readValue(a, 2, valueType);
        boolean color = valueType == VALUE_TYPE_COLOR || (a.hasValue(1) && isColor(a, 1, valueType))
                || (a.hasValue(2) && isColor(a, 2, valueType));
        a.recycle();
        ArrayList<Keyframe> frames = new ArrayList<Keyframe>();
        int type;
        while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                && type != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) continue;
            if ("keyframe".equals(parser.getName())) {
                Keyframe frame = parseKeyframe(res, theme, parser, attrs, valueType, color);
                if (frame != null) frames.add(frame);
            } else {
                consume(parser, parser.getDepth());
            }
        }
        if (from instanceof String || to instanceof String) {
            Log.w(TAG, "pathData property " + property + " is not applied");
            PropertyValuesHolder skip = PropertyValuesHolder.ofFloat(property != null ? property : "", 0f, 1f);
            return skip;
        }
        if (!frames.isEmpty()) {
            PropertyValuesHolder framed = PropertyValuesHolder.ofKeyframe(property != null ? property : "",
                    frames.toArray(new Keyframe[frames.size()]));
            if (color) framed.setEvaluator(new ArgbEvaluator());
            return framed;
        }
        PropertyValuesHolder holder;
        if (color) {
            int end = to instanceof Number ? ((Number) to).intValue() : 0;
            if (from instanceof Number) holder = PropertyValuesHolder.ofInt(property, ((Number) from).intValue(), end);
            else holder = PropertyValuesHolder.ofInt(property, end);
            holder.setEvaluator(new ArgbEvaluator());
            return holder;
        }
        boolean asFloat = valueType != VALUE_TYPE_INT && !(from instanceof Integer && to instanceof Integer);
        if (valueType == VALUE_TYPE_FLOAT || from instanceof Float || to instanceof Float) asFloat = true;
        if (asFloat) {
            float end = to instanceof Number ? ((Number) to).floatValue() : 0f;
            if (from instanceof Number) {
                return PropertyValuesHolder.ofFloat(property, ((Number) from).floatValue(), end);
            }
            return PropertyValuesHolder.ofFloat(property, end);
        }
        int end = to instanceof Number ? ((Number) to).intValue() : 0;
        if (from instanceof Number) return PropertyValuesHolder.ofInt(property, ((Number) from).intValue(), end);
        return PropertyValuesHolder.ofInt(property, end);
    }

    private static Keyframe parseKeyframe(Resources res, Resources.Theme theme, XmlPullParser parser,
            AttributeSet attrs, int valueType, boolean color) throws Exception {
        int depth = parser.getDepth();
        TypedArray a = styled(res, theme, attrs, new int[] {
                android.R.attr.fraction, android.R.attr.value, android.R.attr.interpolator, android.R.attr.valueType});
        float fraction = a.getFloat(0, 0f);
        int localType = a.hasValue(3) ? a.getInt(3, valueType) : valueType;
        boolean hasValue = a.hasValue(1);
        boolean frameColor = color || localType == VALUE_TYPE_COLOR || (hasValue && isColor(a, 1, localType));
        Object value = hasValue ? readValue(a, 1, localType == -1 && frameColor ? VALUE_TYPE_COLOR : localType) : null;
        int interp = a.getResourceId(2, 0);
        a.recycle();
        consume(parser, depth);
        Keyframe frame;
        if (!hasValue || value == null) {
            frame = frameColor || localType == VALUE_TYPE_INT ? Keyframe.ofInt(fraction) : Keyframe.ofFloat(fraction);
        } else if (frameColor || value instanceof Integer) {
            frame = Keyframe.ofInt(fraction, ((Number) value).intValue());
        } else {
            frame = Keyframe.ofFloat(fraction, ((Number) value).floatValue());
        }
        if (interp != 0) frame.setInterpolator(AnimationUtils.loadInterpolator(res, theme, interp));
        return frame;
    }

    private static Object readValue(TypedArray a, int index, int valueType) {
        if (!a.hasValue(index)) return null;
        TypedValue tv = a.peekValue(index);
        if (tv == null) return null;
        if (valueType == VALUE_TYPE_COLOR || tv.isColorType()) return Integer.valueOf(a.getColor(index, 0));
        if (tv.type == TypedValue.TYPE_STRING) return a.getString(index);
        if (valueType == VALUE_TYPE_FLOAT || tv.type == TypedValue.TYPE_FLOAT || tv.type == TypedValue.TYPE_DIMENSION
                || tv.type == TypedValue.TYPE_FRACTION) {
            if (tv.type == TypedValue.TYPE_DIMENSION) return Float.valueOf(a.getDimension(index, 0f));
            return Float.valueOf(a.getFloat(index, 0f));
        }
        if (valueType == VALUE_TYPE_INT) return Integer.valueOf(a.getInt(index, 0));
        if (tv.type >= TypedValue.TYPE_FIRST_INT && tv.type <= TypedValue.TYPE_LAST_INT) {
            return Integer.valueOf(a.getInt(index, 0));
        }
        return Float.valueOf(a.getFloat(index, 0f));
    }

    private static boolean isColor(TypedArray a, int index, int valueType) {
        if (valueType == VALUE_TYPE_COLOR) return true;
        TypedValue tv = a.peekValue(index);
        return tv != null && tv.isColorType();
    }

    private static int[] extractStateSet(AttributeSet attrs) {
        int count = attrs.getAttributeCount();
        int[] states = new int[count];
        int j = 0;
        for (int i = 0; i < count; i++) {
            int id = attrs.getAttributeNameResource(i);
            if (id == 0) continue;
            states[j++] = attrs.getAttributeBooleanValue(i, false) ? id : -id;
        }
        return StateSet.trimStateSet(states, j);
    }

    private static TypedArray styled(Resources res, Resources.Theme theme, AttributeSet attrs, int[] style) {
        if (theme != null) return theme.obtainStyledAttributes(attrs, style, 0, 0);
        return res.obtainAttributes(attrs, style);
    }

    /** Parser is on a start tag. Leaves it on that tag's end tag. */
    private static void consume(XmlPullParser parser, int depth) throws Exception {
        int type;
        while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                && type != XmlPullParser.END_DOCUMENT) {
        }
    }
}
