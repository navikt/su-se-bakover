package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import no.nav.su.se.bakover.domain.søknad.søknadinnhold.OppholdstillatelseAlder

data class OppholdstillatelseAlderJson(
    val eøsborger: Boolean?,
    val familieforening: Boolean?,
) {
    fun toOppholdstillatelseAlder() =
        OppholdstillatelseAlder(eøsborger = eøsborger, familiegjenforening = familieforening)

    companion object {
        fun OppholdstillatelseAlder.toOppholdstillatelseAlderJson() =
            OppholdstillatelseAlderJson(eøsborger = this.eøsborger, familieforening = this.familiegjenforening)
    }
}
