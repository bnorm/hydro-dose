package dev.bnorm.hydro

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

    suspend fun dose(name: String, week: Long) {
        log.debug("Dosing for name={} week={}", name, week)
        val feedChart = feedChartQueries.selectByNameAndWeek(name, week).executeAsOne()
        log.debug("Dosing for feedChart={}", feedChart)
        coroutineScope {
            launch {
                val ph = sensorService[SensorType.Ph].read()
                log.debug("pH reading={}", ph)
                if (ph > 6.3) {
                    pumpService[PumpType.PhDown].dispense(2.0)
                } else if (ph < 5.7) {
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
