package no.nav.su.se.bakover.domain.brev.søknad.lukk.avvist

import com.fasterxml.jackson.annotation.JsonInclude
import dokument.domain.brev.Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst
import dokument.domain.brev.Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev
import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.brev.command.AvvistSøknadDokumentCommand

data class AvvistSøknadVedtakPdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val fritekst: String?,
) : PdfInnhold {
    override val pdfTemplate = PdfTemplateMedDokumentNavn.AvvistSøknadVedtak
}

data class AvvistSøknadFritekstPdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val fritekst: String,
) : PdfInnhold {

    override val pdfTemplate = PdfTemplateMedDokumentNavn.AvvistSøknadFritekst

    @Suppress("unused")
    @JsonInclude
    val tittel: String = pdfTemplate.tittel()
}

fun AvvistSøknadDokumentCommand.tilAvvistSøknadPdfInnhold(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
): PdfInnhold {
    return when (brevvalg) {
        is InformasjonsbrevMedFritekst -> AvvistSøknadFritekstPdfInnhold(
            sakstype = sakstype,
            personalia = personalia,
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = this.brevvalg.fritekst,
        )

        is Vedtaksbrev -> AvvistSøknadVedtakPdfInnhold(
            sakstype = sakstype,
            personalia = personalia,
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = this.brevvalg.fritekst,
        )
    }
}
