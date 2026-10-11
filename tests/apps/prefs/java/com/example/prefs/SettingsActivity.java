package com.example.prefs;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.ListView;

/**
 * android.preference from XML: defaults written by setDefaultValues, a checkbox that a switch depends
 * on, a list preference whose summary follows its value, and a preference that carries an intent.
 */
public class SettingsActivity extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "PREFS";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        PreferenceManager.setDefaultValues(this, R.xml.settings, false);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        Log.i(TAG, "defaults sound=" + sp.getBoolean("sound", false) + " music=" + sp.getBoolean("music", true)
                + " difficulty=" + sp.getString("difficulty", "?") + " name=" + sp.getString("name", "?"));
        Log.i(TAG, "default name " + PreferenceManager.getDefaultSharedPreferencesName(this));
        addPreferencesFromResource(R.xml.settings);
        PreferenceScreen screen = getPreferenceScreen();
        Log.i(TAG, "screen count=" + screen.getPreferenceCount() + " sound in "
                + screen.findPreference("sound").getParent().getKey());
        ListPreference difficulty = (ListPreference) findPreference("difficulty");
        Log.i(TAG, "difficulty entry=" + difficulty.getEntry() + " summary=" + difficulty.getSummary());
        Preference about = findPreference("about");
        Intent intent = about.getIntent();
        Log.i(TAG, "about intent " + intent.getAction() + " " + intent.getData() + " from="
                + intent.getStringExtra("from"));
        CheckBoxPreference sound = (CheckBoxPreference) findPreference("sound");
        Log.i(TAG, "sound checked=" + sound.isChecked() + " music enabled=" + findPreference("music").isEnabled());
        final ListView list = getListView();
        // PreferenceActivity binds the list in a posted message, as on Android.
        Log.i(TAG, "adapter before bind " + (list.getAdapter() == null));
        list.post(new Runnable() {
            public void run() {
                Log.i(TAG, "list adapter count=" + list.getAdapter().getCount());
            }
        });
        sp.registerOnSharedPreferenceChangeListener(this);
        about.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference p) {
                Log.i(TAG, "clicked " + p.getKey());
                return true;
            }
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, final String key) {
        Log.i(TAG, "changed " + key + "=" + sp.getAll().get(key));
        // Dependents and summaries update after the value is persisted, as on Android.
        getListView().post(new Runnable() {
            public void run() {
                Log.i(TAG, "after " + key + " music enabled=" + findPreference("music").isEnabled()
                        + " difficulty summary=" + findPreference("difficulty").getSummary()
                        + " name=" + ((EditTextPreference) findPreference("name")).getText());
            }
        });
    }
}
