package no.nav.su.se.bakover.domain.brev.jsonRequest

import beregning.domain.Beregning
import dokument.domain.GenererDokumentCommand
import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import java.time.LocalDate

data class VedtaksbrevVedReguleringCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    override val sakstype: Sakstype,
    val fraOgMed: LocalDate,
    val saksbehandler: NavIdentBruker,
    val beregning: Beregning,
    val satsoversikt: Satsoversikt,
) : GenererDokumentCommand

data class VedtakVedReguleringPdfInnhold(
    override val sakstype: Sakstype,
    val fraOgMed: LocalDate,
    val FraOgMedMåned: String,
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val beregningsperioder: List<Beregningsperiode>,
    val satsoversikt: Satsoversikt,
    override val pdfTemplate: PdfTemplateMedDokumentNavn = PdfTemplateMedDokumentNavn.VedtakVedRegulering,
) : PdfInnhold {
    companion object {
        fun fromBrevCommand(
            command: VedtaksbrevVedReguleringCommand,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
        ): VedtakVedReguleringPdfInnhold {
            return VedtakVedReguleringPdfInnhold(
                sakstype = command.sakstype,
                fraOgMed = command.fraOgMed,
                FraOgMedMåned = command.fraOgMed.formatMonthYear(),
                personalia = personalia,
                saksbehandlerNavn = saksbehandlerNavn,
                beregningsperioder = LagBrevinnholdForBeregning(command.beregning).brevInnhold,
                satsoversikt = command.satsoversikt,
            )
        }
    }
}
