package com.kylecorry.trail_sense.settings.ui

import android.os.Bundle
import com.kylecorry.andromeda.core.system.Screen
import com.kylecorry.andromeda.fragments.AndromedaPreferenceFragment
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.main.Navigation
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.sensors.SensorService

class PrivacySettingsFragment : AndromedaPreferenceFragment() {

    private val prefs by lazy { UserPreferences(requireContext()) }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.privacy_preferences, rootKey)

        onClick(preference(R.string.pref_privacy_screenshot_protection)) {
            Screen.setAllowScreenshots(
                requireActivity().window,
                !prefs.privacy.isScreenshotProtectionOn
            )
        }
        navigateOnClick(
            preference(R.string.pref_gps_calibration),
            Navigation.CALIBRATE_GPS
        )
    }

    override fun onResume() {
        super.onResume()
        preference(R.string.pref_gps_calibration)?.summary = if (isLocationMocked()) {
            getString(R.string.location_mocked)
        } else {
            getString(R.string.location_not_mocked)
        }
    }

    private fun isLocationMocked(): Boolean {
        return !prefs.useAutoLocation || !SensorService(requireContext()).hasLocationPermission()
    }

}