package com.kylecorry.trail_sense.navigation.paths.infrastructure.commands

import android.content.Context
import com.kylecorry.sol.units.Distance
import com.kylecorry.trail_sense.navigation.paths.domain.PathPoint
import com.kylecorry.trail_sense.navigation.paths.infrastructure.alerts.BacktrackAlerter
import com.kylecorry.trail_sense.navigation.paths.infrastructure.persistence.PathService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.alerts.IValueAlerter
import com.kylecorry.trail_sense.shared.commands.CoroutineCommand
import com.kylecorry.trail_sense.shared.extensions.onDefault
import com.kylecorry.trail_sense.shared.extensions.onIO
import com.kylecorry.trail_sense.shared.networkQuality
import com.kylecorry.trail_sense.shared.sensors.NullCellSignalSensor
import com.kylecorry.trail_sense.shared.sensors.SensorService
import com.kylecorry.andromeda.sense.readAll
import java.time.Duration
import java.time.Instant

class BacktrackCommand(
    private val context: Context,
    private val pathId: Long = 0,
    private val alerter: IValueAlerter<Distance> = BacktrackAlerter(context)
) : CoroutineCommand {

    private val prefs = UserPreferences(context)

    private val sensorService = SensorService(context)
    private val gps = sensorService.getGPS()
    private val altimeter = sensorService.getAltimeter(gps = gps)
    private val cellSignalSensor =
        if (prefs.backtrackSaveCellHistory && pathId == 0L) sensorService.getCellSignal() else NullCellSignalSensor()

    private val pathService = PathService.getInstance(context)

    override suspend fun execute() = onDefault {
        updateSensors()
        val point = recordWaypoint()
        CreateLastSignalBeaconCommand(context).execute(point)
        showNotification()
    }

    private suspend fun showNotification() {
        if (pathId != 0L) {
            return
        }
        val backtrackId = pathService.getBacktrackPathId() ?: return
        val backtrackPath = pathService.getPath(backtrackId) ?: return

        val distance = backtrackPath.metadata.distance
        alerter.alert(distance)
    }

    private suspend fun updateSensors() {
        readAll(
            listOf(gps, altimeter, cellSignalSensor),
            timeout = Duration.ofSeconds(10),
            forceStopOnCompletion = true
        )
    }


    private suspend fun recordWaypoint(): PathPoint {
        return onIO {
            val waypoint = PathPoint(
                0,
                pathId,
                gps.location,
                altimeter.altitude,
                Instant.now(),
                cellSignalSensor.networkQuality()
            )

            if (pathId == 0L) {
                pathService.addBacktrackPoint(waypoint)
            } else {
                pathService.addWaypoint(waypoint)
            }
            waypoint
        }
    }

}