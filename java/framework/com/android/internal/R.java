package com.android.internal;

import com.android.internal.util.InternalRes;

/**
 * framework-internal. The subset of AOSP's com.android.internal.R that ported framework code uses.
 * Ids are looked up by name in framework-res when the class loads (0 when absent); styleables are
 * attribute arrays with their index constants. Regenerate when porting more AOSP code (the script
 * lists the files that use it; see docs/ARCHITECTURE.md).
 */
public final class R {
    private R() {}

    public static final class attr {
        public static final int checkBoxPreferenceStyle = InternalRes.attr("checkBoxPreferenceStyle");
        public static final int dialogPreferenceStyle = InternalRes.attr("dialogPreferenceStyle");
        public static final int editTextPreferenceStyle = InternalRes.attr("editTextPreferenceStyle");
        public static final int fragmentBreadCrumbsStyle = InternalRes.attr("fragmentBreadCrumbsStyle");
        public static final int preferenceActivityStyle = InternalRes.attr("preferenceActivityStyle");
        public static final int preferenceCategoryStyle = InternalRes.attr("preferenceCategoryStyle");
        public static final int preferenceFragmentStyle = InternalRes.attr("preferenceFragmentStyle");
        public static final int preferenceFrameLayoutStyle = InternalRes.attr("preferenceFrameLayoutStyle");
        public static final int preferenceScreenStyle = InternalRes.attr("preferenceScreenStyle");
        public static final int preferenceStyle = InternalRes.attr("preferenceStyle");
        public static final int ringtonePreferenceStyle = InternalRes.attr("ringtonePreferenceStyle");
        public static final int seekBarDialogPreferenceStyle = InternalRes.attr("seekBarDialogPreferenceStyle");
        public static final int seekBarPreferenceStyle = InternalRes.attr("seekBarPreferenceStyle");
        public static final int switchPreferenceStyle = InternalRes.attr("switchPreferenceStyle");
    }

    public static final class bool {
        public static final int preferences_prefer_dual_pane = InternalRes.id("bool", "preferences_prefer_dual_pane");
    }

    public static final class id {
        public static final int back_button = InternalRes.id("id", "back_button");
        public static final int breadcrumb_section = InternalRes.id("id", "breadcrumb_section");
        public static final int button_bar = InternalRes.id("id", "button_bar");
        public static final int checkbox = InternalRes.id("id", "checkbox");
        public static final int edit = InternalRes.id("id", "edit");
        public static final int edittext_container = InternalRes.id("id", "edittext_container");
        public static final int empty = InternalRes.id("id", "empty");
        public static final int headers = InternalRes.id("id", "headers");
        public static final int icon = InternalRes.id("id", "icon");
        public static final int icon_frame = InternalRes.id("id", "icon_frame");
        public static final int left_icon = InternalRes.id("id", "left_icon");
        public static final int list = InternalRes.id("id", "list");
        public static final int list_footer = InternalRes.id("id", "list_footer");
        public static final int message = InternalRes.id("id", "message");
        public static final int next_button = InternalRes.id("id", "next_button");
        public static final int prefs = InternalRes.id("id", "prefs");
        public static final int prefs_container = InternalRes.id("id", "prefs_container");
        public static final int prefs_frame = InternalRes.id("id", "prefs_frame");
        public static final int seekbar = InternalRes.id("id", "seekbar");
        public static final int skip_button = InternalRes.id("id", "skip_button");
        public static final int summary = InternalRes.id("id", "summary");
        public static final int switch_widget = InternalRes.id("id", "switch_widget");
        public static final int title = InternalRes.id("id", "title");
        public static final int widget_frame = InternalRes.id("id", "widget_frame");
    }

    public static final class layout {
        public static final int fragment_bread_crumb_item = InternalRes.id("layout", "fragment_bread_crumb_item");
        public static final int fragment_bread_crumbs = InternalRes.id("layout", "fragment_bread_crumbs");
        public static final int list_content_simple = InternalRes.id("layout", "list_content_simple");
        public static final int preference = InternalRes.id("layout", "preference");
        public static final int preference_header_item = InternalRes.id("layout", "preference_header_item");
        public static final int preference_list_content = InternalRes.id("layout", "preference_list_content");
        public static final int preference_list_content_single = InternalRes.id("layout", "preference_list_content_single");
        public static final int preference_list_fragment = InternalRes.id("layout", "preference_list_fragment");
        public static final int preference_widget_seekbar = InternalRes.id("layout", "preference_widget_seekbar");
    }

    public static final class string {
        public static final int cancel = InternalRes.id("string", "cancel");
        public static final int ok = InternalRes.id("string", "ok");
    }

    public static final class styleable {
        public static final int[] CheckBoxPreference = InternalRes.attrs("summaryOn", "summaryOff", "disableDependentsState");
        public static final int CheckBoxPreference_summaryOn = 0;
        public static final int CheckBoxPreference_summaryOff = 1;
        public static final int CheckBoxPreference_disableDependentsState = 2;
        public static final int[] DialogPreference = InternalRes.attrs("dialogTitle", "dialogMessage", "dialogIcon", "positiveButtonText", "negativeButtonText", "dialogLayout");
        public static final int DialogPreference_dialogTitle = 0;
        public static final int DialogPreference_dialogMessage = 1;
        public static final int DialogPreference_dialogIcon = 2;
        public static final int DialogPreference_positiveButtonText = 3;
        public static final int DialogPreference_negativeButtonText = 4;
        public static final int DialogPreference_dialogLayout = 5;
        public static final int[] FragmentBreadCrumbs = InternalRes.attrs("gravity", "itemLayout", "itemColor");
        public static final int FragmentBreadCrumbs_gravity = 0;
        public static final int FragmentBreadCrumbs_itemLayout = 1;
        public static final int FragmentBreadCrumbs_itemColor = 2;
        public static final int[] ListPreference = InternalRes.attrs("entries", "entryValues");
        public static final int ListPreference_entries = 0;
        public static final int ListPreference_entryValues = 1;
        public static final int[] MultiSelectListPreference = InternalRes.attrs("entries", "entryValues");
        public static final int MultiSelectListPreference_entries = 0;
        public static final int MultiSelectListPreference_entryValues = 1;
        public static final int[] Preference = InternalRes.attrs("icon", "persistent", "enabled", "layout", "title", "selectable", "key", "summary", "order", "widgetLayout", "dependency", "defaultValue", "shouldDisableView", "fragment", "singleLineTitle", "iconSpaceReserved", "recycleEnabled");
        public static final int Preference_icon = 0;
        public static final int Preference_persistent = 1;
        public static final int Preference_enabled = 2;
        public static final int Preference_layout = 3;
        public static final int Preference_title = 4;
        public static final int Preference_selectable = 5;
        public static final int Preference_key = 6;
        public static final int Preference_summary = 7;
        public static final int Preference_order = 8;
        public static final int Preference_widgetLayout = 9;
        public static final int Preference_dependency = 10;
        public static final int Preference_defaultValue = 11;
        public static final int Preference_shouldDisableView = 12;
        public static final int Preference_fragment = 13;
        public static final int Preference_singleLineTitle = 14;
        public static final int Preference_iconSpaceReserved = 15;
        public static final int Preference_recycleEnabled = 16;
        public static final int[] PreferenceActivity = InternalRes.attrs("layout", "headerLayout", "headerRemoveIconIfEmpty");
        public static final int PreferenceActivity_layout = 0;
        public static final int PreferenceActivity_headerLayout = 1;
        public static final int PreferenceActivity_headerRemoveIconIfEmpty = 2;
        public static final int[] PreferenceFragment = InternalRes.attrs("layout", "divider");
        public static final int PreferenceFragment_layout = 0;
        public static final int PreferenceFragment_divider = 1;
        public static final int[] PreferenceFrameLayout = InternalRes.attrs("borderTop", "borderBottom", "borderLeft", "borderRight");
        public static final int PreferenceFrameLayout_borderTop = 0;
        public static final int PreferenceFrameLayout_borderBottom = 1;
        public static final int PreferenceFrameLayout_borderLeft = 2;
        public static final int PreferenceFrameLayout_borderRight = 3;
        public static final int[] PreferenceFrameLayout_Layout = InternalRes.attrs("layout_removeBorders");
        public static final int PreferenceFrameLayout_Layout_layout_removeBorders = 0;
        public static final int[] PreferenceGroup = InternalRes.attrs("orderingFromXml");
        public static final int PreferenceGroup_orderingFromXml = 0;
        public static final int[] PreferenceHeader = InternalRes.attrs("id", "title", "summary", "breadCrumbTitle", "breadCrumbShortTitle", "icon", "fragment");
        public static final int PreferenceHeader_id = 0;
        public static final int PreferenceHeader_title = 1;
        public static final int PreferenceHeader_summary = 2;
        public static final int PreferenceHeader_breadCrumbTitle = 3;
        public static final int PreferenceHeader_breadCrumbShortTitle = 4;
        public static final int PreferenceHeader_icon = 5;
        public static final int PreferenceHeader_fragment = 6;
        public static final int[] PreferenceScreen = InternalRes.attrs("screenLayout", "divider");
        public static final int PreferenceScreen_screenLayout = 0;
        public static final int PreferenceScreen_divider = 1;
        public static final int[] ProgressBar = InternalRes.attrs("max");
        public static final int ProgressBar_max = 0;
        public static final int[] RingtonePreference = InternalRes.attrs("ringtoneType", "showDefault", "showSilent");
        public static final int RingtonePreference_ringtoneType = 0;
        public static final int RingtonePreference_showDefault = 1;
        public static final int RingtonePreference_showSilent = 2;
        public static final int[] SeekBarPreference = InternalRes.attrs("layout");
        public static final int SeekBarPreference_layout = 0;
        public static final int[] SwitchPreference = InternalRes.attrs("summaryOn", "summaryOff", "switchTextOn", "switchTextOff", "disableDependentsState");
        public static final int SwitchPreference_summaryOn = 0;
        public static final int SwitchPreference_summaryOff = 1;
        public static final int SwitchPreference_switchTextOn = 2;
        public static final int SwitchPreference_switchTextOff = 3;
        public static final int SwitchPreference_disableDependentsState = 4;
    }
}
