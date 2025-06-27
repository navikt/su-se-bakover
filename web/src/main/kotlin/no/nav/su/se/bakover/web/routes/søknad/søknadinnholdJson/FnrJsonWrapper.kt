package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FnrWrapper

data class FnrJsonWrapper(val fnr: String) {

    fun toFnrWrapper() = FnrWrapper(fnr = Fnr(fnr))

    companion object {
        fun FnrWrapper.toFnrJsonWrapper() = FnrJsonWrapper(fnr = this.fnr.toString())
    }
}
