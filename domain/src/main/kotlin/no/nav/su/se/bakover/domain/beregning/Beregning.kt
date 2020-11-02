package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.now
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Period
import java.util.UUID

const val TO_PROSENT = 0.02 // https://lovdata.no/dokument/NL/lov/2005-04-29-21 - § 9

data class Beregning(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = now(),
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val sats: Sats,
    val fradrag: List<Fradrag>,
    val månedsberegninger: List<Månedsberegning> = beregn(fraOgMed, tilOgMed, sats, fradrag)
) {

    init {
        require(fraOgMed.dayOfMonth == 1) { "Beregninger gjøres fra den første i måneden. Dato var=$fraOgMed" }
        require(tilOgMed.dayOfMonth == tilOgMed.lengthOfMonth()) { "Beregninger avsluttes den siste i måneded. Dato var=$tilOgMed" }
        require(fraOgMed.isBefore(tilOgMed)) { "Startdato ($fraOgMed) for beregning må være tidligere enn sluttdato ($tilOgMed)." }
        fradrag.forEach { require(it.perMåned() >= 0) { "Fradrag kan ikke være negative" } }
    }

    companion object {
        private fun beregn(
            fraOgMed: LocalDate,
            tilOgMed: LocalDate,
            sats: Sats,
            fradrag: List<Fradrag>
        ): List<Månedsberegning> {
            val antallMåneder = 0L until Period.between(fraOgMed, tilOgMed.plusDays(1)).toTotalMonths()
            return antallMåneder.map {
                Månedsberegning(
                    fraOgMed = fraOgMed.plusMonths(it),
                    sats = sats,
                    fradrag = fradrag.sumBy { f -> f.perMåned() }
                )
            }
        }
    }

    fun beløpErOverNullMenUnderMinstebeløp(): Boolean {
        val minstebeløp = månedsberegninger.map {
            BigDecimal(Sats.HØY.årsbeløp(it.fraOgMed))
                .multiply(BigDecimal(TO_PROSENT))
                .divide(BigDecimal(12), 0, RoundingMode.HALF_UP)
                .toInt()
        }.sum()
        val beregnetBeløp = månedsberegninger.sumBy { it.beløp }

        return beregnetBeløp in 1 until minstebeløp
    }

    fun beløpErNull() =
        månedsberegninger.sumBy { it.beløp } <= 0
}

fun fradragWithForventetInntekt(fradrag: List<Fradrag>, forventetInntekt: Int): List<Fradrag> {
    val (arbeidsinntektFradrag, andreFradrag) = fradrag.partition { it.type == Fradragstype.Arbeidsinntekt }

    val totalArbeidsinntekt = arbeidsinntektFradrag.sumBy { it.beløp }

    if (totalArbeidsinntekt >= forventetInntekt) {
        return fradrag
    }

    return andreFradrag.plus(
        Fradrag(
            type = Fradragstype.ForventetInntekt,
            beløp = forventetInntekt,
            utenlandskInntekt = null,
        )
    )
}
