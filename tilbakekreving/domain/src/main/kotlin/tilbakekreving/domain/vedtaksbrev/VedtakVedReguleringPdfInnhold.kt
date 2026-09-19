package tilbakekreving.domain.vedtaksbrev

import dokument.domain.GenererDokumentCommand
import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr

data class VedtaksbrevVedReguleringCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    override val sakstype: Sakstype,
    val saksbehandler: NavIdentBruker,
) : GenererDokumentCommand

data class VedtakVedReguleringPdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    override val pdfTemplate: PdfTemplateMedDokumentNavn = PdfTemplateMedDokumentNavn.VedtakVedRegulering,
) : PdfInnhold {
    companion object {
        fun fromBrevCommand(
            command: VedtaksbrevVedReguleringCommand,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
        ): VedtakVedReguleringPdfInnhold {
            // TODO
            return VedtakVedReguleringPdfInnhold(
                sakstype = command.sakstype,
                personalia = personalia,
                saksbehandlerNavn = saksbehandlerNavn,
            )
        }
    }
}
