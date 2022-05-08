package dev.bnorm.hydro.client.elevated.model.auth

import dev.bnorm.hydro.client.elevated.model.devices.Device
import kotlinx.serialization.Serializable

@Serializable
class AuthenticatedDevice(
    val token: AuthorizationToken,
    val device: Device,
)
