package no.nav.su.se.bakover.common.infrastructure

import no.nav.su.se.bakover.common.domain.tid.periode.PeriodeMedOptionalTilOgMed
import java.time.LocalDate

data class PeriodeMedOptionalTilOgMedJson(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate?,
) {
    fun toDomain(): PeriodeMedOptionalTilOgMed = PeriodeMedOptionalTilOgMed(fraOgMed, tilOgMed)

    companion object {
        fun PeriodeMedOptionalTilOgMed.toJson(): PeriodeMedOptionalTilOgMedJson =
            PeriodeMedOptionalTilOgMedJson(fraOgMed, tilOgMed)
    }
}
