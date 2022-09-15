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
        PERMANENT,
        ;
    }

    companion object {
        fun tryCreate(
            erNorskStatsborger: Boolean,
            harOppholdstillatelse: Boolean?,
            oppholdstillatelseType: OppholdstillatelseType?,
            statsborgerskapAndreLand: Boolean,
            statsborgerskapAndreLandFritekst: String?,
        ): Either<FeilVedOpprettelseAvOppholdstillatelse, Oppholdstillatelse> {
            validerHarOppholdstillatelse(
                erNorskStatsborger = erNorskStatsborger,
                harOppholdstillatelse = harOppholdstillatelse,
            ).mapLeft { return it.left() }

            validerOppholdstillatelseType(
                erNorskStatsborger = erNorskStatsborger,
                harOppholdstillatelse = harOppholdstillatelse,
                oppholdstillatelseType = oppholdstillatelseType,
            ).mapLeft { return it.left() }

            validerStatsborgerskapAndreLand(
                statsborgerskapAndreLand = statsborgerskapAndreLand,
                statsborgerskapAndreLandFritekst = statsborgerskapAndreLandFritekst,
            ).mapLeft { return it.left() }

            return Oppholdstillatelse(
                erNorskStatsborger = erNorskStatsborger,
                harOppholdstillatelse = harOppholdstillatelse,
                oppholdstillatelseType = oppholdstillatelseType,
                statsborgerskapAndreLand = statsborgerskapAndreLand,
                statsborgerskapAndreLandFritekst = statsborgerskapAndreLandFritekst,
            ).right()
        }

        private fun validerHarOppholdstillatelse(
            erNorskStatsborger: Boolean,
            harOppholdstillatelse: Boolean?,
        ) = if (!erNorskStatsborger && harOppholdstillatelse == null) {
            FeilVedOpprettelseAvOppholdstillatelse.OppholdstillatelseErIkkeUtfylt.left()
        } else {
            Unit.right()
        }

        private fun validerOppholdstillatelseType(
            erNorskStatsborger: Boolean,
            harOppholdstillatelse: Boolean?,
            oppholdstillatelseType: OppholdstillatelseType?,
        ) = if (!erNorskStatsborger && harOppholdstillatelse == true && oppholdstillatelseType == null) {
            FeilVedOpprettelseAvOppholdstillatelse.TypeOppholdstillatelseErIkkeUtfylt.left()
        } else {
            Unit.right()
        }

        private fun validerStatsborgerskapAndreLand(
            statsborgerskapAndreLand: Boolean,
            statsborgerskapAndreLandFritekst: String?,
        ) = if (statsborgerskapAndreLand && statsborgerskapAndreLandFritekst == null) {
            FeilVedOpprettelseAvOppholdstillatelse.FritekstForStatsborgerskapErIkkeUtfylt.left()
        } else {
            Unit.right()
        }
    }
}

sealed interface FeilVedOpprettelseAvOppholdstillatelse {
    object OppholdstillatelseErIkkeUtfylt : FeilVedOpprettelseAvOppholdstillatelse
    object TypeOppholdstillatelseErIkkeUtfylt : FeilVedOpprettelseAvOppholdstillatelse
    object FritekstForStatsborgerskapErIkkeUtfylt : FeilVedOpprettelseAvOppholdstillatelse
}
