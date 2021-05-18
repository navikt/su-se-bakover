package no.nav.su.se.bakover.domain.beregning.fradrag

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon

interface Fradrag : PeriodisertInformasjon {
    val fradragstype: Fradragstype
    val månedsbeløp: Double
    val utenlandskInntekt: UtenlandskInntekt? // TODO can we pls do something about this one?
    val tilhører: FradragTilhører

    /**
     * Sammenligner alle metodene.
     * Laget for å kalles fra sub-klassene sine `override fun equals(other: Any?): Boolean` metoder.
     */
    fun equals(other: Fradrag?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (fradragstype != other.fradragstype) return false
        if (månedsbeløp != other.månedsbeløp) return false
        if (utenlandskInntekt != other.utenlandskInntekt) return false
        if (tilhører != other.tilhører) return false
        return true
    }

    /**
     * Det er ikke lov å ha default implementasjon i interfaces for Any.
     * Denne vil tvinge sub-klassene til å override.
     */
    override fun equals(other: Any?): Boolean
}

enum class FradragTilhører {
    BRUKER,
    EPS;

    companion object {
        fun tryParse(value: String): Either<UgyldigFradragTilhører, Fradragstype> {
            return Fradragstype.values().firstOrNull { it.name == value }?.right() ?: UgyldigFradragTilhører.left()
        }
    }

    object UgyldigFradragTilhører
}
