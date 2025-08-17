package com.kylecorry.trail_sense.tools.photo_maps.ui

import android.os.Bundle
import com.kylecorry.andromeda.core.cache.AppServiceRegistry
import com.kylecorry.andromeda.fragments.AndromedaPreferenceFragment
import com.kylecorry.andromeda.fragments.show
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.CustomUiUtils
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.map_layers.preferences.ui.MapLayersBottomSheet

class PhotoMapSettingsFragment : AndromedaPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.photo_map_preferences, rootKey)

        val reducePhotoResolutionPreference = switch(R.string.pref_low_resolution_maps)
        val reducePdfResolutionPreference = switch(R.string.pref_low_resolution_pdf_maps)

        onClick(reducePhotoResolutionPreference) {
            onReduceResolutionChange(reducePdfResolutionPreference?.isChecked == true)
        }

        onClick(reducePdfResolutionPreference) {
            onReduceResolutionChange(reducePhotoResolutionPreference?.isChecked == true)
        }

        // Layers
        onClick(preference(R.string.pref_map_layer_button)) {
            val prefs = AppServiceRegistry.get<UserPreferences>()
            MapLayersBottomSheet(prefs.photoMaps.layerManager).show(this)
        }
    }

    private fun onReduceResolutionChange(shouldReduce: Boolean) {
        if (shouldReduce) {
            return
        }

        CustomUiUtils.disclaimer(
            requireContext(),
            getString(R.string.reduce_map_resolution),
            getString(R.string.reduce_map_resolution_crop_disclaimer),
            getString(R.string.reduce_map_resolution_crop_disclaimer_shown),
            cancelText = null
        )
    }

}