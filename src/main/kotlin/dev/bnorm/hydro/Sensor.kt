package dev.bnorm.hydro

import com.pi4j.context.Context
import com.pi4j.io.i2c.I2C
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.logging.log4j.LogManager
import java.io.IOException

interface Sensor {
    val id: Int

    suspend fun read(): Double
}

fun Context.Sensor(
    id: Int,
    bus: Int = 1,
    device: Int,
): Sensor {
    val name = "sensor" + id.toString().padStart(2, '0')
    val config = I2C.newConfigBuilder(this)
        .id(name)
        .name(name)
        .bus(bus)
        .device(device)
        .provider("linuxfs-i2c")
    val i2c = create(config)
    return Pi4jSensor(i2c, name, id)
}

private val log = LogManager.getLogger(Sensor::class.java)

private class Pi4jSensor(
    private val i2c: I2C,
    private val name: String,
    override val id: Int,
) : Sensor {
    private var mutex = Mutex()
    private val buffer = ByteArray(31)

    override suspend fun read(): Double {
        mutex.withLock {
            i2c.write("r")
            delay(900)

            i2c.read(buffer)
            val size = buffer.indexOf(0)
            val copy = buffer.copyOf(size)
            log.debug("response for {}: size={} hex={}",
                id, size, copy.joinToString("") { it.toString(16).padStart(2, '0') })

            if (copy[0].toInt() != 1) {
                throw IOException("code=" + copy[0].toString(16))
            } else {
                return copy.decodeToString(startIndex = 1, endIndex = size).toDouble()
            }
        }
    }

    override fun toString(): String = name
}

class FakeSensor(
    override val id: Int,
    private val generator: () -> Double,
) : Sensor {
    override suspend fun read(): Double {
        val reading = generator()
        log.debug("response for {}: value={}", id, reading)
        return reading
    }
}
