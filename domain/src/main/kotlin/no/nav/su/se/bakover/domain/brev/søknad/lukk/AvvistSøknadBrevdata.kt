package no.nav.su.se.bakover.domain.brev.søknad.lukk

import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.BrevTemplate

data class AvvistSøknadVedtakBrevInnhold(
    val personalia: Personalia,
    val fritekst: String?
) : BrevInnhold() {
    override fun brevTemplate() = BrevTemplate.AvvistSøknadVedtak
}

data class AvvistSøknadFritekstBrevInnhold(
    val personalia: Personalia,
    val tittel: String = getBrevtype().tittel(),
    val fritekst: String,
) : BrevInnhold() {
    override fun brevTemplate() = getBrevtype()

    private companion object {
        fun getBrevtype() = BrevTemplate.AvvistSøknadFritekst
    }
}
