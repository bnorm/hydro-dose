package dev.bnorm.hydro.client.elevated.model.devices

import dev.bnorm.hydro.client.elevated.model.sensors.Sensor
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val id: DeviceId,
    val name: String,
    val sensors: List<Sensor>,
    val lastActionTime: Instant? = null,
)
