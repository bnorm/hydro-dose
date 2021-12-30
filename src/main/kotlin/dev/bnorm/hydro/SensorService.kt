package dev.bnorm.hydro

import com.pi4j.context.Context

class SensorService(
    val all: List<Sensor>
) {
    private val map: Map<Int, Sensor> = all.associateBy { it.id }

    operator fun get(id: Int): Sensor? = map[id]
}

fun Context.SensorService(): SensorService {
    // Hardcode known sensors
    return SensorService(
        all = listOf(
            Sensor(1, bus = 1, device = 0x63), // pH
            Sensor(2, bus = 1, device = 0x64), // EC
        )
    )
}
