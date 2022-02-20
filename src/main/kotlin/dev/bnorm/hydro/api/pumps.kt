@file:OptIn(KtorExperimentalLocationsAPI::class)

package dev.bnorm.hydro.api

import dev.bnorm.hydro.PumpService
import dev.bnorm.hydro.dto.toDto
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.Route

@Location("/pumps")
object Pumps

@Location("/pumps/{id}")
data class Pump(
    val id: Int,
) {
    data class Dispense(
        val pump: Pump,
        val amount: Int, // milliliters
    )
}

fun Route.pumpsApi(pumpService: PumpService) {
    get<Pumps> {
        val pumps = pumpService.all
        call.respond(pumps.map { it.toDto() })
    }
    get<Pump> { (id) ->
        val pump = pumpService[id] ?: throw NotFoundException()
        call.respond(pump.toDto())
    }
    put<Pump.Dispense> { (pump, amount) ->
        val pump = pumpService[pump.id] ?: throw NotFoundException()
        pump.dispense(amount.toDouble())
        call.respond(pump.toDto())
    }
}
