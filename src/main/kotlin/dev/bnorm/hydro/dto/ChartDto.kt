package dev.bnorm.hydro.dto

import dev.bnorm.hydro.db.Chart
import kotlinx.serialization.Serializable

@Serializable
data class ChartDto(
    val name: String,
    val week: Long,
    val targetEcLow: Long,
    val targetEcHigh: Long,
    val microMl: Double,
    val groMl: Double,
    val bloomMl: Double,
    val active: Boolean,
)

fun Chart.toDto(): ChartDto {
    return ChartDto(
        name = name,
        week = week,
        targetEcLow = target_ec_low,
        targetEcHigh = target_ec_high,
        microMl = micro_ml,
        groMl = gro_ml,
        bloomMl = bloom_ml,
        active = active,
    )
}
