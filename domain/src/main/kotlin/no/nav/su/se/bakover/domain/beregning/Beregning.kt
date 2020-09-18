package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.PersistentDomainObject
import no.nav.su.se.bakover.domain.VoidObserver
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.util.UUID

data class Beregning(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Instant = now(),
    val fom: LocalDate,
    val tom: LocalDate,
    val sats: Sats,
    val fradrag: List<Fradrag>,
    val månedsberegninger: List<Månedsberegning> = beregn(fom, tom, sats, fradrag)
) : PersistentDomainObject<VoidObserver>() {

    init {
        require(fom.dayOfMonth == 1) { "Beregninger gjøres fra den første i måneden. Dato var=$fom" }
        require(tom.dayOfMonth == tom.lengthOfMonth()) { "Beregninger avsluttes den siste i måneded. Dato var=$tom" }
        require(fom.isBefore(tom)) { "Startdato ($fom) for beregning må være tidligere enn sluttdato ($tom)." }
        fradrag.forEach { require(it.perMåned() >= 0) { "Fradrag kan ikke være negative" } }
    }

    companion object {
        private fun beregn(
            fom: LocalDate,
            tom: LocalDate,
            sats: Sats,
            fradrag: List<Fradrag>
        ): List<Månedsberegning> {
            val antallMåneder = 0L until Period.between(fom, tom.plusDays(1)).toTotalMonths()
            return antallMåneder.map {
                Månedsberegning(
                    fom = fom.plusMonths(it),
                    sats = sats,
                    fradrag = fradrag.sumBy { f -> f.perMåned() }
                )
            }
        }
    }

    fun hentPerioder() =
        månedsberegninger.groupBy { it.beløp }.map {
            BeregningsPeriode(
                fom = it.value.minByOrNull { it.fom }!!.fom,
                tom = it.value.maxByOrNull { it.tom }!!.tom,
                beløp = it.key,
            )
        }

    object Opprettet : Comparator<Beregning> {
        override fun compare(o1: Beregning?, o2: Beregning?): Int {
            return o1!!.opprettet.toEpochMilli().compareTo(o2!!.opprettet.toEpochMilli())
        }
    }
}

data class BeregningsPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: Int,
)
