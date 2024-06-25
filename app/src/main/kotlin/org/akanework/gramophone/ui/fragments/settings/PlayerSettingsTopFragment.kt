package org.akanework.gramophone.ui.fragments.settings

import android.os.Bundle
import androidx.preference.Preference
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.fragments.BasePreferenceFragment
import org.akanework.gramophone.ui.fragments.BaseSettingFragment

class PlayerSettingsFragment : BaseSettingFragment(R.string.settings_player_ui,
    { PlayerSettingsTopFragment() })

class PlayerSettingsTopFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_player, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "lyrics") {
            val supportFragmentManager = requireActivity().supportFragmentManager
            supportFragmentManager
                .beginTransaction()
                .addToBackStack(System.currentTimeMillis().toString())
                .hide(supportFragmentManager.fragments.let { it[it.size - 1] })
                .add(R.id.container, LyricSettingsFragment())
                .commit()
        }
        return super.onPreferenceTreeClick(preference)
    }

}
