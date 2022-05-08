package dev.bnorm.hydro.client.elevated.model.devices

import dev.bnorm.hydro.client.elevated.model.auth.Password
import kotlinx.serialization.Serializable

@Serializable
class DevicePrototype(
    val name: String,
    val key: Password,
)
