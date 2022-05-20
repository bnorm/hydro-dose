@file:OptIn(KtorExperimentalLocationsAPI::class, ExperimentalTime::class)

import com.pi4j.Pi4J
import com.pi4j.common.Descriptor
import com.pi4j.context.Context
import dev.bnorm.hydro.ChartService
import dev.bnorm.hydro.FakePumpService
import dev.bnorm.hydro.FakeSensorService
import dev.bnorm.hydro.PumpService
import dev.bnorm.hydro.SensorReadingService
import dev.bnorm.hydro.SensorService
import dev.bnorm.hydro.api.chartsApi
import dev.bnorm.hydro.api.pumpsApi
import dev.bnorm.hydro.api.sensorsApi
import dev.bnorm.hydro.client.elevated.ElevatedClient
import dev.bnorm.hydro.client.elevated.model.devices.PumpDispenseArguments
import dev.bnorm.hydro.db.Database
import dev.bnorm.hydro.db.createDatabase
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.locations.*
import io.ktor.server.locations.put
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.dataconversion.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

@Location("/{name}/weeks/{week}/dose")
data class DoseChart(
    val name: String,
    val week: Long,
)

@Suppress("unused") // application.conf
fun Application.app() {
    val loggingExceptionHandler = CoroutineExceptionHandler { _, t ->
        log.warn("Unhandled exception in worker scope", t)
    }
    val scope = CoroutineScope(context = SupervisorJob() + Dispatchers.Default + loggingExceptionHandler)
    environment.monitor.subscribe(ApplicationStopping) { scope.cancel() }

    val elevatedClient: ElevatedClient?

    val pi4j: Context?
    val pumpService: PumpService
    val sensorService: SensorService
    val database: Database

    if (System.getProperty("os.name") == "Mac OS X") {
        elevatedClient = null

        pi4j = null

        pumpService = FakePumpService()
        sensorService = FakeSensorService()

        Files.createDirectories(Paths.get("build/runtime"))
        database = createDatabase("build/runtime/app.sqlite")
    } else {
        elevatedClient = ElevatedClient()
        scope.schedule(name = "Device Authentication", frequency = 1.hours, immediate = true) {
            // Immediately login and refresh authentication every hour
            elevatedClient.authenticate()
        }

        pi4j = Pi4J.newAutoContext()
        environment.monitor.subscribe(ApplicationStopping) { pi4j.shutdown() }

        pumpService = pi4j.PumpService()
        sensorService = pi4j.SensorService()

        database = createDatabase("/app/data/app.sqlite")
    }

    val chartService = ChartService(database.chartQueries, sensorService, pumpService)
    val sensorReadingService = SensorReadingService(sensorService, database.sensorReadingQueries, elevatedClient)

    scope.schedule(name = "Record Sensors", frequency = 1.minutes) {
        sensorReadingService.record()
    }

    scope.schedule(name = "Dose Active Chart", frequency = 4.hours) {
        // Starts at hour 0 UTC - 6 PM CST
        // 6 PM .. 10 PM .. 2 AM .. 6 AM .. 10 AM .. 2 PM ..
        chartService.doseActive()
    }

    if (elevatedClient != null) {
        scope.launch {
            try {
                elevatedClient.authenticate()

                elevatedClient.getActionQueue().collect {
                    when (it.args) {
                        is PumpDispenseArguments -> {
                            pumpService[it.args.pump]?.dispense(it.args.amount)
                            elevatedClient.completeDeviceAction(it.id)
                        }
                    }
                }
            } catch (t: Throwable) {
                log.warn("Unhandled exception in websocket connection", t)
                throw t
            }
        }
    }

    install(ContentNegotiation) {
        json()
    }

    install(DataConversion) {
        convert<Instant> {
            decode { Instant.parse(it.single()) }
            encode { listOf(it.toString()) }
        }
    }

    install(Locations)

    routing {
        route("/api/v1/") {
            chartsApi(chartService)
            sensorsApi(sensorService, sensorReadingService)
            pumpsApi(pumpService)

            if (pi4j != null) {
                route("pi") {
                    get("context") {
                        call.respondText(pi4j.describe().print())
                    }
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

fun Descriptor.print(): String {
    val stream = ByteArrayOutputStream()
    print(PrintStream(stream))
    return stream.toString(Charsets.UTF_8)
}
