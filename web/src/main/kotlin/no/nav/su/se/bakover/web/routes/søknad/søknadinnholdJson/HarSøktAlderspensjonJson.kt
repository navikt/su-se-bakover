package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import no.nav.su.se.bakover.domain.søknadinnhold.HarSøktAlderspensjon

data class HarSøktAlderspensjonJson(
    val harSøktAlderspensjon: Boolean,
) {
    fun toHarSøktAlderspensjon() = HarSøktAlderspensjon(harSøktAlderspensjon)

    companion object {
        fun HarSøktAlderspensjon.toHarSøktAlderspensjonJson() = HarSøktAlderspensjonJson(this.harSøktAlderspensjon)
    }
}
