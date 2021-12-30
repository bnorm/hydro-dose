package dev.bnorm.hydro.dto

import dev.bnorm.hydro.Sensor
import kotlinx.serialization.Serializable

@Serializable
data class SensorDto(
    val id: Int,
)

fun Sensor.toDto(): SensorDto {
    return SensorDto(id)
}
