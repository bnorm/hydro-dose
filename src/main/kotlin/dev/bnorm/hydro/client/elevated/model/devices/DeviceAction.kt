package dev.bnorm.hydro.client.elevated.model.devices

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
class DeviceAction(
    val id: DeviceActionId,
    val deviceId: DeviceId,
    val submitted: Instant,
    val completed: Instant?,
    val args: DeviceActionArguments,
)
