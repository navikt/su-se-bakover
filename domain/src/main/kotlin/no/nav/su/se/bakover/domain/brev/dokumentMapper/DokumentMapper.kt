package no.nav.su.se.bakover.domain.brev.dokumentMapper

import dokument.domain.Distribusjonstype
import dokument.domain.Dokument
import dokument.domain.DokumentFormaal
import dokument.domain.GenererDokumentCommand
import dokument.domain.brev.Brevvalg
import dokument.domain.pdf.PdfInnhold
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.brev.command.AvsluttRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.AvvistSøknadDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.ForhåndsvarselDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.FritekstDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.InnkallingTilKontrollsamtaleDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.PåminnelseNyStønadsperiodeDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.TrukketSøknadDokumentCommand
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand
import tilbakekreving.domain.vedtaksbrev.VedtaksbrevTilbakekrevingsbehandlingDokumentCommand
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
            dokumentFormaal = DokumentFormaal.VEDTAK,
        )

        is ForhåndsvarselDokumentCommand,
        is ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand,
        is InnkallingTilKontrollsamtaleDokumentCommand,
        is PåminnelseNyStønadsperiodeDokumentCommand,
        -> informasjonViktig(
            id = id,
            clock = clock,
            pdfInnhold = pdfInnhold,
            dokumentFormaal = when (command) {
                is ForhåndsvarselDokumentCommand,
                is ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand,
                -> DokumentFormaal.FORHANDSVARSEL

                else -> DokumentFormaal.ANNET
            },
        )

        is KlageDokumentCommand.OpprettholdEllerDelvisOmgjøring,
        is AvsluttRevurderingDokumentCommand,
        is TrukketSøknadDokumentCommand,
        -> informasjonAnnet(
            id = id,
            clock = clock,
            pdfInnhold = pdfInnhold,
            dokumentFormaal = DokumentFormaal.ANNET,
        )

        is FritekstDokumentCommand -> {
            when (command.distribusjonstype) {
                Distribusjonstype.VEDTAK -> vedtak(
                    id = id,
                    clock = clock,
                    pdfInnhold = pdfInnhold,
                    dokumentFormaal = DokumentFormaal.VEDTAK,
                )

                Distribusjonstype.VIKTIG -> informasjonViktig(
                    id = id,
                    clock = clock,
                    pdfInnhold = pdfInnhold,
                    dokumentFormaal = DokumentFormaal.ANNET,
                )

                Distribusjonstype.ANNET -> informasjonAnnet(
                    id = id,
                    clock = clock,
                    pdfInnhold = pdfInnhold,
                    dokumentFormaal = DokumentFormaal.ANNET,
                )
            }
        }

        is AvvistSøknadDokumentCommand -> when (command.brevvalg) {
            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst -> informasjonAnnet(
                id = id,
                clock = clock,
                pdfInnhold = pdfInnhold,
                dokumentFormaal = DokumentFormaal.ANNET,
            )

            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.UtenFritekst -> vedtak(
                id = id,
                clock = clock,
                pdfInnhold = pdfInnhold,
                dokumentFormaal = DokumentFormaal.VEDTAK,
            )

            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst -> vedtak(
                id = id,
                clock = clock,
                pdfInnhold = pdfInnhold,
                dokumentFormaal = DokumentFormaal.VEDTAK,
            )
        }

        else -> throw IllegalStateException("Ukjent GenererDokumentCommand for sak ${command.saksnummer}. ")
    }
}

private fun PdfA.vedtak(
    id: UUID = UUID.randomUUID(),
    clock: Clock,
    pdfInnhold: PdfInnhold,
    dokumentFormaal: DokumentFormaal?,
) = Dokument.UtenMetadata.Vedtak(
    id = id,
    opprettet = Tidspunkt.now(clock),
    tittel = pdfInnhold.pdfTemplate.tittel(),
    generertDokument = this,
    generertDokumentJson = pdfInnhold.toJson(),
    dokumentFormaal = dokumentFormaal,
)

private fun PdfA.informasjonViktig(
    id: UUID = UUID.randomUUID(),
    clock: Clock,
    pdfInnhold: PdfInnhold,
    dokumentFormaal: DokumentFormaal?,
) = Dokument.UtenMetadata.Informasjon.Viktig(
    id = id,
    opprettet = Tidspunkt.now(clock),
    tittel = pdfInnhold.pdfTemplate.tittel(),
    generertDokument = this,
    generertDokumentJson = pdfInnhold.toJson(),
    dokumentFormaal = dokumentFormaal,
)

private fun PdfA.informasjonAnnet(
    id: UUID = UUID.randomUUID(),
    clock: Clock,
    pdfInnhold: PdfInnhold,
    dokumentFormaal: DokumentFormaal?,
) = Dokument.UtenMetadata.Informasjon.Annet(
    id = id,
    opprettet = Tidspunkt.now(clock),
    tittel = pdfInnhold.pdfTemplate.tittel(),
    generertDokument = this,
    generertDokumentJson = pdfInnhold.toJson(),
    dokumentFormaal = dokumentFormaal,
)
