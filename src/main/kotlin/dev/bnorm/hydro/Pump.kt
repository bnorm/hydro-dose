package dev.bnorm.hydro

import com.pi4j.context.Context
import com.pi4j.io.gpio.digital.DigitalOutput
import com.pi4j.io.gpio.digital.DigitalState
import kotlinx.coroutines.delay

interface Pump {
    val id: Int
    val state: State

    fun on()
    fun off()

    suspend fun dispense(milliliters: Int)

    enum class State {
        ON,
        OFF,
    }
}

fun Context.Pump(
    id: Int,
    address: Int,
    rate: Double // ml / second
): Pump {
    val name = "pump" + id.toString().padStart(2, '0')
    val config = DigitalOutput.newConfigBuilder(this)
        .address(address)
        .id(name)
        .name(name)
        .shutdown(DigitalState.HIGH)
        .initial(DigitalState.HIGH)
        .provider("pigpio-digital-output")
    val output = create(config)
    return Pi4jPump(output, name, rate, id, Pump.State.OFF)
}

private class Pi4jPump(
    private val output: DigitalOutput,
    private val name: String,
    private val rate: Double, // ml / second
    override val id: Int,
    override var state: Pump.State,
) : Pump {
    override fun on() {
        output.low()
        state = Pump.State.ON
    }

    override fun off() {
        output.high()
        state = Pump.State.OFF
    }

    override suspend fun dispense(milliliters: Int) {
        val milliseconds = milliliters / rate * 1000
        try {
            on()
            delay(milliseconds.toLong())
        } finally {
            off() // always attempt to turn off
        }
    }

    override fun toString(): String = name
}
