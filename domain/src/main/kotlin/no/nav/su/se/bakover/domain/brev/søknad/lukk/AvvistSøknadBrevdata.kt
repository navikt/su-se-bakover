package no.nav.su.se.bakover.domain.brev.søknad.lukk

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.BrevTemplate

data class AvvistSøknadVedtakBrevInnhold(
    val personalia: Personalia,
    val saksbehandlerNavn: String,
    val fritekst: String?,
) : BrevInnhold() {

    override val brevTemplate = BrevTemplate.AvvistSøknadVedtak
}

data class AvvistSøknadFritekstBrevInnhold(
    val personalia: Personalia,
    val saksbehandlerNavn: String,
    val fritekst: String,
) : BrevInnhold() {

    override val brevTemplate = BrevTemplate.AvvistSøknadFritekst

    @Suppress("unused")
    @JsonInclude
    val tittel: String = brevTemplate.tittel()
}
