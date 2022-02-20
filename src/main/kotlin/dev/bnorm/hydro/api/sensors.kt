@file:OptIn(KtorExperimentalLocationsAPI::class)

package dev.bnorm.hydro.api

import dev.bnorm.hydro.SensorReadingService
import dev.bnorm.hydro.SensorService
import dev.bnorm.hydro.dto.toDto
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.hours

@Location("/sensors")
object Sensors

@Location("/sensors/{id}")
data class Sensor(
    val id: Int,
) {
    data class Read(
        val sensor: Sensor,
    )

    data class Readings(
        val sensor: Sensor,
        val before: Instant = Clock.System.now(),
        val after: Instant = before - 2.hours,
    )
}

fun Route.sensorsApi(sensorService: SensorService, sensorReadingService: SensorReadingService) {
    get<Sensors> {
        val sensors = sensorService.all
        call.respond(sensors.map { it.toDto() })
    }

    get<Sensor> { (id) ->
        val sensor = sensorService[id] ?: throw NotFoundException()
        call.respond(sensor.toDto())
    }

    get<Sensor.Read> { (sensor) ->
        val sensor = sensorService[sensor.id] ?: throw NotFoundException()
        val measurement = sensor.read()
        call.respond(measurement)
    }

    get<Sensor.Readings> { (sensor, before, after) ->
        val sensor = sensorService[sensor.id] ?: throw NotFoundException()
        val readings = sensorReadingService.findAll(sensor.id, after, before)
        call.respond(readings.map { it.toDto() })
    }
}