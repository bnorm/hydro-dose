package dev.bnorm.hydro.client.elevated.model.devices

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class DeviceActionArguments

@Serializable
@SerialName("PUMP_DISPENSE")
class PumpDispenseArguments(
    val pump: Int,
    val amount: Int, // milliliters
) : DeviceActionArguments()