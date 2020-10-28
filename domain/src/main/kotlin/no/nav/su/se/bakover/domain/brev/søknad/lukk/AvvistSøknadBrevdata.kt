package no.nav.su.se.bakover.domain.brev.søknad.lukk

import no.nav.su.se.bakover.domain.brev.BrevTemplate
import no.nav.su.se.bakover.domain.brev.Brevdata

data class AvvistSøknadVedtakBrevdata(
    val personalia: Personalia,
) : Brevdata() {
    override fun brevtype() = BrevTemplate.AvvistSøknadVedtak
}

data class AvvistSøknadFritekstBrevdata(
    val personalia: Personalia,
    val tittel: String = getBrevtype().tittel(),
    val fritekst: String,
) : Brevdata() {
    override fun brevtype() = getBrevtype()

    private companion object {
        fun getBrevtype() = BrevTemplate.AvvistSøknadFritekst
    }
}
