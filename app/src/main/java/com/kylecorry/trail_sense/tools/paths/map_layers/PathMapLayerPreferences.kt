package com.kylecorry.trail_sense.tools.paths.map_layers

import android.content.Context
import com.kylecorry.andromeda.preferences.StringEnumPreference
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.map_layers.preferences.definition.ListMapLayerPreference
import com.kylecorry.trail_sense.shared.map_layers.preferences.definition.MapLayerPreferenceConfig
import com.kylecorry.trail_sense.shared.map_layers.preferences.definition.MapLayerViewPreference
import com.kylecorry.trail_sense.shared.map_layers.preferences.repo.BaseMapLayerPreferences
import com.kylecorry.trail_sense.tools.paths.ui.PathBackgroundColor

class PathMapLayerPreferences(
    context: Context,
    mapId: String
) : BaseMapLayerPreferences(context, mapId, "path", R.string.paths) {
    private var _backgroundColor by StringEnumPreference(
        cache,
        "pref_${mapId}_${layerId}_layer_background_color",
        PathBackgroundColor.entries.associateBy { it.id.toString() },
        PathBackgroundColor.None
    )

    val backgroundColor = MapLayerPreferenceConfig(
        get = { _backgroundColor },
        set = { _backgroundColor = it },
        preference = ListMapLayerPreference(
            context.getString(R.string.background_color),
            "${layerId}_layer_background_color",
            listOf(
                context.getString(R.string.none) to PathBackgroundColor.None.id.toString(),
                context.getString(R.string.color_black) to PathBackgroundColor.Black.id.toString(),
                context.getString(R.string.color_white) to PathBackgroundColor.White.id.toString()
            ),
            dependency = enabledPreferenceId,
            defaultValue = PathBackgroundColor.None.id.toString()
        )
    )

    override fun getAllPreferences(): List<MapLayerViewPreference> {
        return listOf(
            isEnabled.preference,
            opacity.preference,
            backgroundColor.preference
        )
    }
}
