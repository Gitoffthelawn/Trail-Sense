package com.kylecorry.trail_sense.tools.photo_maps.domain

import com.kylecorry.trail_sense.shared.grouping.Groupable

interface IMap: Groupable {
    val name: String
}