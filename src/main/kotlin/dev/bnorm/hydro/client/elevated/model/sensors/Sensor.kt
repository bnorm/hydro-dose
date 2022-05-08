package dev.bnorm.hydro.client.elevated.model.sensors

import kotlinx.serialization.Serializable

@Serializable
class Sensor(
    val id: SensorId,
    val name: String,
)
