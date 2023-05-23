package no.nav.su.se.bakover.domain.brev.søknad.lukk

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.domain.brev.PdfInnhold
import no.nav.su.se.bakover.domain.brev.PdfTemplate

data class AvvistSøknadVedtakPdfInnhold(
    val personalia: Personalia,
    val saksbehandlerNavn: String,
    val fritekst: String?,
) : PdfInnhold() {

    override val pdfTemplate = PdfTemplate.AvvistSøknadVedtak
}

data class AvvistSøknadFritekstPdfInnhold(
    val personalia: Personalia,
    val saksbehandlerNavn: String,
    val fritekst: String,
) : PdfInnhold() {

    override val pdfTemplate = PdfTemplate.AvvistSøknadFritekst

    @Suppress("unused")
    @JsonInclude
    val tittel: String = pdfTemplate.tittel()
}
