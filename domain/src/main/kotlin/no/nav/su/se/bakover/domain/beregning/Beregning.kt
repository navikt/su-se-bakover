package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.PersistentDomainObject
import no.nav.su.se.bakover.domain.VoidObserver
import java.time.LocalDate
import java.time.Period
import java.util.UUID

data class Beregning(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = now(),
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val sats: Sats,
    val fradrag: List<Fradrag>,
    val forventetInntekt: Int,
    val månedsberegninger: List<Månedsberegning> = beregn(fraOgMed, tilOgMed, sats, fradrag, forventetInntekt)
) : PersistentDomainObject<VoidObserver>() {

    init {
        require(fraOgMed.dayOfMonth == 1) { "Beregninger gjøres fra den første i måneden. Dato var=$fraOgMed" }
        require(tilOgMed.dayOfMonth == tilOgMed.lengthOfMonth()) { "Beregninger avsluttes den siste i måneded. Dato var=$tilOgMed" }
        require(fraOgMed.isBefore(tilOgMed)) { "Startdato ($fraOgMed) for beregning må være tidligere enn sluttdato ($tilOgMed)." }
        fradrag.forEach { require(it.perMåned() >= 0) { "Fradrag kan ikke være negative" } }
        require(forventetInntekt >= 0) { "Forventet inntekt kan ikke være negativ" }
    }

    companion object {
        private fun beregn(
            fraOgMed: LocalDate,
            tilOgMed: LocalDate,
            sats: Sats,
            fradrag: List<Fradrag>,
            forventetInntekt: Int
        ): List<Månedsberegning> {
            val antallMåneder = 0L until Period.between(fraOgMed, tilOgMed.plusDays(1)).toTotalMonths()
            return antallMåneder.map {
                Månedsberegning(
                    fraOgMed = fraOgMed.plusMonths(it),
                    sats = sats,
                    fradrag = fradragWithForventetInntekt(fradrag, forventetInntekt).sumBy { f -> f.perMåned() }
                )
            }
        }
    }

    object Opprettet : Comparator<Beregning> {
        override fun compare(o1: Beregning?, o2: Beregning?): Int {
            return o1!!.opprettet.toEpochMilli().compareTo(o2!!.opprettet.toEpochMilli())
        }
    }
}

private fun fradragWithForventetInntekt(fradrag: List<Fradrag>, forventetInntekt: Int): List<Fradrag> {
    val (arbeidsinntektFradrag, andreFradrag) = fradrag.partition { it.type == Fradragstype.Arbeidsinntekt }

    val totalArbeidsinntekt = arbeidsinntektFradrag.sumBy { it.beløp }

    if (totalArbeidsinntekt > forventetInntekt) {
        return fradrag
    }

    return andreFradrag.plus(Fradrag(type = Fradragstype.ForventetInntekt, beløp = forventetInntekt))
}
