package com.kylecorry.trail_sense.navigation.paths.infrastructure

import android.content.Context
import com.kylecorry.andromeda.core.specifications.Specification
import com.kylecorry.trail_sense.shared.UserPreferences

class BacktrackIsEnabled : Specification<Context>() {
    override fun isSatisfiedBy(value: Context): Boolean {
        return UserPreferences(value).backtrackEnabled
    }
}