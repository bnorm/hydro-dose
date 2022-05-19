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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.until
import org.apache.logging.log4j.LogManager
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.time.Duration
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
    val scope = CoroutineScope(context = SupervisorJob() + Dispatchers.Default)
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
            elevatedClient.authenticate()

            elevatedClient.getActionQueue().collect {
                when (it.args) {
                    is PumpDispenseArguments -> {
                        pumpService[it.args.pump]?.dispense(it.args.amount)
                        elevatedClient.completeDeviceAction(it.id)
                    }
                }
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

fun CoroutineScope.schedule(
    name: String,
    frequency: Duration,
    immediate: Boolean = false,
    action: suspend () -> Unit,
) {
    val log = LogManager.getLogger("dev.bnorm.hydro.scheduler")

    suspend fun perform(timestamp: Instant) {
        try {
            log.debug("Performing scheduled action {}", name)
            action()
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            log.warn("Unable to perform scheduled action {} at {}", name, timestamp, t)
        }
    }

    launch(start = if (immediate) CoroutineStart.UNDISPATCHED else CoroutineStart.DEFAULT) {
        val start = Clock.System.now()
        val truncated = (start.toEpochMilliseconds() / frequency.inWholeMilliseconds) * frequency.inWholeMilliseconds
        var next = Instant.fromEpochMilliseconds(truncated)

        if (immediate) {
            perform(start)
        }

        while (isActive) {
            val now = Clock.System.now()
            while (next < now) next += frequency
            log.debug("Waiting until {} to perform scheduled action {}", next, name)
            delay(now.until(next, DateTimeUnit.MILLISECOND))
            perform(next)
        }
    }
}

fun Descriptor.print(): String {
    val stream = ByteArrayOutputStream()
    print(PrintStream(stream))
    return stream.toString(Charsets.UTF_8)
}
