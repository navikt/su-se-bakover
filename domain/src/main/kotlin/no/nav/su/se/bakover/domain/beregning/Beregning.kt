package no.nav.su.se.bakover.domain.beregning

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategyName
import java.util.UUID

interface Beregning : PeriodisertInformasjon {
    fun getId(): UUID
    fun getOpprettet(): Tidspunkt
    fun getSats(): Sats
    fun getMånedsberegninger(): List<Månedsberegning>
    fun getFradrag(): List<Fradrag>
    fun getSumYtelse(): Int
    fun getSumFradrag(): Double
    fun getFradragStrategyName(): FradragStrategyName
    fun getBegrunnelse(): String?

    /**
     * Sammenligner alle metodene  bortsett fraikke getId(), getOpprettet() og getBegrunnelse().
     * Laget for å kalles fra sub-klassene sine `override fun equals(other: Any?): Boolean` metoder.
     */
    fun equals(other: Beregning?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (getSats() != other.getSats()) return false
        if (getMånedsberegninger() != other.getMånedsberegninger()) return false
        if (getMånedsberegninger() != other.getMånedsberegninger()) return false
        if (getFradrag() != other.getFradrag()) return false
        if (getSumYtelse() != other.getSumYtelse()) return false
        if (getFradragStrategyName() != other.getFradragStrategyName()) return false
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
        .getOrHandle { return false }
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

object BeregningUtenMerknader

fun Beregning.finnMånederMedMerknadForAvslag(): Either<IngenMerknaderForAvslag, NonEmptyList<Pair<Månedsberegning, Merknad.Beregning>>> {
    return finnMånederMedMerknad()
        .getOrHandle { return IngenMerknaderForAvslag.left() }
        .map { (månedsberegning, _) ->
            månedsberegning.finnMerknadForAvslag()
                .mapLeft { IngenMerknaderForAvslag }
                .map { månedsberegning to it }
                .getOrHandle { IngenMerknaderForAvslag }
        }
        .filterIsInstance<Pair<Månedsberegning, Merknad.Beregning>>()
        .ifEmpty { return IngenMerknaderForAvslag.left() }
        .map { nonEmptyListOf(it) }
        .reduce { a, b -> a + b }
        .right()
}

object IngenMerknaderForAvslag

fun Beregning.finnFørsteMånedMedMerknadForAvslag(): Either<IngenMerknaderForAvslag, Pair<Månedsberegning, Merknad.Beregning>> {
    return finnMånederMedMerknadForAvslag()
        .getOrHandle { return IngenMerknaderForAvslag.left() }
        .minByOrNull { (månedsberegning, _) -> månedsberegning.periode.fraOgMed }!!
        .right()
}
