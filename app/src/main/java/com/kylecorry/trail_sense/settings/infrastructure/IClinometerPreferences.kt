package com.kylecorry.trail_sense.settings.infrastructure

import com.kylecorry.sol.units.Distance

interface IClinometerPreferences {
    var lockWithVolumeButtons: Boolean
    var baselineDistance: Distance?
    var measureHeightInstructionsSent: Boolean
    var measureDistanceInstructionsSent: Boolean
    val useAugmentedReality: Boolean
    val useHaptics: Boolean
}