package org.akanework.gramophone.ui.fragments.settings

import android.os.Bundle
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.fragments.BasePreferenceFragment
import org.akanework.gramophone.ui.fragments.BaseSettingFragment

class AudioSettingsFragment : BaseSettingFragment(R.string.settings_audio,
    { AudioSettingsTopFragment() })

class AudioSettingsTopFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_audio, rootKey)
    }
}
