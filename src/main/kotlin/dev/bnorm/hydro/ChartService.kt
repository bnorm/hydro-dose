package dev.bnorm.hydro

import dev.bnorm.hydro.db.Chart
import dev.bnorm.hydro.db.ChartQueries
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager

class ChartService(
    private val feedChartQueries: ChartQueries,
    private val sensorService: SensorService,
    private val pumpService: PumpService,
) {
    private val log = LogManager.getLogger(ChartService::class.java)

    suspend fun active(): Chart {
        return feedChartQueries.selectByActive()
            .executeAsOne()
    }

    suspend fun get(name: String, week: Long): Chart {
        return feedChartQueries.selectByNameAndWeek(name, week)
            .executeAsOne()
    }

    suspend fun activate(name: String, week: Long) {
        log.debug("Activating chart name={} week={}", name, week)
        feedChartQueries.activateByNameAndWeek(name, week)
    }

    suspend fun doseActive() {
        log.debug("Dosing for active chart")
        dose(feedChartQueries.selectByActive().executeAsOne())
    }

    suspend fun dose(name: String, week: Long) {
        log.debug("Dosing for name={} week={}", name, week)
        dose(feedChartQueries.selectByNameAndWeek(name, week).executeAsOne())
    }

    private suspend fun dose(feedChart: Chart) {
        log.debug("Dosing for feedChart={}", feedChart)
        coroutineScope {
            launch {
                val ph = sensorService[SensorType.Ph].read()
                log.debug("pH reading={}", ph)
                if (ph > 5.9) {
                    pumpService[PumpType.PhDown].dispense(1.0)
                } else if (ph < 5.3) {
                    // TODO throw ph too low?
                }
            }
            launch {
                val ec = sensorService[SensorType.Ec].read()
                log.debug("EC reading={}", ec)
                if (ec < feedChart.target_ec_low) {
                    pumpService[PumpType.Micro].dispense(feedChart.micro_ml / 2)
                    pumpService[PumpType.Gro].dispense(feedChart.gro_ml / 2)
                    pumpService[PumpType.Bloom].dispense(feedChart.bloom_ml / 2)
                } else if (ec > feedChart.target_ec_high) {
                    // TODO throw ec too high?
                }
            }
        }
    }
}
