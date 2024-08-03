package com.kylecorry.trail_sense.settings.infrastructure

import android.content.Context
import com.kylecorry.andromeda.preferences.BooleanPreference
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.debugging.isDebug

class TidePreferences(context: Context) : PreferenceRepo(context), ITidePreferences {

    override val showNearestTide by BooleanPreference(
        cache,
        context.getString(R.string.pref_show_nearest_tide),
        false
    )

    override var lastTide: Long?
        get() = cache.getLong(context.getString(R.string.last_tide_id))
        set(value) {
            if (value != null) {
                cache.putLong(context.getString(R.string.last_tide_id), value)
            } else {
                cache.remove(context.getString(R.string.last_tide_id))
            }
        }

    // TODO: Add an experimental setting for this
    override val useLunitidalInterval: Boolean
        get() = isDebug()

}