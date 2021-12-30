@file:OptIn(KtorExperimentalLocationsAPI::class)

import com.pi4j.Pi4J
import com.pi4j.common.Descriptor
import dev.bnorm.hydro.PumpService
import dev.bnorm.hydro.SensorService
import dev.bnorm.hydro.dto.toDto
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.locations.*
import io.ktor.locations.put
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream

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

@Suppress("unused") // application.conf
fun Application.app() {
    val pi4j = Pi4J.newAutoContext()
    environment.monitor.subscribe(ApplicationStopping) { pi4j.shutdown() }

    val pumpService = pi4j.PumpService()
    val sensorService = pi4j.SensorService()

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
                    pump.dispense(amount)
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
                put<Read> { (id) ->
                    val sensor = sensorService[id] ?: throw NotFoundException()
                    val measurement = sensor.read()
                    call.respond(measurement)
                }
            }
        }
    }
}

fun Descriptor.print(): String {
    val stream = ByteArrayOutputStream()
    print(PrintStream(stream))
    return stream.toString(Charsets.UTF_8)
}
