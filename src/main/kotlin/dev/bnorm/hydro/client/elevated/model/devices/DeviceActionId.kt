package dev.bnorm.hydro.client.elevated.model.devices

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class DeviceActionId(
    val value: String,
)
