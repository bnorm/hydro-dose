package dev.bnorm.hydro

import dev.bnorm.hydro.db.SensorReading
import dev.bnorm.hydro.db.SensorReadingQueries
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class SensorReadingService(
    private val sensorService: SensorService,
    private val sensorReadingQueries: SensorReadingQueries,
) {
    suspend fun record() {
        coroutineScope {
            val timestamp = Clock.System.now()
            SensorType.values()
                .map { async { it to sensorService[it].read() } }
                .forEach {
                    val (type, reading) = it.await()
                    sensorReadingQueries.insert(SensorReading(type.id, reading, timestamp))
                }
        }
    }

    fun findAll(sensorId: Int, after: Instant, before: Instant = Clock.System.now()): List<SensorReading> {
        return sensorReadingQueries.selectAll(sensorId, after, before).executeAsList()
    }
}
