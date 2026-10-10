package com.example.prefs;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

/** A legacy PreferenceActivity: defaults from XML, then the hierarchy listed; changes are logged for the check. */
public class SettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "PREFS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager.setDefaultValues(this, R.xml.prefs, false);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        Log.i(TAG, "defaults sound=" + sp.getBoolean("sound", false) + " music=" + sp.getBoolean("music", true)
                + " level=" + sp.getString("level", "?") + " name=" + sp.getString("name", "?") + " has="
                + getSharedPreferences(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, MODE_PRIVATE)
                        .getBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, false));
        addPreferencesFromResource(R.xml.prefs);
        Preference volume = findPreference("volume");
        Log.i(TAG, "volume enabled=" + volume.isEnabled() + " level summary=" + findPreference("level").getSummary());
        sp.registerOnSharedPreferenceChangeListener(this);
    }

    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        Object value = sp.getAll().get(key);
        Log.i(TAG, "changed " + key + "=" + value + " volume enabled=" + findPreference("volume").isEnabled()
                + " level summary=" + findPreference("level").getSummary());
    }
}
