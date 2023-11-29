package beregning.domain

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import beregning.domain.fradrag.Fradrag
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.PeriodisertInformasjon
import java.util.UUID

interface Beregning : PeriodisertInformasjon {
    fun getId(): UUID
    fun getOpprettet(): Tidspunkt
    fun getMånedsberegninger(): List<Månedsberegning>
    fun getFradrag(): List<Fradrag>
    fun getSumYtelse(): Int
    fun getSumFradrag(): Double
    fun getBegrunnelse(): String?

    /**
     * Sammenligner alle metodene  bortsett fraikke getId(), getOpprettet() og getBegrunnelse().
     * Laget for å kalles fra sub-klassene sine `override fun equals(other: Any?): Boolean` metoder.
     */
    fun equals(other: Beregning?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (getMånedsberegninger() != other.getMånedsberegninger()) return false
        if (getFradrag() != other.getFradrag()) return false
        if (getSumYtelse() != other.getSumYtelse()) return false
        return true
    }

    /**
     * Det er ikke lov å ha default implementasjon i interfaces for Any.
     * Denne vil tvinge sub-klassene til å override.
     */
    override fun equals(other: Any?): Boolean
}

fun Beregning.harAlleMånederMerknadForAvslag(): Boolean {
    return finnMånederMedMerknadForAvslag()
        .getOrElse { return false }
        .count() == getMånedsberegninger().count()
}

fun Beregning.finnMånederMedMerknad(): Either<BeregningUtenMerknader, NonEmptyList<Pair<Månedsberegning, List<Merknad.Beregning>>>> {
    return getMånedsberegninger()
        .filterNot { it.getMerknader().isEmpty() }
        .ifEmpty { return BeregningUtenMerknader.left() }
        .map { nonEmptyListOf(it to it.getMerknader()) }
        .reduce { a, b -> a + b }
        .right()
}

data object BeregningUtenMerknader

fun Beregning.finnMånederMedMerknadForAvslag(): Either<IngenMerknaderForAvslag, NonEmptyList<Pair<Månedsberegning, Merknad.Beregning>>> {
    return finnMånederMedMerknad()
        .getOrElse { return IngenMerknaderForAvslag.left() }
        .map { (månedsberegning, _) ->
            månedsberegning.finnMerknadForAvslag()
                .mapLeft { IngenMerknaderForAvslag }
                .map { månedsberegning to it }
                .getOrElse { IngenMerknaderForAvslag }
        }
        .filterIsInstance<Pair<Månedsberegning, Merknad.Beregning>>()
        .ifEmpty { return IngenMerknaderForAvslag.left() }
        .map { nonEmptyListOf(it) }
        .reduce { a, b -> a + b }
        .right()
}

fun Beregning.finnFørsteMånedMedMerknadForAvslag(): Either<IngenMerknaderForAvslag, Pair<Månedsberegning, Merknad.Beregning>> {
    return finnMånederMedMerknadForAvslag()
        .getOrElse { return IngenMerknaderForAvslag.left() }
        .minByOrNull { (månedsberegning, _) -> månedsberegning.periode.fraOgMed }!!
        .right()
}
