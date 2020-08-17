package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.PersistentDomainObject
import no.nav.su.se.bakover.domain.VoidObserver
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.util.UUID

data class Beregning(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Instant = now(),
    private val fom: LocalDate,
    private val tom: LocalDate,
    private val sats: Sats,
    private val fradrag: List<Fradrag>,
    private val månedsberegninger: MutableList<Månedsberegning> = mutableListOf()
) : PersistentDomainObject<VoidObserver>(), DtoConvertable<BeregningDto> {
    private val antallMnd = Period.between(fom, tom.plusDays(1)).toTotalMonths()

    init {
        require(fom.dayOfMonth == 1) { "Beregninger gjøres fra den første i måneden. Dato var=$fom" }
        require(tom.dayOfMonth == tom.lengthOfMonth()) { "Beregninger avsluttes den siste i måneded. Dato var=$tom" }
        require(fom.isBefore(tom)) { "Startdato ($fom) for beregning må være tidligere enn sluttdato ($tom)." }
        fradrag.forEach { require(it.perMåned() >= 0) { "Fradrag kan ikke være negative" } }
        if (månedsberegninger.isEmpty()) beregn()
    }

    private fun beregn() = (0L until antallMnd).map {
        månedsberegninger.add(
            Månedsberegning(
                fom = fom.plusMonths(it),
                sats = sats,
                fradrag = fradrag.sumBy { f -> f.perMåned() }
            )
        )
    }

    // TODO må fikses for å støtte flere perioder med ulikt beløp
    fun månedsbeløp() = månedsberegninger.first().beløp

    override fun toDto(): BeregningDto =
        BeregningDto(
            id = id,
            opprettet = opprettet,
            fom = fom,
            tom = tom,
            sats = sats,
            månedsberegninger = månedsberegninger.map { it.toDto() },
            fradrag = fradrag.map { it.toDto() }
        )

    object Opprettet : Comparator<Beregning> {
        override fun compare(o1: Beregning?, o2: Beregning?): Int {
            return (o1!!.opprettet.toEpochMilli() - o2!!.opprettet.toEpochMilli()).toInt()
        }
    }
}

data class BeregningDto(
    val id: UUID,
    val opprettet: Instant,
    val fom: LocalDate,
    val tom: LocalDate,
    val sats: Sats,
    val månedsberegninger: List<MånedsberegningDto>,
    val fradrag: List<FradragDto>
) {
    fun getMånedsbeløp() = månedsberegninger.firstOrNull()?.beløp

    fun getSatsbeløp() = månedsberegninger.firstOrNull()?.let { it.beløp + it.fradrag }
}
