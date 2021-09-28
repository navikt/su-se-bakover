package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype

interface Månedsberegning : PeriodisertInformasjon {
    fun getSumYtelse(): Int
    fun getSumFradrag(): Double
    fun getBenyttetGrunnbeløp(): Int
    fun getSats(): Sats
    fun getSatsbeløp(): Double
    fun getFradrag(): List<Fradrag>
    fun getFribeløpForEps(): Double
    fun getMerknader(): List<Merknad>

    fun erFradragForEpsBenyttetIBeregning() =
        getFradrag().any { it.fradragstype == Fradragstype.BeregnetFradragEPS }

    fun erSumYtelseUnderMinstebeløp() =
        getSumYtelse() == 0 && getFradrag().any { it.fradragstype == Fradragstype.UnderMinstenivå }

    /**
     * Sammenligner alle metodene.
     * Laget for å kalles fra sub-klassene sine `override fun equals(other: Any?): Boolean` metoder.
     */
    fun equals(other: Månedsberegning?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (getSumYtelse() != other.getSumYtelse()) return false
        if (getSumFradrag() != other.getSumFradrag()) return false
        if (getBenyttetGrunnbeløp() != other.getBenyttetGrunnbeløp()) return false
        if (getSats() != other.getSats()) return false
        if (getSatsbeløp() != other.getSatsbeløp()) return false
        if (getFradrag() != other.getFradrag()) return false
        if (getFribeløpForEps() != other.getFribeløpForEps()) return false
        return true
    }

    /**
     * Det er ikke lov å ha default implementasjon i interfaces for Any.
     * Denne vil tvinge sub-klassene til å override.
     */
    override fun equals(other: Any?): Boolean
}
