package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.domain.søknadinnhold.Personopplysninger

data class PersonopplysningerJson(val fnr: String) {

    fun toPersonopplysninger() = Personopplysninger(fnr = Fnr(fnr))

    companion object {
        fun Personopplysninger.toPersonopplysningerJson() = PersonopplysningerJson(fnr = this.fnr.toString())
    }
}
