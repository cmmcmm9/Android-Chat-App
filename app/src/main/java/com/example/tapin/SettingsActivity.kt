package com.example.tapin

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import viewmodel.TapInViewModel
import java.util.prefs.PreferenceChangeEvent
import java.util.prefs.PreferenceChangeListener

/**
 * Activity to show the user settings. The view is replaced by the
 * @see[com.example.tapin.SettingsFragment] fragment.
 *
 */
class SettingsActivity : AppCompatActivity() {

    /**
     * Commit the Settings Fragment to the front of the stack
     *
     * @param savedInstanceState
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }
    }

}