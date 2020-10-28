package no.nav.su.se.bakover.domain.brev.søknad.lukk

import no.nav.su.se.bakover.domain.brev.Brevdata
import no.nav.su.se.bakover.domain.brev.Brevtype

data class AvvistSøknadVedtakBrevdata(
    val personalia: Personalia,
) : Brevdata() {
    override fun brevtype() = Brevtype.AvvistSøknadVedtak
}

data class AvvistSøknadFritekstBrevdata(
    val personalia: Personalia,
) : Brevdata() {
    override fun brevtype() = Brevtype.AvvistSøknadFritekst
}
