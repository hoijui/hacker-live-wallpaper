package com.gulshansingh.hackerlivewallpaper

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.Preference
import android.preference.PreferenceActivity
import android.preference.PreferenceManager
import android.view.Menu
import android.view.MenuItem

import net.margaritov.preference.colorpicker.ColorPickerPreference

import java.util.Arrays

class SettingsActivity : PreferenceActivity() {

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.prefs)

        val pm = preferenceManager
        val characterSet = pm.sharedPreferences.getString("character_set_name", "Binary")
        pm.findPreference(KEY_CHARACTER_SET_PREFS)!!.summary = "Character set is " + characterSet!!

        pm.findPreference("set_as_wallpaper")!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val i = Intent()
            try {
                if (Build.VERSION.SDK_INT > 15) {
                    i.action = WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER

                    val p = HackerWallpaperService::class.java.`package`.name
                    val c = HackerWallpaperService::class.java.canonicalName
                    i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(p, c))
                } else {
                    i.action = WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER
                }
            } catch (e: ActivityNotFoundException) {
                // Fallback to the old method, some devices greater than SDK 15 are crashing
                i.action = WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER
            }

            startActivity(i)
            true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_reset_to_defaults) {
            resetToDefaults()
            refreshPreferences()
        } else {
            return super.onOptionsItemSelected(item)
        }
        return true
    }

    /** Sets the preferences to their default values without updating the GUI  */
    private fun resetToDefaults() {
        val preferences = PreferenceManager
                .getDefaultSharedPreferences(this)
        val editor = preferences.edit()
        editor.clear()
        editor.commit()
        PreferenceManager.setDefaultValues(this, R.xml.prefs, true)
    }

    /** Initializes the GUI to match the preferences  */
    private fun refreshPreferences() {
        for (key in mRefreshKeys) {
            (preferenceScreen.findPreference(key) as Refreshable)
                    .refresh(this)
        }

        // We set the color to the color in the preferences to refresh the color preview
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val bitPref = findPreference(KEY_BIT_COLOR) as ColorPickerPreference
        var defaultColor = resources.getColor(R.color.default_bit_color)
        var color = sp.getInt(KEY_BIT_COLOR, defaultColor)
        bitPref.onColorChanged(color)

        val backgroundPref = findPreference(KEY_BACKGROUND_COLOR) as ColorPickerPreference
        defaultColor = resources
                .getColor(R.color.default_background_color)
        color = sp.getInt(KEY_BACKGROUND_COLOR, defaultColor)
        backgroundPref.onColorChanged(color)

        val depthEnabledPref = findPreference(KEY_ENABLE_DEPTH) as CheckBoxPreference
        depthEnabledPref.isChecked = sp.getBoolean(KEY_ENABLE_DEPTH, true)
    }

    public override fun onStart() {
        super.onStart()
        refreshPreferences()
    }

    public override fun onStop() {
        super.onStop()
        BitSequence.configure(this)
        HackerWallpaperService.reset()
    }

    companion object {
        const val KEY_BACKGROUND_COLOR = "background_color"
        const val KEY_ENABLE_DEPTH = "enable_depth"
        const val KEY_TEXT_SIZE = "text_size"
        const val KEY_CHANGE_BIT_SPEED = "change_bit_speed"
        const val KEY_FALLING_SPEED = "falling_speed"
        const val KEY_NUM_BITS = "num_bits"
        const val KEY_BIT_COLOR = "bit_color"
        const val KEY_CHARACTER_SET_PREFS = "character_set_prefs"

        /** Keys for preferences that should be refreshed  */
        private val mRefreshKeys = Arrays.asList(
                KEY_NUM_BITS, KEY_FALLING_SPEED, KEY_CHANGE_BIT_SPEED,
                KEY_TEXT_SIZE, KEY_CHARACTER_SET_PREFS)
    }
}
