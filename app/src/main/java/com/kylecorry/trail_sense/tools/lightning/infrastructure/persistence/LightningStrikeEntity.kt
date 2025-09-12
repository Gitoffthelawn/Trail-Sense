package com.kylecorry.trail_sense.tools.lightning.infrastructure.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kylecorry.sol.units.Distance
import com.kylecorry.sol.units.Reading
import com.kylecorry.trail_sense.shared.data.Identifiable
import com.kylecorry.trail_sense.tools.lightning.domain.LightningStrike
import java.time.Instant

@Entity(tableName = "lightning")
data class LightningStrikeEntity(
    @ColumnInfo(name = "time") val time: Instant,
    @ColumnInfo(name = "distance") val distance: Float
) : Identifiable {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    override var id: Long = 0

    fun toReading(): Reading<LightningStrike> {
        return Reading(LightningStrike(id, Distance.meters(distance)), time)
    }

    companion object {
        fun from(reading: Reading<LightningStrike>): LightningStrikeEntity {
            return LightningStrikeEntity(
                reading.time,
                reading.value.distance.meters().value
            ).also {
                it.id = reading.value.id
            }
        }
    }

}