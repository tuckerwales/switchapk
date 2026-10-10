package android.preference;

import android.content.Context;
import android.content.Intent;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Xml;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Inflates preference hierarchies from XML (AOSP's PreferenceInflater over GenericInflater): tags without a package
 * are android.preference classes; <intent> and <extra> fill the enclosing preference. A child is added to its parent
 * before its own children are read, as GenericInflater does.
 */
class PreferenceInflater {
    private static final String INTENT_TAG_NAME = "intent";
    private static final String EXTRA_TAG_NAME = "extra";
    private static final String DEFAULT_PACKAGE = "android.preference.";
    private static final HashMap<String, Constructor<?>> sConstructors = new HashMap<String, Constructor<?>>();

    private final Context mContext;
    private final PreferenceManager mPreferenceManager;

    PreferenceInflater(Context context, PreferenceManager preferenceManager) {
        mContext = context;
        mPreferenceManager = preferenceManager;
    }

    Preference inflate(int resource, PreferenceGroup root, boolean attachToRoot) {
        XmlResourceParser parser = mContext.getResources().getXml(resource);
        try {
            return inflate(parser, root, attachToRoot);
        } finally {
            parser.close();
        }
    }

    Preference inflate(XmlPullParser parser, PreferenceGroup root, boolean attachToRoot) {
        final AttributeSet attrs = Xml.asAttributeSet(parser);
        Preference result = root;
        try {
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
                // skip to the root element
            }
            if (type != XmlPullParser.START_TAG) {
                throw new android.view.InflateException(parser.getPositionDescription() + ": No start tag found!");
            }
            Preference xmlRoot = createItemFromTag(parser.getName(), attrs);
            result = onMergeRoots(root, attachToRoot, (PreferenceGroup) xmlRoot);
            rInflate(parser, result, attrs);
        } catch (XmlPullParserException e) {
            throw new android.view.InflateException(e.getMessage(), e);
        } catch (IOException e) {
            throw new android.view.InflateException(parser.getPositionDescription() + ": " + e.getMessage(), e);
        }
        return result;
    }

    private PreferenceGroup onMergeRoots(PreferenceGroup givenRoot, boolean attachToGivenRoot,
            PreferenceGroup xmlRoot) {
        if (givenRoot == null) {
            xmlRoot.onAttachedToHierarchy(mPreferenceManager);
            return xmlRoot;
        }
        return givenRoot;
    }

    private Preference createItemFromTag(String name, AttributeSet attrs) {
        String className = name.indexOf('.') < 0 ? DEFAULT_PACKAGE + name : name;
        try {
            Constructor<?> constructor;
            synchronized (sConstructors) {
                constructor = sConstructors.get(className);
                if (constructor == null) {
                    Class<?> clazz = Class.forName(className, false, mContext.getClassLoader());
                    constructor = clazz.getConstructor(Context.class, AttributeSet.class);
                    sConstructors.put(className, constructor);
                }
            }
            return (Preference) constructor.newInstance(mContext, attrs);
        } catch (Exception e) {
            android.view.InflateException ie =
                    new android.view.InflateException(attrs.getPositionDescription() + ": Error inflating class "
                            + className);
            ie.initCause(e);
            throw ie;
        }
    }

    private void rInflate(XmlPullParser parser, Preference parent, AttributeSet attrs)
            throws XmlPullParserException, IOException {
        final int depth = parser.getDepth();
        int type;
        while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                && type != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) continue;
            final String name = parser.getName();
            if (INTENT_TAG_NAME.equals(name)) {
                final Intent intent;
                try {
                    intent = Intent.parseIntent(mContext.getResources(), parser, attrs);
                } catch (Exception e) {
                    XmlPullParserException ex = new XmlPullParserException("Error parsing preference");
                    ex.initCause(e);
                    throw ex;
                }
                parent.setIntent(intent);
            } else if (EXTRA_TAG_NAME.equals(name)) {
                mContext.getResources().parseBundleExtra(EXTRA_TAG_NAME, attrs, parent.getExtras());
                skipCurrentTag(parser);
            } else {
                final Preference item = createItemFromTag(name, attrs);
                if (!(parent instanceof PreferenceGroup)) {
                    throw new android.view.InflateException("Cannot add a child preference to "
                            + parent.getClass().getName() + ", which is not a PreferenceGroup");
                }
                ((PreferenceGroup) parent).addItemFromInflater(item);
                rInflate(parser, item, attrs);
            }
        }
    }

    private static void skipCurrentTag(XmlPullParser parser) throws XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            // nothing
        }
    }
}
