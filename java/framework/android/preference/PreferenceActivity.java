package android.preference;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Port of AOSP's PreferenceActivity (WS4), single pane (the console's 480 dp-wide screen is below AOSP's dual-pane
 * threshold): it lists headers (onBuildHeaders) or a legacy preference hierarchy (addPreferencesFromResource), and a
 * header's fragment opens in a new instance of the activity with EXTRA_SHOW_FRAGMENT, as AOSP's single pane does.
 */
@Deprecated
public abstract class PreferenceActivity extends ListActivity
        implements PreferenceManager.OnPreferenceTreeClickListener,
        PreferenceFragment.OnPreferenceStartFragmentCallback {
    private static final String HEADERS_TAG = ":android:headers";
    private static final String CUR_HEADER_TAG = ":android:cur_header";
    private static final String PREFERENCES_TAG = ":android:preferences";

    public static final String EXTRA_SHOW_FRAGMENT = ":android:show_fragment";
    public static final String EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":android:show_fragment_args";
    public static final String EXTRA_SHOW_FRAGMENT_TITLE = ":android:show_fragment_title";
    public static final String EXTRA_SHOW_FRAGMENT_SHORT_TITLE = ":android:show_fragment_short_title";
    public static final String EXTRA_NO_HEADERS = ":android:no_headers";
    private static final String BACK_STACK_PREFS = ":android:prefs";
    private static final String EXTRA_PREFS_SHOW_BUTTON_BAR = "extra_prefs_show_button_bar";

    public static final long HEADER_ID_UNDEFINED = -1;

    private static final int FIRST_REQUEST_CODE = 100;
    private static final int MSG_BIND_PREFERENCES = 1;
    private static final int MSG_BUILD_HEADERS = 2;
    private static final int PREFS_FRAME_ID = 0x00ff0010;

    private final ArrayList<Header> mHeaders = new ArrayList<Header>();
    private FrameLayout mListFooter;
    private ViewGroup mPrefsContainer;
    private View mListContainer;
    private boolean mSinglePane = true;
    private Header mCurHeader;
    private PreferenceManager mPreferenceManager;
    private Bundle mSavedInstanceState;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_BIND_PREFERENCES:
                bindPreferences();
                break;
            case MSG_BUILD_HEADERS:
                ArrayList<Header> oldHeaders = new ArrayList<Header>(mHeaders);
                mHeaders.clear();
                onBuildHeaders(mHeaders);
                if (mAdapter instanceof ArrayAdapter) ((ArrayAdapter<?>) mAdapter).notifyDataSetChanged();
                break;
            }
        }
    };

    private static class HeaderAdapter extends ArrayAdapter<Header> {
        private final LayoutInflater mInflater;
        private final int mLayoutResId;

        HeaderAdapter(Context context, List<Header> objects, int layoutResId) {
            super(context, 0, objects);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mLayoutResId = layoutResId;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView : mInflater.inflate(mLayoutResId, parent, false);
            Header header = getItem(position);
            ImageView icon = (ImageView) view.findViewById(android.R.id.icon);
            TextView title = (TextView) view.findViewById(android.R.id.title);
            TextView summary = (TextView) view.findViewById(android.R.id.summary);
            if (icon != null) {
                if (header.iconRes == 0) {
                    icon.setVisibility(View.GONE);
                } else {
                    icon.setVisibility(View.VISIBLE);
                    icon.setImageResource(header.iconRes);
                }
            }
            if (title != null) title.setText(header.getTitle(getContext().getResources()));
            if (summary != null) {
                CharSequence summaryText = header.getSummary(getContext().getResources());
                if (!TextUtils.isEmpty(summaryText)) {
                    summary.setVisibility(View.VISIBLE);
                    summary.setText(summaryText);
                } else {
                    summary.setVisibility(View.GONE);
                }
            }
            return view;
        }
    }

    public static final class Header implements Parcelable {
        public long id = HEADER_ID_UNDEFINED;
        public int titleRes;
        public CharSequence title;
        public int summaryRes;
        public CharSequence summary;
        public int breadCrumbTitleRes;
        public CharSequence breadCrumbTitle;
        public int breadCrumbShortTitleRes;
        public CharSequence breadCrumbShortTitle;
        public int iconRes;
        public String fragment;
        public Bundle fragmentArguments;
        public Intent intent;
        public Bundle extras;

        public Header() {}

        public CharSequence getTitle(Resources res) {
            if (titleRes != 0) return res.getText(titleRes);
            return title;
        }

        public CharSequence getSummary(Resources res) {
            if (summaryRes != 0) return res.getText(summaryRes);
            return summary;
        }

        public CharSequence getBreadCrumbTitle(Resources res) {
            if (breadCrumbTitleRes != 0) return res.getText(breadCrumbTitleRes);
            return breadCrumbTitle;
        }

        public CharSequence getBreadCrumbShortTitle(Resources res) {
            if (breadCrumbShortTitleRes != 0) return res.getText(breadCrumbShortTitleRes);
            return breadCrumbShortTitle;
        }

        public int describeContents() { return 0; }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(id);
            dest.writeInt(titleRes);
            TextUtils.writeToParcel(title, dest, flags);
            dest.writeInt(summaryRes);
            TextUtils.writeToParcel(summary, dest, flags);
            dest.writeInt(breadCrumbTitleRes);
            TextUtils.writeToParcel(breadCrumbTitle, dest, flags);
            dest.writeInt(breadCrumbShortTitleRes);
            TextUtils.writeToParcel(breadCrumbShortTitle, dest, flags);
            dest.writeInt(iconRes);
            dest.writeString(fragment);
            dest.writeBundle(fragmentArguments);
            if (intent != null) {
                dest.writeInt(1);
                intent.writeToParcel(dest, flags);
            } else {
                dest.writeInt(0);
            }
            dest.writeBundle(extras);
        }

        public void readFromParcel(Parcel in) {
            id = in.readLong();
            titleRes = in.readInt();
            title = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            summaryRes = in.readInt();
            summary = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            breadCrumbTitleRes = in.readInt();
            breadCrumbTitle = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            breadCrumbShortTitleRes = in.readInt();
            breadCrumbShortTitle = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            iconRes = in.readInt();
            fragment = in.readString();
            fragmentArguments = in.readBundle();
            if (in.readInt() != 0) intent = Intent.CREATOR.createFromParcel(in);
            extras = in.readBundle();
        }

        Header(Parcel in) { readFromParcel(in); }

        public static final Creator<Header> CREATOR = new Creator<Header>() {
            public Header createFromParcel(Parcel source) { return new Header(source); }
            public Header[] newArray(int size) { return new Header[size]; }
        };
    }

    public PreferenceActivity() {}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) { return super.onOptionsItemSelected(item); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildContent();
        mSinglePane = !onIsMultiPane();
        final String initialFragment = getIntent().getStringExtra(EXTRA_SHOW_FRAGMENT);
        final Bundle initialArguments = getIntent().getBundleExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS);
        if (savedInstanceState != null) {
            ArrayList<Header> headers = savedInstanceState.getParcelableArrayList(HEADERS_TAG);
            if (headers != null) {
                mHeaders.addAll(headers);
                int curHeader = savedInstanceState.getInt(CUR_HEADER_TAG, (int) HEADER_ID_UNDEFINED);
                if (curHeader >= 0 && curHeader < mHeaders.size()) mCurHeader = mHeaders.get(curHeader);
            }
        } else if (!onIsHidingHeaders()) {
            onBuildHeaders(mHeaders);
        }
        if (initialFragment != null) {
            showFragmentPane();
            switchToHeaderInner(initialFragment, initialArguments);
            CharSequence initialTitle = getIntent().getCharSequenceExtra(EXTRA_SHOW_FRAGMENT_TITLE);
            int initialTitleRes = getIntent().getIntExtra(EXTRA_SHOW_FRAGMENT_TITLE, 0);
            if (initialTitleRes != 0) setTitle(getText(initialTitleRes));
            else if (initialTitle != null) setTitle(initialTitle);
        } else if (mHeaders.size() > 0) {
            int layout = getResources().getIdentifier("preference_header_item", "layout", "android");
            if (layout == 0) layout = android.R.layout.simple_list_item_2;
            setListAdapter(new HeaderAdapter(this, mHeaders, layout));
        }
        // with no headers and no fragment, the list shows a legacy hierarchy (addPreferencesFromResource)
    }

    /** The list for headers or legacy preferences, and a frame for a fragment; one of them shows. */
    private void buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        ListView list = new ListView(this);
        list.setId(android.R.id.list);
        listContainer.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        mListFooter = new FrameLayout(this);
        mListFooter.setVisibility(View.GONE);
        listContainer.addView(mListFooter, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(listContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        FrameLayout prefs = new FrameLayout(this);
        prefs.setId(PREFS_FRAME_ID);
        prefs.setVisibility(View.GONE);
        root.addView(prefs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        mListContainer = listContainer;
        mPrefsContainer = prefs;
        setContentView(root);
    }

    private void showFragmentPane() {
        mListContainer.setVisibility(View.GONE);
        mPrefsContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() { super.onBackPressed(); }

    public boolean hasHeaders() { return getListView().getVisibility() == View.VISIBLE && mPreferenceManager == null; }

    /** framework-internal (hidden in AOSP) */
    public List<Header> getHeaders() { return mHeaders; }

    public boolean isMultiPane() { return !mSinglePane; }

    /** AOSP asks the preferences_prefer_dual_pane resource, true from 720 dp: the console's screen is 480 dp. */
    public boolean onIsMultiPane() {
        return getResources().getConfiguration().smallestScreenWidthDp >= 720;
    }

    public boolean onIsHidingHeaders() { return getIntent().getBooleanExtra(EXTRA_NO_HEADERS, false); }

    public Header onGetInitialHeader() {
        for (int i = 0; i < mHeaders.size(); i++) {
            Header h = mHeaders.get(i);
            if (h.fragment != null) return h;
        }
        throw new IllegalStateException("Must have at least one header with a fragment");
    }

    public Header onGetNewHeader() { return null; }

    public void onBuildHeaders(List<Header> target) {}

    public void invalidateHeaders() {
        if (!mHandler.hasMessages(MSG_BUILD_HEADERS)) mHandler.sendEmptyMessage(MSG_BUILD_HEADERS);
    }

    private static final int[] HEADER_ATTRS = {
        android.R.attr.id, android.R.attr.title, android.R.attr.summary, android.R.attr.breadCrumbTitle,
        android.R.attr.breadCrumbShortTitle, android.R.attr.icon, android.R.attr.fragment,
    };

    public void loadHeadersFromResource(int resid, List<Header> target) {
        XmlResourceParser parser = null;
        try {
            parser = getResources().getXml(resid);
            AttributeSet attrs = Xml.asAttributeSet(parser);
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT && type != XmlPullParser.START_TAG) {
                // skip to the root
            }
            String nodeName = parser.getName();
            if (!"preference-headers".equals(nodeName)) {
                throw new RuntimeException("XML document must start with <preference-headers> tag; found"
                        + nodeName + " at " + parser.getPositionDescription());
            }
            Bundle curBundle = null;
            final int outerDepth = parser.getDepth();
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) continue;
                nodeName = parser.getName();
                if (!"header".equals(nodeName)) {
                    skipCurrentTag(parser);
                    continue;
                }
                Header header = new Header();
                TypedArray sa = obtainStyledAttributes(attrs, HEADER_ATTRS);
                header.id = sa.getResourceId(0, (int) HEADER_ID_UNDEFINED);
                header.titleRes = textRes(sa, 1);
                if (header.titleRes == 0) header.title = sa.getText(1);
                header.summaryRes = textRes(sa, 2);
                if (header.summaryRes == 0) header.summary = sa.getText(2);
                header.breadCrumbTitleRes = textRes(sa, 3);
                if (header.breadCrumbTitleRes == 0) header.breadCrumbTitle = sa.getText(3);
                header.breadCrumbShortTitleRes = textRes(sa, 4);
                if (header.breadCrumbShortTitleRes == 0) header.breadCrumbShortTitle = sa.getText(4);
                header.iconRes = sa.getResourceId(5, 0);
                header.fragment = sa.getString(6);
                sa.recycle();
                if (curBundle == null) curBundle = new Bundle();
                final int innerDepth = parser.getDepth();
                while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                        && (type != XmlPullParser.END_TAG || parser.getDepth() > innerDepth)) {
                    if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) continue;
                    String innerNodeName = parser.getName();
                    if ("extra".equals(innerNodeName)) {
                        getResources().parseBundleExtra("extra", attrs, curBundle);
                        skipCurrentTag(parser);
                    } else if ("intent".equals(innerNodeName)) {
                        header.intent = Intent.parseIntent(getResources(), parser, attrs);
                    } else {
                        skipCurrentTag(parser);
                    }
                }
                if (curBundle.size() > 0) {
                    header.fragmentArguments = curBundle;
                    curBundle = null;
                }
                target.add(header);
            }
        } catch (XmlPullParserException e) {
            throw new RuntimeException("Error parsing headers", e);
        } catch (IOException e) {
            throw new RuntimeException("Error parsing headers", e);
        } finally {
            if (parser != null) parser.close();
        }
    }

    private static int textRes(TypedArray sa, int index) {
        TypedValue tv = sa.peekValue(index);
        if (tv != null && tv.type == TypedValue.TYPE_STRING) return tv.resourceId;
        return 0;
    }

    private static void skipCurrentTag(XmlPullParser parser) throws XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            // nothing
        }
    }

    /** As AOSP: apps targeting KitKat and later must list the fragments they allow. */
    protected boolean isValidFragment(String fragmentName) {
        if (getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.KITKAT) {
            throw new RuntimeException("Subclasses of PreferenceActivity must override isValidFragment(String)"
                    + " to verify that the Fragment class is valid! " + getClass().getName()
                    + " has not checked if fragment " + fragmentName + " is valid.");
        }
        return true;
    }

    public void setListFooter(View view) {
        mListFooter.removeAllViews();
        mListFooter.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        mListFooter.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mPreferenceManager != null) mPreferenceManager.dispatchActivityStop();
    }

    @Override
    protected void onDestroy() {
        mHandler.removeMessages(MSG_BIND_PREFERENCES);
        mHandler.removeMessages(MSG_BUILD_HEADERS);
        super.onDestroy();
        if (mPreferenceManager != null) mPreferenceManager.dispatchActivityDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mHeaders.size() > 0) {
            outState.putParcelableArrayList(HEADERS_TAG, mHeaders);
            if (mCurHeader != null) {
                int index = mHeaders.indexOf(mCurHeader);
                if (index >= 0) outState.putInt(CUR_HEADER_TAG, index);
            }
        }
        if (mPreferenceManager != null) {
            final PreferenceScreen preferenceScreen = getPreferenceScreen();
            if (preferenceScreen != null) {
                Bundle container = new Bundle();
                preferenceScreen.saveHierarchyState(container);
                outState.putBundle(PREFERENCES_TAG, container);
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        if (mPreferenceManager != null) {
            Bundle container = state.getBundle(PREFERENCES_TAG);
            if (container != null) {
                final PreferenceScreen preferenceScreen = getPreferenceScreen();
                if (preferenceScreen != null) {
                    preferenceScreen.restoreHierarchyState(container);
                    mSavedInstanceState = state;
                    return;
                }
            }
        }
        super.onRestoreInstanceState(state);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mPreferenceManager != null) mPreferenceManager.dispatchActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        if (mPreferenceManager != null) postBindPreferences();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (!isResumed()) return;
        super.onListItemClick(l, v, position, id);
        if (mAdapter instanceof HeaderAdapter) {
            Object item = mAdapter.getItem(position);
            if (item instanceof Header) onHeaderClick((Header) item, position);
        }
    }

    public void onHeaderClick(Header header, int position) {
        if (header.fragment != null) {
            switchToHeader(header);
        } else if (header.intent != null) {
            startActivity(header.intent);
        }
    }

    public Intent onBuildStartFragmentIntent(String fragmentName, Bundle args, int titleRes, int shortTitleRes) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(this, getClass());
        intent.putExtra(EXTRA_SHOW_FRAGMENT, fragmentName);
        intent.putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, args);
        intent.putExtra(EXTRA_SHOW_FRAGMENT_TITLE, titleRes);
        intent.putExtra(EXTRA_SHOW_FRAGMENT_SHORT_TITLE, shortTitleRes);
        intent.putExtra(EXTRA_NO_HEADERS, true);
        return intent;
    }

    public void startWithFragment(String fragmentName, Bundle args, Fragment resultTo, int resultRequestCode) {
        startWithFragment(fragmentName, args, resultTo, resultRequestCode, 0, 0);
    }

    public void startWithFragment(String fragmentName, Bundle args, Fragment resultTo, int resultRequestCode,
            int titleRes, int shortTitleRes) {
        Intent intent = onBuildStartFragmentIntent(fragmentName, args, titleRes, shortTitleRes);
        if (resultTo == null) startActivity(intent);
        else resultTo.startActivityForResult(intent, resultRequestCode);
    }

    public void showBreadCrumbs(CharSequence title, CharSequence shortTitle) {
        if (title != null) setTitle(title);
    }

    public void setParentTitle(CharSequence title, CharSequence shortTitle, View.OnClickListener listener) {}

    private void switchToHeaderInner(String fragmentName, Bundle args) {
        getFragmentManager().popBackStack(BACK_STACK_PREFS, android.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        if (!isValidFragment(fragmentName)) {
            throw new IllegalArgumentException("Invalid fragment for this activity: " + fragmentName);
        }
        Fragment f = Fragment.instantiate(this, fragmentName, args);
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.setTransition(FragmentTransaction.TRANSIT_NONE);
        transaction.replace(PREFS_FRAME_ID, f);
        transaction.commitAllowingStateLoss();
    }

    public void switchToHeader(String fragmentName, Bundle args) {
        Header selectedHeader = null;
        for (int i = 0; i < mHeaders.size(); i++) {
            if (fragmentName.equals(mHeaders.get(i).fragment)) {
                selectedHeader = mHeaders.get(i);
                break;
            }
        }
        if (selectedHeader != null) {
            switchToHeader(selectedHeader);
        } else {
            startWithFragment(fragmentName, args, null, 0);
        }
    }

    public void switchToHeader(Header header) {
        if (mCurHeader == header) {
            getFragmentManager().popBackStack(BACK_STACK_PREFS,
                    android.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
            return;
        }
        if (header.fragment == null) throw new IllegalStateException("can't switch to header that has no fragment");
        mCurHeader = header;
        if (mSinglePane) {
            startWithFragment(header.fragment, header.fragmentArguments, null, 0, header.breadCrumbTitleRes,
                    header.breadCrumbShortTitleRes);
        } else {
            showFragmentPane();
            switchToHeaderInner(header.fragment, header.fragmentArguments);
        }
    }

    public void startPreferenceFragment(Fragment fragment, boolean push) {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(PREFS_FRAME_ID, fragment);
        if (push) {
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            transaction.addToBackStack(BACK_STACK_PREFS);
        } else {
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        }
        showFragmentPane();
        transaction.commitAllowingStateLoss();
    }

    public void startPreferencePanel(String fragmentClass, Bundle args, int titleRes, CharSequence titleText,
            Fragment resultTo, int resultRequestCode) {
        Fragment f = Fragment.instantiate(this, fragmentClass, args);
        if (resultTo != null) f.setTargetFragment(resultTo, resultRequestCode);
        if (mSinglePane) {
            startWithFragment(fragmentClass, args, resultTo, resultRequestCode, titleRes, 0);
            return;
        }
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(PREFS_FRAME_ID, f);
        if (titleRes != 0) transaction.setBreadCrumbTitle(titleRes);
        else if (titleText != null) transaction.setBreadCrumbTitle(titleText);
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        transaction.addToBackStack(BACK_STACK_PREFS);
        transaction.commitAllowingStateLoss();
    }

    public void finishPreferencePanel(Fragment caller, int resultCode, Intent resultData) {
        setResult(resultCode, resultData);
        finish();
    }

    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
        startPreferencePanel(pref.getFragment(), pref.getExtras(), pref.getTitleRes(), pref.getTitle(), null, 0);
        return true;
    }

    private void postBindPreferences() {
        if (mHandler.hasMessages(MSG_BIND_PREFERENCES)) return;
        mHandler.obtainMessage(MSG_BIND_PREFERENCES).sendToTarget();
    }

    private void bindPreferences() {
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen != null) {
            preferenceScreen.bind(getListView());
            if (mSavedInstanceState != null) {
                super.onRestoreInstanceState(mSavedInstanceState);
                mSavedInstanceState = null;
            }
        }
    }

    @Deprecated
    public PreferenceManager getPreferenceManager() { return mPreferenceManager; }

    private void requirePreferenceManager() {
        if (mPreferenceManager == null) {
            if (mAdapter == null) throw new RuntimeException("This should be called after super.onCreate.");
            throw new RuntimeException("Modern two-pane PreferenceActivity requires use of a PreferenceFragment");
        }
    }

    @Deprecated
    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
        requirePreferenceManager();
        if (mPreferenceManager.setPreferences(preferenceScreen) && preferenceScreen != null) {
            postBindPreferences();
            CharSequence title = getPreferenceScreen().getTitle();
            if (title != null) setTitle(title);
        }
    }

    @Deprecated
    public PreferenceScreen getPreferenceScreen() {
        return mPreferenceManager != null ? mPreferenceManager.getPreferenceScreen() : null;
    }

    private void ensurePreferenceManager() {
        if (mPreferenceManager == null && mAdapter == null) {
            mPreferenceManager = new PreferenceManager(this, FIRST_REQUEST_CODE);
            mPreferenceManager.setOnPreferenceTreeClickListener(this);
        }
    }

    @Deprecated
    public void addPreferencesFromIntent(Intent intent) {
        ensurePreferenceManager();
        requirePreferenceManager();
        setPreferenceScreen(mPreferenceManager.inflateFromIntent(intent, getPreferenceScreen()));
    }

    @Deprecated
    public void addPreferencesFromResource(int preferencesResId) {
        ensurePreferenceManager();
        requirePreferenceManager();
        setPreferenceScreen(mPreferenceManager.inflateFromResource(this, preferencesResId, getPreferenceScreen()));
    }

    @Deprecated
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) { return false; }

    @Deprecated
    public Preference findPreference(CharSequence key) {
        if (mPreferenceManager == null) return null;
        return mPreferenceManager.findPreference(key);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (mPreferenceManager != null) mPreferenceManager.dispatchNewIntent(intent);
    }
}
