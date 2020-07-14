package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.domain.PersistentDomainObject
import no.nav.su.se.bakover.domain.VoidObserver
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.util.UUID

class Beregning(
    id: UUID = UUID.randomUUID(),
    opprettet: Instant = Instant.now(),
    private val fom: LocalDate,
    private val tom: LocalDate,
    private val sats: Sats,
    private val månedsberegninger: MutableList<Månedsberegning> = mutableListOf()
) : PersistentDomainObject<VoidObserver>(id, opprettet), DtoConvertable<BeregningDto> {
    private val antallMnd = Period.between(fom, tom.plusDays(1)).toTotalMonths()

    init {
        require(fom.dayOfMonth == 1) { "Beregninger gjøres fra den første i måneden. Dato var=$fom" }
        require(tom.dayOfMonth == fom.lengthOfMonth()) { "Beregninger avsluttes den siste i måneded. Dato var=$tom" }
        require(fom.isBefore(tom)) { "Startdato ($fom) for beregning må være tidligere enn sluttdato ($tom)." }
        if (månedsberegninger.isEmpty()) beregn()
    }

    private fun beregn() = (0L until antallMnd).map {
        månedsberegninger.add(
            Månedsberegning(
                fom = fom.plusMonths(it),
                sats = sats
            )
        )
    }

    override fun toDto(): BeregningDto =
        BeregningDto(
            id = id,
            opprettet = opprettet,
            fom = fom,
            tom = tom,
            sats = sats,
            månedsberegninger = månedsberegninger.map { it.toDto() }
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
    val månedsberegninger: List<MånedsberegningDto>
)
