package no.nav.su.se.bakover.service.brev.maltest

import arrow.core.nonEmptyListOf
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.VedtakVedReguleringPdfInnhold
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
import no.nav.su.se.bakover.domain.brev.jsonRequest.fromBrevCommand
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.avsluttRevurderingPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.avvistSøknadVedtakPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.forhåndsvarselPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.forhåndsvarselTilbakekrevingDødsboPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.forhåndsvarselTilbakekrevingPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.fritekstDokumentPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.innkallingKontrollsamtalePdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.kontrollnotatPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.opphørsvedtakPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.påminnelseOmNyStønadsperiodePdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.revurderingAvInntektPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.skattegrunnlagPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.soknadAlderPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.soknadUførePdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.søknadTrukketPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.vedtakAvslagPdf
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.testdata.vedtakInnvilgelsePdf
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.vurderingerMedKrav
import tilbakekreving.domain.vedtaksbrev.VedtaksbrevTilbakekrevingsbehandlingDokumentCommand
import tilbakekreving.domain.vurdering.Vurdering
import tilbakekreving.domain.vurdering.Vurderinger
import java.time.LocalDate
import java.util.UUID
import tilbakekreving.domain.vedtaksbrev.fromBrevCommand as tilbakekrevingTilPdfInnhold

/**
 * Ett realistisk eksempel per mal. Generatorene i [variasjoner] tar utgangspunkt i disse.
 * Testen `alle dokumentmaler har et eksempel` feiler hvis en ny mal mangler her.
 */
internal val malEksempler: List<Pair<String, PdfInnhold>> by lazy {
    listOf(
        "Søknad, uføre" to soknadUførePdf,
        "Søknad, alder" to soknadAlderPdf,
        "Kontrollnotat" to kontrollnotatPdf,
        "Innvilget søknad" to vedtakInnvilgelsePdf,
        "Avslått søknad" to vedtakAvslagPdf,
        "Trukket søknad" to søknadTrukketPdf,
        "Avvist søknad" to avvistSøknadVedtakPdf,
        "Forhåndsvarsel revurdering" to forhåndsvarselPdf,
        "Revurdering av inntekt" to revurderingAvInntektPdf,
        "Opphørsvedtak" to opphørsvedtakPdf,
        "Avsluttet revurdering" to avsluttRevurderingPdf,
        "Forhåndsvarsel tilbakekreving" to forhåndsvarselTilbakekrevingPdf,
        "Forhåndsvarsel tilbakekreving, dødsbo" to forhåndsvarselTilbakekrevingDødsboPdf,
        "Vedtaksbrev tilbakekreving" to vedtaksbrevTilbakekreving(dødsbo = false),
        "Vedtaksbrev tilbakekreving, dødsbo" to vedtaksbrevTilbakekreving(dødsbo = true),
        "Klage oversendt" to klageOversendt(),
        "Klage avvist" to klageAvvist(),
        "Innkalling til kontrollsamtale" to innkallingKontrollsamtalePdf,
        "Påminnelse om ny stønadsperiode" to påminnelseOmNyStønadsperiodePdf,
        "Fritekstdokument" to fritekstDokumentPdf,
        "Skattegrunnlag" to skattegrunnlagPdf,
        "Regulering av aldersfradrag" to regulering(),
    )
}

private const val SAKSBEHANDLER = "Saks Behandler"
private const val ATTESTANT = "Atte Stant"

private val personalia = PersonaliaPdfInnhold(
    dato = "01.01.2021",
    fødselsnummer = "12345678901",
    fornavn = "Tore",
    etternavn = "Strømøy",
    saksnummer = 2021L,
)

private fun klageOversendt(): PdfInnhold = KlageDokumentCommand.OpprettholdEllerDelvisOmgjøring(
    fødselsnummer = Fnr(personalia.fødselsnummer),
    saksnummer = Saksnummer(personalia.saksnummer),
    sakstype = Sakstype.ALDER,
    saksbehandler = NavIdentBruker.Saksbehandler(SAKSBEHANDLER),
    attestant = null,
    fritekst = "Saken er sendt til klageinstansen.",
    klageDato = LocalDate.of(2021, 12, 20),
    vedtaksbrevDato = LocalDate.of(2021, 12, 1),
    dødsbo = false,
).fromBrevCommand(
    personalia = personalia,
    saksbehandlerNavn = SAKSBEHANDLER,
    attestantNavn = ATTESTANT,
)

private fun klageAvvist(): PdfInnhold = KlageDokumentCommand.Avvist(
    fødselsnummer = Fnr(personalia.fødselsnummer),
    saksnummer = Saksnummer(personalia.saksnummer),
    sakstype = Sakstype.ALDER,
    saksbehandler = NavIdentBruker.Saksbehandler(SAKSBEHANDLER),
    attestant = null,
    fritekst = "Klagen er avvist.",
    dødsbo = false,
).fromBrevCommand(
    personalia = personalia,
    saksbehandlerNavn = SAKSBEHANDLER,
    attestantNavn = ATTESTANT,
)

private fun vedtaksbrevTilbakekreving(dødsbo: Boolean): PdfInnhold = VedtaksbrevTilbakekrevingsbehandlingDokumentCommand(
    fødselsnummer = Fnr(personalia.fødselsnummer),
    saksnummer = Saksnummer(personalia.saksnummer),
    correlationId = CorrelationId.generate(),
    sakId = UUID.randomUUID(),
    sakstype = Sakstype.UFØRE,
    saksbehandler = NavIdentBruker.Saksbehandler(SAKSBEHANDLER),
    attestant = NavIdentBruker.Attestant(ATTESTANT),
    fritekst = "Du må betale tilbake.",
    vurderingerMedKrav = vurderingerMedKrav(
        perioderVurderinger = nonEmptyListOf(
            Vurderinger.Periodevurdering(
                periode = januar(2021),
                vurdering = Vurdering.SkalTilbakekreve,
            ),
        ),
    ),
    skalTilbakekreve = true,
    dødsbo = dødsbo,
).tilbakekrevingTilPdfInnhold(
    personalia = personalia,
    saksbehandlerNavn = SAKSBEHANDLER,
    attestantNavn = ATTESTANT,
    clock = fixedClock,
)

private fun regulering(): PdfInnhold = VedtakVedReguleringPdfInnhold(
    sakstype = Sakstype.ALDER,
    fraOgMed = "01.05.2021",
    alderEndretFraOgMed = "april 2021",
    personalia = personalia,
    saksbehandlerNavn = SAKSBEHANDLER,
    beregningsperioder = revurderingAvInntektPdf.beregningsperioder,
    satsoversikt = revurderingAvInntektPdf.satsoversikt,
    attestantNavn = ATTESTANT,
)
