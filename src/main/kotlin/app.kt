@file:OptIn(KtorExperimentalLocationsAPI::class, ExperimentalTime::class)

import com.pi4j.Pi4J
import com.pi4j.common.Descriptor
import dev.bnorm.hydro.ChartService
import dev.bnorm.hydro.PumpService
import dev.bnorm.hydro.SensorReadingService
import dev.bnorm.hydro.SensorService
import dev.bnorm.hydro.db.createDatabase
import dev.bnorm.hydro.dto.toDto
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.locations.put
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.until
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

@Location("/{id}")
data class ById(
    val id: Int,
)

@Location("/{id}/dispense")
data class Dispense(
    val id: Int,
    val amount: Int, // milliliters
)

@Location("/{id}/read")
data class Read(
    val id: Int,
)

@Location("/{id}/readings")
data class QueryReadings(
    val id: Int,
    val before: Instant = Clock.System.now(),
    val after: Instant = before - 2.hours,
)

@Location("/{name}/weeks/{week}/dose")
data class DoseChart(
    val name: String,
    val week: Long,
)

@Suppress("unused") // application.conf
fun Application.app() {
    val scope = CoroutineScope(context = Dispatchers.Default)
    environment.monitor.subscribe(ApplicationStopping) { scope.cancel() }

    val pi4j = Pi4J.newAutoContext()
    environment.monitor.subscribe(ApplicationStopping) { pi4j.shutdown() }

    val database = createDatabase("/app/data/app.sqlite")
    val pumpService = pi4j.PumpService()
    val sensorService = pi4j.SensorService()
    val chartService = ChartService(database.chartQueries, sensorService, pumpService)
    val sensorReadingService = SensorReadingService(sensorService, database.sensorReadingQueries)

    scope.schedule(frequency = 1.minutes) { sensorReadingService.record() }

    install(ContentNegotiation) {
        json()
    }

    install(Locations)

    routing {
        route("/api/v1/") {
            route("pi") {
                get("context") {
                    call.respondText(pi4j.describe().print())
                }
            }
            route("pumps") {
                get {
                    val pumps = pumpService.all
                    call.respond(pumps.map { it.toDto() })
                }
                get<ById> { (id) ->
                    val pump = pumpService[id] ?: throw NotFoundException()
                    call.respond(pump.toDto())
                }
                put<Dispense> { (id, amount) ->
                    val pump = pumpService[id] ?: throw NotFoundException()
                    pump.dispense(amount.toDouble())
                    call.respond(pump.toDto())
                }
            }
            route("sensors") {
                get {
                    val sensors = sensorService.all
                    call.respond(sensors.map { it.toDto() })
                }
                get<ById> { (id) ->
                    val sensor = sensorService[id] ?: throw NotFoundException()
                    call.respond(sensor.toDto())
                }
                get<Read> { (id) ->
                    val sensor = sensorService[id] ?: throw NotFoundException()
                    val measurement = sensor.read()
                    call.respond(measurement)
                }
                get<QueryReadings> { (id, before, after) ->
                    val sensor = sensorService[id] ?: throw NotFoundException()
                    val readings = sensorReadingService.findAll(sensor.id, after, before)
                    call.respond(readings.map { it.toDto() })
                }
            }
            route("chart") {
                put<DoseChart> { (name, week) ->
                    chartService.dose(name, week)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

fun CoroutineScope.schedule(
    frequency: Duration,
    action: suspend () -> Unit,
) {
    launch {
        var next = Clock.System.now()
        while (true) {
            action()

            while (next < Clock.System.now()) {
                next += frequency
            }
            delay(Clock.System.now().until(next, DateTimeUnit.MILLISECOND))
        }
    }
}

fun Descriptor.print(): String {
    val stream = ByteArrayOutputStream()
    print(PrintStream(stream))
    return stream.toString(Charsets.UTF_8)
}
