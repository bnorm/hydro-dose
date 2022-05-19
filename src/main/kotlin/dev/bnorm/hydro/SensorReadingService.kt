package dev.bnorm.hydro

import dev.bnorm.hydro.client.elevated.ElevatedClient
import dev.bnorm.hydro.db.SensorReading
import dev.bnorm.hydro.db.SensorReadingQueries
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.apache.logging.log4j.LogManager

class SensorReadingService(
    private val sensorService: SensorService,
    private val sensorReadingQueries: SensorReadingQueries,
    private val elevatedClient: ElevatedClient?,
) {
    companion object {
        private val log = LogManager.getLogger(SensorReadingService::class.java)
    }

    suspend fun record() {
        coroutineScope {
            val timestamp = Clock.System.now()
            SensorType.values()
                .map { async { it to sensorService[it].read() } }
                .forEach {
                    val (type, reading) = it.await()
                    sensorReadingQueries.insert(SensorReading(type.id, reading, timestamp))
                    runCatching {
                        when (type) {
                            SensorType.Ph -> elevatedClient?.recordPhReading(reading, timestamp)
                            SensorType.Ec -> elevatedClient?.recordEcReading(reading, timestamp)
                        }
                    }.onFailure { error ->
                        log.warn("Unable to upload {} sensor reading to elevated.bnorm.dev", type, error)
                    }
                }
        }
    }

    fun findAll(sensorId: Int, after: Instant, before: Instant = Clock.System.now()): List<SensorReading> {
        return sensorReadingQueries.selectAll(sensorId, after, before).executeAsList()
    }
}
