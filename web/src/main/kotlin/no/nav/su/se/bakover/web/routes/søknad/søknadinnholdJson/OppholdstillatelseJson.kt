package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import no.nav.su.se.bakover.domain.søknadinnhold.Oppholdstillatelse

data class OppholdstillatelseJson(
    val erNorskStatsborger: Boolean,
    val harOppholdstillatelse: Boolean? = null,
    val typeOppholdstillatelse: String? = null,
    val statsborgerskapAndreLand: Boolean,
    val statsborgerskapAndreLandFritekst: String? = null,
) {
    fun toOppholdstillatelse() = Oppholdstillatelse.tryCreate(
        erNorskStatsborger = erNorskStatsborger,
        harOppholdstillatelse = harOppholdstillatelse,
        oppholdstillatelseType = typeOppholdstillatelse?.let {
            toOppholdstillatelseType(it)
        },
        statsborgerskapAndreLand = statsborgerskapAndreLand,
        statsborgerskapAndreLandFritekst = statsborgerskapAndreLandFritekst,
    )

    private fun toOppholdstillatelseType(str: String): Oppholdstillatelse.OppholdstillatelseType {
        return when (str) {
            "midlertidig" -> Oppholdstillatelse.OppholdstillatelseType.MIDLERTIDIG
            "permanent" -> Oppholdstillatelse.OppholdstillatelseType.PERMANENT
            else -> throw IllegalArgumentException("Ikke gyldig oppholdstillatelse type")
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
