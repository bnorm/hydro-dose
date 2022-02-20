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
import dev.bnorm.hydro.db.Database
import dev.bnorm.hydro.db.createDatabase
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.locations.put
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    val scope = CoroutineScope(context = Dispatchers.Default)
    environment.monitor.subscribe(ApplicationStopping) { scope.cancel() }

    val pi4j: Context?
    val pumpService: PumpService
    val sensorService: SensorService
    val database: Database

    if (System.getProperty("os.name") == "Mac OS X") {
        pi4j = null

        pumpService = FakePumpService()
        sensorService = FakeSensorService()

        Files.createDirectories(Paths.get("build/runtime"))
        database = createDatabase("build/runtime/app.sqlite")
    } else {
        pi4j = Pi4J.newAutoContext()
        environment.monitor.subscribe(ApplicationStopping) { pi4j.shutdown() }

        pumpService = pi4j.PumpService()
        sensorService = pi4j.SensorService()

        database = createDatabase("/app/data/app.sqlite")
    }

    val chartService = ChartService(database.chartQueries, sensorService, pumpService)
    val sensorReadingService = SensorReadingService(sensorService, database.sensorReadingQueries)

    scope.schedule(name = "Record Sensors", frequency = 1.minutes) {
        sensorReadingService.record()
    }

    scope.schedule(name = "Dose Active Chart", frequency = 4.hours) {
        // Starts at hour 0 UTC - 6 PM CST
        // 6 PM .. 10 PM .. 2 AM .. 6 AM .. 10 AM .. 2 PM ..
        chartService.doseActive()
    }

    install(ContentNegotiation) {
        json()
    }

    install(DataConversion) {
        convert<Instant> {
            decode { values, _ ->
                values.singleOrNull()?.let { Instant.parse(it) }
            }

            encode { value ->
                when (value) {
                    null -> listOf()
                    is Instant -> listOf(value.toString())
                    else -> throw DataConversionException("Cannot convert $value as Instant")
                }
            }
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
    action: suspend () -> Unit,
) {
    val log = LogManager.getLogger("dev.bnorm.hydro.scheduler")
    launch {
        val start = Clock.System.now()
        val truncated = (start.toEpochMilliseconds() / frequency.inWholeMilliseconds) * frequency.inWholeMilliseconds
        var next = Instant.fromEpochMilliseconds(truncated)

        while (isActive) {
            val now = Clock.System.now()
            while (next < now) next += frequency
            log.debug("Waiting until {} to perform scheduled action {}", next, name)
            delay(now.until(next, DateTimeUnit.MILLISECOND))

            try {
                log.debug("Performing scheduled action {}", name)
                action()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                log.warn("Unable to perform scheduled action {} at {}", name, next, t)
            }
        }
    }
}

fun Descriptor.print(): String {
    val stream = ByteArrayOutputStream()
    print(PrintStream(stream))
    return stream.toString(Charsets.UTF_8)
}
