package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Personopplysninger

data class PersonopplysningerJson(val fnr: String) {

    fun toPersonopplysninger() = Personopplysninger(fnr = Fnr(fnr))

    companion object {
        fun Personopplysninger.toPersonopplysningerJson() = PersonopplysningerJson(fnr = this.fnr.toString())
    }
}
