package com.kylecorry.trail_sense.settings.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.HarmonizedColors
import com.kylecorry.andromeda.core.system.Intents
import com.kylecorry.andromeda.core.system.Package
import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.andromeda.fragments.AndromedaPreferenceFragment
import com.kylecorry.andromeda.pickers.Pickers
import com.kylecorry.andromeda.sense.Sensors
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.backup.BackupCommand
import com.kylecorry.trail_sense.backup.RestoreCommand
import com.kylecorry.trail_sense.main.Navigation
import com.kylecorry.trail_sense.shared.QuickActionUtils
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.io.ActivityUriPicker
import com.kylecorry.trail_sense.shared.preferences.PreferencesSubsystem
import com.kylecorry.trail_sense.shared.requireMainActivity
import com.kylecorry.trail_sense.tools.flashlight.infrastructure.FlashlightSubsystem
import kotlinx.coroutines.launch

class SettingsFragment : AndromedaPreferenceFragment() {

    private val navigationMap = mapOf(
        R.string.pref_unit_settings to Navigation.UNIT_SETTINGS,
        R.string.pref_privacy_settings to Navigation.PRIVACY_SETTINGS,
        R.string.pref_power_settings to Navigation.POWER_SETTINGS,
        R.string.pref_experimental_settings to Navigation.EXPERIMENTAL_SETTINGS,
        R.string.pref_error_settings to Navigation.ERROR_SETTINGS,
        R.string.pref_sensor_settings to Navigation.SENSOR_SETTINGS,

        // Tools
        R.string.pref_navigation_header_key to Navigation.NAVIGATION_SETTINGS,
        R.string.pref_weather_category to Navigation.WEATHER_SETTINGS,
        R.string.pref_astronomy_category to Navigation.ASTRONOMY_SETTINGS,
        R.string.pref_flashlight_settings to Navigation.FLASHLIGHT_SETTINGS,
        R.string.pref_maps_header_key to Navigation.MAP_SETTINGS,
        R.string.pref_tide_settings to Navigation.TIDE_SETTINGS,
        R.string.pref_clinometer_settings to Navigation.CLINOMETER_SETTINGS,
        R.string.pref_odometer_calibration to Navigation.CALIBRATE_PEDOMETER,

        // About
        R.string.pref_open_source_licenses to Navigation.LICENSES,
        R.string.pref_diagnostics to Navigation.DIAGNOSTICS,
    )

    private val uriPicker by lazy { ActivityUriPicker(requireMainActivity()) }
    private val backupCommand by lazy { BackupCommand(requireContext(), uriPicker) }
    private val restoreCommand by lazy { RestoreCommand(requireContext(), uriPicker) }
    private val prefs by lazy { UserPreferences(requireContext()) }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        for (nav in navigationMap) {
            navigateOnClick(preference(nav.key), nav.value)
        }

        preference(R.string.pref_weather_category)?.isVisible =
            Sensors.hasBarometer(requireContext())

        preference(R.string.pref_flashlight_settings)?.isVisible =
            FlashlightSubsystem.getInstance(requireContext()).isAvailable()

        reloadThemeOnChange(list(R.string.pref_theme))

        onClick(preference(R.string.pref_github)) {
            val i = Intents.url(it.summary.toString())
            startActivity(i)
        }

        onClick(preference(R.string.pref_privacy_policy)) {
            val i = Intents.url(it.summary.toString())
            startActivity(i)
        }

        onClick(preference(R.string.pref_email)) {
            val intent = Intents.email(it.summary.toString(), getString(R.string.app_name))
            startActivity(Intent.createChooser(intent, it.title.toString()))
        }

        val dynamicColorsSwitch = switch(R.string.pref_use_dynamic_colors)
        val dynamicCompassColorsSwitch = switch(R.string.pref_use_dynamic_colors_on_compass)
        dynamicColorsSwitch?.isVisible = DynamicColors.isDynamicColorAvailable()
        dynamicCompassColorsSwitch?.isVisible = DynamicColors.isDynamicColorAvailable()
        dynamicCompassColorsSwitch?.isEnabled = prefs.useDynamicColors
        dynamicColorsSwitch?.setOnPreferenceChangeListener { _, _ ->
            requireMainActivity().reloadTheme()
            dynamicCompassColorsSwitch?.isEnabled = prefs.useDynamicColors
            true
        }

        val version = Package.getVersionName(requireContext())
        preference(R.string.pref_app_version)?.summary = version
        setIconColor(preferenceScreen, Resources.androidTextColorSecondary(requireContext()))

        onClick(findPreference("backup_restore")) {
            Pickers.item(
                requireContext(),
                getString(R.string.backup_restore),
                listOf(
                    getString(R.string.backup),
                    getString(R.string.restore)
                )
            ) {
                when (it) {
                    0 -> backup()
                    1 -> restore()
                }
            }
        }


        onClick(findPreference(getString(R.string.pref_tool_quick_action_header_key))){
            val potentialActions = QuickActionUtils.tools(requireContext())

            val selected = prefs.toolQuickActions

            val selectedIndices = potentialActions.mapIndexedNotNull { index, quickActionType ->
                if (selected.contains(quickActionType)) index else null
            }

            Pickers.items(
                requireContext(),
                getString(R.string.tool_quick_actions),
                potentialActions.map { QuickActionUtils.getName(requireContext(), it) },
                selectedIndices
            ) {
                if (it != null) {
                    prefs.toolQuickActions = it.map { potentialActions[it] }
                }
            }
        }
    }

    private fun backup() {
        lifecycleScope.launch {
            backupCommand.execute()
        }
    }

    private fun restore() {
        lifecycleScope.launch {
            restoreCommand.execute()
        }
    }

    private fun reloadThemeOnChange(pref: Preference?) {
        pref?.setOnPreferenceChangeListener { _, _ ->
            requireMainActivity().reloadTheme()
            true
        }
    }

}