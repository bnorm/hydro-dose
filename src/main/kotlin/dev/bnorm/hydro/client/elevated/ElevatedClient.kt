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
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.apache.logging.log4j.LogManager
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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

    private val json = Json(DefaultJson) {
        ignoreUnknownKeys = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(Duration.ofSeconds(30))
        .build()

    private val client = HttpClient(OkHttp.create {
        preconfigured = okHttpClient
    }) {
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
                    val request = Request.Builder()
                        .url("https://elevated.bnorm.dev/api/v1/devices/$DEVICE_ID/connect")
                        .build()

                    val incoming = Channel<String>(capacity = Channel.UNLIMITED)
                    val webSocket = okHttpClient.newWebSocket(request, incoming)

                    val authorization = authorization
                    if (authorization != null) {
                        webSocket.send(authorization.substringAfter(' '))
                    }

                    log.info("Getting actions for device={}", device)
                    val pending = getDeviceActions(device.lastActionTime ?: Instant.DISTANT_PAST)
                    val actionIds = pending.map { it.id }.toSet()

                    log.info("Existing actions={}", pending)
                    val actions = incoming.consumeAsFlow()
                        .onEach { log.info("Received : frame.text={}", it) }
                        .map { json.decodeFromString(DeviceAction.serializer(), it) }
                        .filter { it.id !in actionIds }
                        .onStart { emitAll(pending.asFlow()) }
                        .filter { it.completed == null }
                        .produceIn(this)

                    log.info("Processing messages")
                    while (!actions.isClosedForReceive) {
                        select<Unit> {
                            actions.onReceive {
                                log.info("Received : action={}", it)
                                flow.send(it)
                            }
                            onTimeout(60.seconds) {
                                log.info("Still connected to server...")
                            }
                        }
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

    private suspend fun OkHttpClient.newWebSocket(request: Request, incoming: SendChannel<String>): WebSocket {
        return suspendCancellableCoroutine {
            val webSocket = newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        if (it.isActive) it.resume(webSocket)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        incoming.close(t)
                        if (it.isActive) it.resumeWithException(t)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        incoming.close()
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        incoming.close()
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        incoming.trySendBlocking(bytes.hex())
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        incoming.trySendBlocking(text)
                    }
                },
            )

            it.invokeOnCancellation { webSocket.cancel() }
        }
    }
}
