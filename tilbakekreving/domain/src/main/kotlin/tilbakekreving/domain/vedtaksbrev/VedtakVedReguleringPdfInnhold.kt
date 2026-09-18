package tilbakekreving.domain.vedtaksbrev

import dokument.domain.GenererDokumentCommand
import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr

data class VedtaksbrevVedReguleringCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    override val sakstype: Sakstype,
) : GenererDokumentCommand

data class VedtakVedReguleringPdfInnhold(
    override val sakstype: Sakstype,

    override val pdfTemplate: PdfTemplateMedDokumentNavn = PdfTemplateMedDokumentNavn.VedtakVedRegulering,
) : PdfInnhold {
    companion object {
        fun fromBrevCommand(
            command: VedtaksbrevVedReguleringCommand,
        ): VedtakVedReguleringPdfInnhold {
            // TODO
            return VedtakVedReguleringPdfInnhold(
                sakstype = command.sakstype,
            )
        }
    }
}
