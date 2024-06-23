package org.akanework.gramophone.ui.fragments.settings

import android.os.Bundle
import androidx.preference.Preference
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.fragments.BasePreferenceFragment
import org.akanework.gramophone.ui.fragments.BaseSettingFragment

class MainSettingsFragment : BaseSettingFragment(R.string.home_menu_settings,
    { MainSettingsTopFragment() })

class MainSettingsTopFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_top, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "appearance" -> {
                val supportFragmentManager = requireActivity().supportFragmentManager
                supportFragmentManager
                    .beginTransaction()
                    .addToBackStack(System.currentTimeMillis().toString())
                    .hide(supportFragmentManager.fragments.let { it[it.size - 1] })
                    .add(R.id.container, AppearanceSettingsFragment())
                    .commit()
            }

            "behavior" -> {
                val supportFragmentManager = requireActivity().supportFragmentManager
                supportFragmentManager
                    .beginTransaction()
                    .addToBackStack(System.currentTimeMillis().toString())
                    .hide(supportFragmentManager.fragments.let { it[it.size - 1] })
                    .add(R.id.container, BehaviorSettingsFragment())
                    .commit()
            }

            "player" -> {
                val supportFragmentManager = requireActivity().supportFragmentManager
                supportFragmentManager
                    .beginTransaction()
                    .addToBackStack(System.currentTimeMillis().toString())
                    .hide(supportFragmentManager.fragments.let { it[it.size - 1] })
                    .add(R.id.container, PlayerSettingsFragment())
                    .commit()
            }



        }
        return super.onPreferenceTreeClick(preference)
    }

}
