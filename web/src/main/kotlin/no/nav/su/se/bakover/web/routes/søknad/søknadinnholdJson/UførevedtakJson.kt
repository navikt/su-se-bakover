package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import no.nav.su.se.bakover.domain.søknadinnhold.Uførevedtak

data class UførevedtakJson(
    val harUførevedtak: Boolean,
) {
    fun toUførevedtak() = Uførevedtak(harUførevedtak)

    companion object {
        fun Uførevedtak.toUførevedtakJson() = UførevedtakJson(this.harUførevedtak)
    }
}
