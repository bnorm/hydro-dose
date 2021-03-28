package dev.bnorm.hydro

import com.pi4j.context.Context

class PumpService(
    val all: List<Pump>
) {
    private val map: Map<Int, Pump> = all.associateBy { it.id }

    operator fun get(id: Int): Pump? = map[id]
}

fun Context.PumpService(): PumpService {
    // Hardcode known pumps
    return PumpService(
        all = listOf(
            Pump(1, address = 17, rate = 1.2540),
            Pump(2, address = 18, rate = 1.2540),
            Pump(3, address = 27, rate = 1.1989),
            Pump(4, address = 22, rate = 1.1989),
        )
    )
}
