package dev.bnorm.hydro.client

import dev.bnorm.hydro.client.elevated.model.auth.AuthenticatedDevice
import dev.bnorm.hydro.client.elevated.model.auth.AuthorizationToken
import dev.bnorm.hydro.client.elevated.model.auth.JwtTokenUsage
import dev.bnorm.hydro.client.elevated.model.auth.Password
import dev.bnorm.hydro.client.elevated.model.devices.Device
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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class ElevatedClient {
    companion object {
        private const val API_HOSTNAME = "https://elevated.bnorm.dev"

        private val environment = System.getenv().also {
            println(it)
        }

        private val DEVICE_ID by environment
        private val DEVICE_KEY by environment

        private val SENSOR_PH_ID by environment
        private val SENSOR_EC_ID by environment
    }

    private var authorization: String? = null

    @OptIn(JwtTokenUsage::class)
    private fun authorize(token: AuthorizationToken) {
        authorization = "${token.type} ${token.value.value}"
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json()
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

    suspend fun recordPhReading(value: Double, timestamp: Instant = Clock.System.now()): SensorReading {
        val response = client.post("$API_HOSTNAME/api/v1/sensors/${SENSOR_PH_ID}/readings/record") {
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
        val response = client.post("$API_HOSTNAME/api/v1/sensors/${SENSOR_EC_ID}/readings/record") {
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
