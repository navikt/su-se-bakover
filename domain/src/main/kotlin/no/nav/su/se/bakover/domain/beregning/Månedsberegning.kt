package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.Grunnbeløp
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

data class Månedsberegning(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = now(),
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate = fraOgMed.plusMonths(1).minusDays(1),
    val grunnbeløp: Int = Grunnbeløp.`1G`.fraDato(fraOgMed).toInt(),
    val sats: Sats,
    val fradrag: Int,
    val beløp: Int = kalkulerBeløp(sats, fraOgMed, fradrag)
) {
    val satsBeløp: Int = sats.fraDatoAsInt(fraOgMed) / 12

    init {
        require(fraOgMed.dayOfMonth == 1) { "Månedsberegninger gjøres fra den første i måneden. Dato var=$fraOgMed" }
        require(tilOgMed.dayOfMonth == fraOgMed.lengthOfMonth()) { "Månedsberegninger avsluttes den siste i måneded. Dato var=$tilOgMed" }
    }

    companion object {
        // TODO AI: Se på möjligheterna för att ha fradrag som BigDecimal för att komma undan off-by-one fel.
        fun kalkulerBeløp(sats: Sats, fraOgMed: LocalDate, fradrag: Int) =
            BigDecimal(sats.fraDato(fraOgMed)).divide(BigDecimal(12), 0, RoundingMode.HALF_UP) // 20637,32 --> 20637
                .minus(BigDecimal(fradrag)) // Her runder vi av begge sider av regnestykket, kan potentiellt leda till fel.
                .max(BigDecimal.ZERO)
                .toInt()
    }
}
