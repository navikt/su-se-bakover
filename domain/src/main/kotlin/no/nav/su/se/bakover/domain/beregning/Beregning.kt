package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategyName
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
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

    fun alleMånederErUnderMinstebeløp(): Boolean = getMånedsberegninger().all { it.erSumYtelseUnderMinstebeløp() }
    fun alleMånederHarBeløpLik0(): Boolean = getMånedsberegninger().all { it.getSumYtelse() == 0 }
}

/* Sosialstønad skal ikke kunne føre til avslag eller under minste grense for utbetaling */
fun Beregning.utenSosialstønad(): Beregning = BeregningFactory.ny(
    periode = periode,
    sats = getSats(),
    fradrag = getFradrag().filterNot { it.fradragstype == Fradragstype.Sosialstønad },
    fradragStrategy = FradragStrategy.fromName(getFradragStrategyName()),
    begrunnelse = getBegrunnelse()
)
