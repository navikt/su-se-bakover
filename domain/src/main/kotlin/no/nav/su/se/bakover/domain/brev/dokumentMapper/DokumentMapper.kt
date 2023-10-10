package no.nav.su.se.bakover.domain.brev.dokumentMapper

import dokument.domain.Dokument
import dokument.domain.GenererDokumentCommand
import dokument.domain.brev.Brevvalg
import dokument.domain.brev.PdfInnhold
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
import java.lang.IllegalStateException
import java.time.Clock
import java.util.UUID

fun PdfA.tilDokument(
    pdfInnhold: PdfInnhold,
    command: GenererDokumentCommand,
    clock: Clock,
): Dokument.UtenMetadata {
    return when (command) {
        is IverksettRevurderingDokumentCommand,
        is IverksettSøknadsbehandlingDokumentCommand,
        is KlageDokumentCommand.Avvist,
        -> vedtak(clock, pdfInnhold)

        is ForhåndsvarselDokumentCommand,
        is ForhåndsvarselTilbakekrevingDokumentCommand,
        is InnkallingTilKontrollsamtaleDokumentCommand,
        is PåminnelseNyStønadsperiodeDokumentCommand,
        -> informasjonViktig(clock, pdfInnhold)

        // På sikt vil vi kanskje la saksbehandler velge viktighetsgraden (viktig/annet) ved sending av fritekstbrev.
        is FritekstDokumentCommand,
        is KlageDokumentCommand.Oppretthold,
        is AvsluttRevurderingDokumentCommand,
        is TrukketSøknadDokumentCommand,
        -> informasjonAnnet(clock, pdfInnhold)

        is AvvistSøknadDokumentCommand -> when (command.brevvalg) {
            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst -> informasjonAnnet(
                clock,
                pdfInnhold,
            )

            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.UtenFritekst -> vedtak(
                clock,
                pdfInnhold,
            )

            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst -> vedtak(
                clock,
                pdfInnhold,
            )
        }

        else -> throw IllegalStateException("Ukjent GenererDokumentCommand for sak ${command.saksnummer}. ")
    }
}

private fun PdfA.vedtak(
    clock: Clock,
    pdfInnhold: PdfInnhold,
) = Dokument.UtenMetadata.Vedtak(
    id = UUID.randomUUID(),
    opprettet = Tidspunkt.now(clock),
    tittel = pdfInnhold.pdfTemplate.tittel(),
    generertDokument = this,
    generertDokumentJson = pdfInnhold.toJson(),
)

private fun PdfA.informasjonViktig(
    clock: Clock,
    pdfInnhold: PdfInnhold,
) = Dokument.UtenMetadata.Informasjon.Viktig(
    id = UUID.randomUUID(),
    opprettet = Tidspunkt.now(clock),
    tittel = pdfInnhold.pdfTemplate.tittel(),
    generertDokument = this,
    generertDokumentJson = pdfInnhold.toJson(),
)

private fun PdfA.informasjonAnnet(
    clock: Clock,
    pdfInnhold: PdfInnhold,
) = Dokument.UtenMetadata.Informasjon.Annet(
    id = UUID.randomUUID(),
    opprettet = Tidspunkt.now(clock),
    tittel = pdfInnhold.pdfTemplate.tittel(),
    generertDokument = this,
    generertDokumentJson = pdfInnhold.toJson(),
)
