package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.MicroInstant
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.Grunnbeløp
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

data class Månedsberegning(
    val id: UUID = UUID.randomUUID(),
    val opprettet: MicroInstant = now(),
    val fom: LocalDate,
    val tom: LocalDate = fom.plusMonths(1).minusDays(1),
    val grunnbeløp: Int = Grunnbeløp.`1G`.fraDato(fom).toInt(),
    val sats: Sats,
    val fradrag: Int,
    val beløp: Int = kalkulerBeløp(sats, fom, fradrag)
) {
    val satsBeløp: Int = sats.fraDatoAsInt(fom) / 12

    init {
        require(fom.dayOfMonth == 1) { "Månedsberegninger gjøres fra den første i måneden. Dato var=$fom" }
        require(tom.dayOfMonth == fom.lengthOfMonth()) { "Månedsberegninger avsluttes den siste i måneded. Dato var=$tom" }
    }

    companion object {
        fun kalkulerBeløp(sats: Sats, fom: LocalDate, fradrag: Int) =
            BigDecimal(sats.fraDato(fom)).divide(BigDecimal(12), 0, RoundingMode.HALF_UP)
                .minus(BigDecimal(fradrag))
                .max(BigDecimal.ZERO)
                .toInt()
    }
}
