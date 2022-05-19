package dev.bnorm.hydro.client.elevated

import dev.bnorm.hydro.client.elevated.model.auth.AuthenticatedDevice
import dev.bnorm.hydro.client.elevated.model.auth.AuthorizationToken
import dev.bnorm.hydro.client.elevated.model.auth.JwtTokenUsage
import dev.bnorm.hydro.client.elevated.model.auth.Password
import dev.bnorm.hydro.client.elevated.model.devices.Device
import dev.bnorm.hydro.client.elevated.model.devices.DeviceAction
import dev.bnorm.hydro.client.elevated.model.devices.DeviceActionId
import dev.bnorm.hydro.client.elevated.model.devices.DeviceId
import dev.bnorm.hydro.client.elevated.model.devices.DeviceLoginRequest
import dev.bnorm.hydro.client.elevated.model.sensors.SensorReading
import dev.bnorm.hydro.client.elevated.model.sensors.SensorReadingPrototype
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.apache.logging.log4j.LogManager
import kotlin.time.Duration.Companion.seconds

class ElevatedClient {
    companion object {
        private val log = LogManager.getLogger(ElevatedClient::class.java)

        private const val API_HOSTNAME = "https://elevated.bnorm.dev"

        private val environment = System.getenv()

        private val DEVICE_ID by environment
        private val DEVICE_KEY by environment

        private val SENSOR_PH_ID by environment
        private val SENSOR_EC_ID by environment
    }

    private var authorization: String? = null

    private val json = DefaultJson

    private val client = HttpClient(OkHttp) {
        install(WebSockets)

        install(ContentNegotiation) {
            json(json)
        }

        install(DefaultRequest) {
            authorization?.let { headers[HttpHeaders.Authorization] = it }
        }

        install(HttpCallValidator) {
            handleResponseExceptionWithRequest { exception, response ->
                val clientException = exception as? ClientRequestException ?: return@handleResponseExceptionWithRequest
                if (clientException.response.status == HttpStatusCode.Unauthorized) {
                    authorization = null
                }
            }
        }
    }

    @OptIn(JwtTokenUsage::class)
    private fun authorize(token: AuthorizationToken) {
        authorization = "${token.type} ${token.value.value}"
    }

    suspend fun authenticate(): Device {
        val response = client.post("$API_HOSTNAME/api/v1/devices/login") {
            contentType(ContentType.Application.Json)
            setBody(
                DeviceLoginRequest(
                    id = DeviceId(DEVICE_ID),
                    key = Password(DEVICE_KEY)
                )
            )
        }
        val authenticatedDevice = response.body<AuthenticatedDevice>()
        authorize(authenticatedDevice.token)
        return authenticatedDevice.device
    }

    suspend fun getDevice(): Device {
        return client.get("$API_HOSTNAME/api/v1/devices/$DEVICE_ID").body()
    }

    suspend fun getDeviceActions(submittedAfter: Instant): List<DeviceAction> {
        return client.get("$API_HOSTNAME/api/v1/devices/$DEVICE_ID/actions?submittedAfter=$submittedAfter").body()
    }

    suspend fun completeDeviceAction(deviceActionId: DeviceActionId): DeviceAction {
        return client.put("$API_HOSTNAME/api/v1/devices/$DEVICE_ID/actions/${deviceActionId.value}/complete").body()
    }

    suspend fun getActionQueue(): Flow<DeviceAction> {
        return channelFlow {
            while (isActive) {
                try {
                    val device = getDevice()
                    log.info("Connecting to server for device={}", device)

                    val flow = this.channel
                    client.webSocket({
                        method = HttpMethod.Get
                        url.takeFrom("$API_HOSTNAME/api/v1/devices/$DEVICE_ID/connect")
                        url.protocol = URLProtocol.WSS
                    }) {
                        val authorization = authorization
                        if (authorization != null) {
                            outgoing.send(Frame.Text(authorization.substringAfter(' ')))
                        }

                        log.info("Getting actions for device={}", device)
                        val pending = getDeviceActions(device.lastActionTime ?: Instant.DISTANT_PAST)
                        val actionIds = pending.map { it.id }.toSet()

                        log.info("Existing actions={}", pending)
                        incoming.consumeAsFlow()
                            .filterIsInstance<Frame.Text>()
                            .map { it.readText() }
                            .map { json.decodeFromString(DeviceAction.serializer(), it) }
                            .filter { it.id !in actionIds }
                            .onStart { emitAll(pending.asFlow()) }
                            .filter { it.completed == null }
                            .onEach { log.info("Received : {}", it) }
                            .collect { flow.send(it) }
                    }

                    log.info("Disconnected from server")
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    log.warn("Error in WebSocket", t)
                }

                delay(5.seconds)
            }
        }
    }

    suspend fun recordPhReading(value: Double, timestamp: Instant = Clock.System.now()): SensorReading {
        val response = client.post("$API_HOSTNAME/api/v1/sensors/$SENSOR_PH_ID/readings/record") {
            contentType(ContentType.Application.Json)
            setBody(
                SensorReadingPrototype(
                    value = value,
                    timestamp = timestamp
                )
            )
        }
        return response.body()
    }

    suspend fun recordEcReading(value: Double, timestamp: Instant = Clock.System.now()): SensorReading {
        val response = client.post("$API_HOSTNAME/api/v1/sensors/$SENSOR_EC_ID/readings/record") {
            contentType(ContentType.Application.Json)
            setBody(
                SensorReadingPrototype(
                    value = value,
                    timestamp = timestamp
                )
            )
        }
        return response.body()
    }
}
