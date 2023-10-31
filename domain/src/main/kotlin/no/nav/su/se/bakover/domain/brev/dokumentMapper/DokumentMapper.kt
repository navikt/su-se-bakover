package no.nav.su.se.bakover.domain.brev.dokumentMapper

import dokument.domain.Dokument
import dokument.domain.GenererDokumentCommand
import dokument.domain.brev.Brevvalg
import dokument.domain.pdf.PdfInnhold
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.brev.command.AvsluttRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.AvvistSøknadDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.ForhåndsvarselDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.ForhåndsvarselTilbakekrevingDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.FritekstDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.InnkallingTilKontrollsamtaleDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.PåminnelseNyStønadsperiodeDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.TrukketSøknadDokumentCommand
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand
import tilbakekreving.domain.forhåndsvarsel.VedtaksbrevTilbakekrevingsbehandlingDokumentCommand
import java.time.Clock
import java.util.UUID

fun PdfA.tilDokument(
    id: UUID = UUID.randomUUID(),
    pdfInnhold: PdfInnhold,
    command: GenererDokumentCommand,
    clock: Clock,
): Dokument.UtenMetadata {
    return when (command) {
        is IverksettRevurderingDokumentCommand,
        is IverksettSøknadsbehandlingDokumentCommand,
        is VedtaksbrevTilbakekrevingsbehandlingDokumentCommand,
        is KlageDokumentCommand.Avvist,
        -> vedtak(
            id = id,
            clock = clock,
            pdfInnhold = pdfInnhold,
        )

        is ForhåndsvarselDokumentCommand,
        is ForhåndsvarselTilbakekrevingDokumentCommand,
        is ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand,
        is InnkallingTilKontrollsamtaleDokumentCommand,
        is PåminnelseNyStønadsperiodeDokumentCommand,
        -> informasjonViktig(
            id = id,
            clock = clock,
            pdfInnhold = pdfInnhold,
        )

        // På sikt vil vi kanskje la saksbehandler velge viktighetsgraden (viktig/annet) ved sending av fritekstbrev.
        is FritekstDokumentCommand,
        is KlageDokumentCommand.Oppretthold,
        is AvsluttRevurderingDokumentCommand,
        is TrukketSøknadDokumentCommand,
        -> informasjonAnnet(
            id = id,
            clock = clock,
            pdfInnhold = pdfInnhold,
        )

        is AvvistSøknadDokumentCommand -> when (command.brevvalg) {
            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst -> informasjonAnnet(
                id = id,
                clock = clock,
                pdfInnhold = pdfInnhold,
            )

            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.UtenFritekst -> vedtak(
                id = id,
                clock = clock,
                pdfInnhold = pdfInnhold,
            )

            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst -> vedtak(
                id = id,
                clock = clock,
                pdfInnhold = pdfInnhold,
            )
        }

        else -> throw IllegalStateException("Ukjent GenererDokumentCommand for sak ${command.saksnummer}. ")
    }
}

private fun PdfA.vedtak(
    id: UUID = UUID.randomUUID(),
    clock: Clock,
    pdfInnhold: PdfInnhold,
) = Dokument.UtenMetadata.Vedtak(
    id = id,
    opprettet = Tidspunkt.now(clock),
    tittel = pdfInnhold.pdfTemplate.tittel(),
    generertDokument = this,
    generertDokumentJson = pdfInnhold.toJson(),
)

private fun PdfA.informasjonViktig(
    id: UUID = UUID.randomUUID(),
    clock: Clock,
    pdfInnhold: PdfInnhold,
) = Dokument.UtenMetadata.Informasjon.Viktig(
    id = id,
    opprettet = Tidspunkt.now(clock),
    tittel = pdfInnhold.pdfTemplate.tittel(),
    generertDokument = this,
    generertDokumentJson = pdfInnhold.toJson(),
)

private fun PdfA.informasjonAnnet(
    id: UUID = UUID.randomUUID(),
    clock: Clock,
    pdfInnhold: PdfInnhold,
) = Dokument.UtenMetadata.Informasjon.Annet(
    id = id,
    opprettet = Tidspunkt.now(clock),
    tittel = pdfInnhold.pdfTemplate.tittel(),
    generertDokument = this,
    generertDokumentJson = pdfInnhold.toJson(),
)
