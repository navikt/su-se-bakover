package no.nav.su.se.bakover.domain.søknadinnhold

import arrow.core.Either
import arrow.core.left
import arrow.core.right

data class Oppholdstillatelse private constructor(
    val erNorskStatsborger: Boolean,
    val harOppholdstillatelse: Boolean? = null,
    val oppholdstillatelseType: OppholdstillatelseType? = null,
    val statsborgerskapAndreLand: Boolean,
    val statsborgerskapAndreLandFritekst: String? = null,
) {
    enum class OppholdstillatelseType() {
        MIDLERTIDIG,
        PERMANENT;
    }

    companion object {
        fun tryCreate(
            erNorskStatsborger: Boolean,
            harOppholdstillatelse: Boolean?,
            oppholdstillatelseType: OppholdstillatelseType?,
            statsborgerskapAndreLand: Boolean,
            statsborgerskapAndreLandFritekst: String?,
        ): Either<FeilVedOpprettelseAvOppholdstillatelse, Oppholdstillatelse> {
            if (!erNorskStatsborger) {
                if (harOppholdstillatelse == null) {
                    return FeilVedOpprettelseAvOppholdstillatelse.OppholdstillatelseErIkkeUtfylt.left()
                }

                if (harOppholdstillatelse == true && oppholdstillatelseType == null) {
                    return FeilVedOpprettelseAvOppholdstillatelse.TypeOppholdstillatelseErIkkeUtfylt.left()
                }
            }
            if (statsborgerskapAndreLand) {
                if (statsborgerskapAndreLandFritekst == null) {
                    return FeilVedOpprettelseAvOppholdstillatelse.FritekstForStatsborgerskapErIkkeUtfylt.left()
                }
            }

            return Oppholdstillatelse(
                erNorskStatsborger = erNorskStatsborger,
                harOppholdstillatelse = harOppholdstillatelse,
                oppholdstillatelseType = oppholdstillatelseType,
                statsborgerskapAndreLand = statsborgerskapAndreLand,
                statsborgerskapAndreLandFritekst = statsborgerskapAndreLandFritekst,
            ).right()
        }
    }
}

sealed interface FeilVedOpprettelseAvOppholdstillatelse {
    object OppholdstillatelseErIkkeUtfylt : FeilVedOpprettelseAvOppholdstillatelse
    object TypeOppholdstillatelseErIkkeUtfylt : FeilVedOpprettelseAvOppholdstillatelse
    object FritekstForStatsborgerskapErIkkeUtfylt : FeilVedOpprettelseAvOppholdstillatelse
}
