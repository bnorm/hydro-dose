package dev.bnorm.hydro.client.elevated.model.devices

import dev.bnorm.hydro.client.elevated.model.auth.Password
import kotlinx.serialization.Serializable

@Serializable
class DeviceLoginRequest(
    val id: DeviceId,
    val key: Password,
)
