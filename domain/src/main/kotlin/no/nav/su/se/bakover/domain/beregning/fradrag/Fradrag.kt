package no.nav.su.se.bakover.domain.beregning.fradrag

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon

interface Fradrag : PeriodisertInformasjon {
    fun getFradragstype(): Fradragstype
    fun getMånedsbeløp(): Double
    fun getUtenlandskInntekt(): UtenlandskInntekt? // TODO can we pls do something about this one?
    fun getTilhører(): FradragTilhører

    /**
     * Sammenligner alle metodene.
     * Laget for å kalles fra sub-klassene sine `override fun equals(other: Any?): Boolean` metoder.
     */
    fun equals(other: Fradrag?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (getFradragstype() != other.getFradragstype()) return false
        if (getMånedsbeløp() != other.getMånedsbeløp()) return false
        if (getUtenlandskInntekt() != other.getUtenlandskInntekt()) return false
        if (getTilhører() != other.getTilhører()) return false
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
