package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.domain.Grunnbeløp
import no.nav.su.se.bakover.domain.PersistentDomainObject
import no.nav.su.se.bakover.domain.VoidObserver
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class Månedsberegning(
    id: UUID = UUID.randomUUID(),
    opprettet: Instant = Instant.now(),
    private val fom: LocalDate,
    private val tom: LocalDate = fom.plusMonths(1).minusDays(1),
    private val grunnbeløp: Int = Grunnbeløp.`1G`.fraDato(fom).toInt(),
    private val sats: Sats,
    private val beløp: Int = (BigDecimal(sats.fraDato(fom)).divide(BigDecimal(12), 0, RoundingMode.HALF_UP)).toInt()
) : PersistentDomainObject<VoidObserver>(id, opprettet) {
    init {
        require(fom.dayOfMonth == 1) { "Månedsberegninger gjøres fra den første i måneden. Dato var=$fom" }
        require(tom.dayOfMonth == fom.lengthOfMonth()) { "Månedsberegninger avsluttes den siste i måneded. Dato var=$tom" }
    }

    fun toDto() = MånedsberegningDto(
        id = id,
        opprettet = opprettet,
        fom = fom,
        tom = tom,
        grunnbeløp = grunnbeløp,
        sats = sats,
        beløp = beløp
    )
}

data class MånedsberegningDto(
    val id: UUID,
    val opprettet: Instant,
    val fom: LocalDate,
    val tom: LocalDate,
    val grunnbeløp: Int,
    val sats: Sats,
    val beløp: Int
)
