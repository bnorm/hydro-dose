package dev.bnorm.hydro.dto

import dev.bnorm.hydro.db.SensorReading
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class SensorReadingDto(
    val sensorId: Int,
    val value: Double,
    val timestamp: Instant
)

fun SensorReading.toDto(): SensorReadingDto {
    return SensorReadingDto(sensor_id, value_, timestamp)
}
