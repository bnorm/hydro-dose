package dev.bnorm.hydro.client.elevated.model.sensors

import dev.bnorm.hydro.client.elevated.model.devices.DeviceId
import kotlinx.serialization.Serializable

@Serializable
class SensorPrototype(
    val name: String,
    val deviceId: DeviceId,
)
