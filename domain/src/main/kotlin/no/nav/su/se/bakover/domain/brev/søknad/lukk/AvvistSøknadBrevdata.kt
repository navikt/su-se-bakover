package no.nav.su.se.bakover.domain.brev.søknad.lukk

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.domain.brev.BrevTemplate
import no.nav.su.se.bakover.domain.brev.PdfInnhold

data class AvvistSøknadVedtakPdfInnhold(
    val personalia: Personalia,
    val saksbehandlerNavn: String,
    val fritekst: String?,
) : PdfInnhold() {

    override val brevTemplate = BrevTemplate.AvvistSøknadVedtak
}

data class AvvistSøknadFritekstPdfInnhold(
    val personalia: Personalia,
    val saksbehandlerNavn: String,
    val fritekst: String,
) : PdfInnhold() {

    override val brevTemplate = BrevTemplate.AvvistSøknadFritekst

    @Suppress("unused")
    @JsonInclude
    val tittel: String = brevTemplate.tittel()
}
