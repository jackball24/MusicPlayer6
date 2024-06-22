package org.akanework.gramophone.ui.fragments.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.fragments.BasePreferenceFragment
import org.akanework.gramophone.ui.fragments.BaseSettingFragment

class AppearanceSettingsFragment : BaseSettingFragment(R.string.settings_category_appearance,
    { AppearanceSettingsTopFragment() })

class AppearanceSettingsTopFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_appearance, rootKey)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "theme_mode" -> {
                when (sharedPreferences?.getString("theme_mode", "0")) {
                    "0" -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    }

                    "1" -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    }

                    "2" -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    }
                }
            }
        }
    }

}
