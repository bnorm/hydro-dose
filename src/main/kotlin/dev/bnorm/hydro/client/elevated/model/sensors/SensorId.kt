package dev.bnorm.hydro.client.elevated.model.sensors

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class SensorId(
    val value: String,
)
