package com.kylecorry.trail_sense.tools.pedometer.domain

import com.kylecorry.trail_sense.settings.infrastructure.IPedometerPreferences
import com.kylecorry.trail_sense.shared.alerts.IAlerter
import com.kylecorry.trail_sense.shared.commands.Command
import com.kylecorry.trail_sense.tools.pedometer.infrastructure.IStepCounter

class DistanceAlertCommand(
    private val prefs: IPedometerPreferences,
    private val counter: IStepCounter,
    private val paceCalculator: IPaceCalculator,
    private val alerter: IAlerter
) : Command {
    override fun execute() {
        val alertDistance = prefs.alertDistance ?: return
        val distance = paceCalculator.distance(counter.steps)
        if (distance.meters().value >= alertDistance.meters().value) {
            alerter.alert()
            prefs.alertDistance = null
        }
    }
}