package dev.bnorm.hydro.client.elevated.model.devices

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class DeviceId(
    val value: String,
)
