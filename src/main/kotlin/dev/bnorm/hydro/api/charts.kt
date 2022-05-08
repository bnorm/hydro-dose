@file:OptIn(KtorExperimentalLocationsAPI::class)

package dev.bnorm.hydro.api

import dev.bnorm.hydro.ChartService
import dev.bnorm.hydro.dto.toDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.locations.*
import io.ktor.server.locations.put
import io.ktor.server.response.*
import io.ktor.server.routing.*

@Location("/charts/{name}/weeks/{week}")
data class Chart(
    val name: String,
    val week: Long,
) {
    @Location("/activate")
    data class Activate(
        val parent: Chart,
    )

    @Location("/dose")
    data class Dose(
        val parent: Chart,
    )
}

@Location("/charts")
object Charts {
    @Location("/active")
    data class Active(
        val parent: Charts,
    ) {
        @Location("/dose")
        data class Dose(
            val parent: Active,
        )
    }
}

fun Route.chartsApi(chartService: ChartService) {
    // Charts

    get<Chart> { chart ->
        call.respond(chartService.get(chart.name, chart.week).toDto())
    }
    put<Chart.Activate> { (chart) ->
        chartService.activate(chart.name, chart.week)
        call.respond(HttpStatusCode.NoContent)
    }
    put<Chart.Dose> { (chart) ->
        chartService.dose(chart.name, chart.week)
        call.respond(HttpStatusCode.NoContent)
    }

    get<Charts.Active> {
        call.respond(chartService.active().toDto())
    }

    put<Charts.Active.Dose> {
        chartService.doseActive()
        call.respond(HttpStatusCode.NoContent)
    }
}
