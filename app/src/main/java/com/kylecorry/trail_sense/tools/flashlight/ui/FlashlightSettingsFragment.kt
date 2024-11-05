package com.kylecorry.trail_sense.tools.flashlight.ui

import android.os.Bundle
import com.kylecorry.andromeda.fragments.AndromedaPreferenceFragment
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.CustomUiUtils
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.tools.flashlight.infrastructure.FlashlightSubsystem

class FlashlightSettingsFragment : AndromedaPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.flashlight_preferences, rootKey)

        val formatter = FormatService.getInstance(requireContext())
        val prefs = UserPreferences(requireContext()).flashlight

        val hasFlashlight = FlashlightSubsystem.getInstance(requireContext()).isAvailable()
        if (!hasFlashlight){
            preference(R.string.pref_flashlight_toggle_with_volume)?.isVisible = false
            preference(R.string.pref_flashlight_should_timeout)?.isVisible = false
            preference(R.string.pref_flashlight_timeout)?.isVisible = false

        }

        val timeout = preference(R.string.pref_flashlight_timeout)
        timeout?.summary =
            formatter.formatDuration(prefs.timeout, short = false, includeSeconds = true)

        timeout?.setOnPreferenceClickListener { pref ->
            val title = pref.title.toString()
            CustomUiUtils.pickDuration(
                requireContext(),
                prefs.timeout,
                title
            ) {
                if (it != null && !it.isZero) {
                    prefs.timeout = it
                    timeout.summary =
                        formatter.formatDuration(it, short = false, includeSeconds = true)
                }
            }
            true
        }

    }

}