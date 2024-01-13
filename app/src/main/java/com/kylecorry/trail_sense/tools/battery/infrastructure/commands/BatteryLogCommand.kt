package com.kylecorry.trail_sense.tools.battery.infrastructure.commands

import android.content.Context
import com.kylecorry.andromeda.battery.Battery
import com.kylecorry.andromeda.battery.BatteryChargingStatus
import com.kylecorry.trail_sense.shared.commands.CoroutineCommand
import com.kylecorry.trail_sense.tools.battery.domain.BatteryReading
import com.kylecorry.trail_sense.tools.battery.infrastructure.persistence.BatteryRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant

class BatteryLogCommand(private val context: Context) : CoroutineCommand {
    override suspend fun execute() {
        val battery = Battery(context)
        val batteryRepo = BatteryRepo.getInstance(context)
        withContext(Dispatchers.IO) {
            try {
                withTimeoutOrNull(Duration.ofSeconds(10).toMillis()) {
                    battery.read()
                }
            } finally {
                battery.stop(null)
            }
        }
        val pct = battery.percent
        val charging = battery.chargingStatus == BatteryChargingStatus.Charging
        val time = Instant.now()
        val capacity = battery.capacity
        val reading = BatteryReading(time, pct, capacity, charging)
        if (battery.hasValidReading) {
            batteryRepo.add(reading)
        }
        batteryRepo.deleteBefore(Instant.now().minus(Duration.ofDays(1)))
    }
}