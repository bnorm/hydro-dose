package dev.bnorm.hydro.dto

import dev.bnorm.hydro.Pump
import kotlinx.serialization.Serializable

@Serializable
data class PumpDto(
    val id: Int,
    val state: String,
)

fun Pump.toDto(): PumpDto {
    return PumpDto(id, state.toString())
}
