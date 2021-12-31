package dev.bnorm.hydro

import com.pi4j.context.Context

class SensorService(
    val all: List<Sensor>
) {
    private val map: Map<Int, Sensor> = all.associateBy { it.id }

    operator fun get(id: Int): Sensor? = map[id]
    operator fun get(type: SensorType): Sensor = map.getValue(type.id)
}

enum class SensorType(
    val id: Int,
    val bus: Int = 1,
    val device: Int,
) {
    Ph(1, bus = 1, device = 0x63),
    Ec(2, bus = 1, device = 0x64),
}

fun Context.SensorService(): SensorService {
    // Hardcode known sensors
    return SensorService(
        all = SensorType.values().map { Sensor(it.id, it.bus, it.device) }
    )
}
