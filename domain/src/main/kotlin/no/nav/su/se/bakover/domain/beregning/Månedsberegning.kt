package no.nav.su.se.bakover.domain.beregning

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import behandling.domain.beregning.Merknad
import behandling.domain.beregning.fradrag.FradragForMåned
import behandling.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.PeriodisertInformasjon
import satser.domain.Satskategori
import satser.domain.supplerendestønad.FullSupplerendeStønadForMåned

interface Månedsberegning : PeriodisertInformasjon {
    fun getSumYtelse(): Int
    fun getSumFradrag(): Double
    fun getBenyttetGrunnbeløp(): Int? // bare relevant for uføre
    fun getSats(): Satskategori
    fun getSatsbeløp(): Double
    fun getFradrag(): List<FradragForMåned>
    fun getFribeløpForEps(): Double
    fun getMerknader(): List<Merknad.Beregning>

    fun erFradragForEpsBenyttetIBeregning() =
        getFradrag().any { it.fradragstype == Fradragstype.BeregnetFradragEPS }

    val måned: Måned
    val fullSupplerendeStønadForMåned: FullSupplerendeStønadForMåned

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
        if (måned != other.måned) return false
        if (periode != other.periode) return false
        return true
    }

    /**
     * Det er ikke lov å ha default implementasjon i interfaces for Any.
     * Denne vil tvinge sub-klassene til å override.
     */
    override fun equals(other: Any?): Boolean
}

/**
 * Godtar bare at det eksisterer 1 merknad som kan føre til avslag per måned.
 * Se logikk for opprettelsen av merknader i [Merknader.Beregningsmerknad]
 */
fun Månedsberegning.finnMerknadForAvslag(): Either<IngenMerknaderForAvslag, Merknad.Beregning> {
    return getMerknader().mapNotNull {
        when (it) {
            is Merknad.Beregning.Avslag -> it
            else -> null
        }
    }.let {
        when (it.isEmpty()) {
            true -> IngenMerknaderForAvslag.left()
            false -> it.single().right()
        }
    }
}
