package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FeilVedOpprettelseAvOppholdstillatelse
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Oppholdstillatelse

data class OppholdstillatelseJson(
    val erNorskStatsborger: Boolean,
    val harOppholdstillatelse: Boolean? = null,
    val typeOppholdstillatelse: String? = null,
    val statsborgerskapAndreLand: Boolean,
    val statsborgerskapAndreLandFritekst: String? = null,
) {
    fun toOppholdstillatelse(): Either<FeilVedOpprettelseAvOppholdstillatelseJson, Oppholdstillatelse> {
        val oppholdstillatelseType = typeOppholdstillatelse?.let {
            toOppholdstillatelseType(it).getOrElse {
                return FeilVedOpprettelseAvOppholdstillatelseJson.UgyldigInput(it).left()
            }
        }

        return Oppholdstillatelse.tryCreate(
            erNorskStatsborger = erNorskStatsborger,
            harOppholdstillatelse = harOppholdstillatelse,
            oppholdstillatelseType = oppholdstillatelseType,
            statsborgerskapAndreLand = statsborgerskapAndreLand,
            statsborgerskapAndreLandFritekst = statsborgerskapAndreLandFritekst,
        ).fold(
            { FeilVedOpprettelseAvOppholdstillatelseJson.DomeneFeil(it).left() },
            { it.right() },
        )
    }

    private fun toOppholdstillatelseType(str: String): Either<UgyldigSøknadsinnholdInputFraJson, Oppholdstillatelse.OppholdstillatelseType> {
        return when (str) {
            "midlertidig" -> Oppholdstillatelse.OppholdstillatelseType.MIDLERTIDIG.right()
            "permanent" -> Oppholdstillatelse.OppholdstillatelseType.PERMANENT.right()
            else -> UgyldigSøknadsinnholdInputFraJson(
                felt = "oppholdstillatelse.typeOppholdstillatelse",
                begrunnelse = "Ukjent verdi: $str",
            ).left()
        }
    }

    companion object {
        fun Oppholdstillatelse.toOppholdstillatelseJson() =
            OppholdstillatelseJson(
                erNorskStatsborger = this.erNorskStatsborger,
                harOppholdstillatelse = this.harOppholdstillatelse,
                typeOppholdstillatelse = this.oppholdstillatelseType?.toJson(),
                statsborgerskapAndreLand = this.statsborgerskapAndreLand,
                statsborgerskapAndreLandFritekst = this.statsborgerskapAndreLandFritekst,
            )
    }
}

private fun Oppholdstillatelse.OppholdstillatelseType.toJson(): String {
    return when (this) {
        Oppholdstillatelse.OppholdstillatelseType.MIDLERTIDIG -> "midlertidig"
        Oppholdstillatelse.OppholdstillatelseType.PERMANENT -> "permanent"
    }
}

sealed interface FeilVedOpprettelseAvOppholdstillatelseJson {
    data class DomeneFeil(val underliggendeFeil: FeilVedOpprettelseAvOppholdstillatelse) : FeilVedOpprettelseAvOppholdstillatelseJson
    data class UgyldigInput(val underliggendeFeil: UgyldigSøknadsinnholdInputFraJson) : FeilVedOpprettelseAvOppholdstillatelseJson
}
