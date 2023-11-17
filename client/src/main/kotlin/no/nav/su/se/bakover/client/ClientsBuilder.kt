package no.nav.su.se.bakover.client

import dokument.domain.distribuering.DokDistFordeling
import dokument.domain.journalføring.brev.JournalførBrevClient
import dokument.domain.journalføring.søknad.JournalførSøknadClient
import no.nav.su.se.bakover.client.kodeverk.Kodeverk
import no.nav.su.se.bakover.client.krr.KontaktOgReservasjonsregister
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.auth.TokenOppslag
import no.nav.su.se.bakover.common.domain.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.nais.LeaderPodLookup
import no.nav.su.se.bakover.domain.journalpost.QueryJournalpostClient
import no.nav.su.se.bakover.domain.klage.KlageClient
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.TilbakekrevingClient
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.skatt.JournalførSkattedokumentPåSakClient
import no.nav.su.se.bakover.domain.skatt.JournalførSkattedokumentUtenforSakClient
import no.nav.su.se.bakover.domain.skatt.Skatteoppslag
import person.domain.IdentClient
import person.domain.PersonOppslag
import økonomi.domain.simulering.SimuleringClient

interface ClientsBuilder {
    fun build(applicationConfig: ApplicationConfig): Clients
}

data class Clients(
    val oauth: AzureAd,
    val personOppslag: PersonOppslag,
    val tokenOppslag: TokenOppslag,
    val pdfGenerator: PdfGenerator,
    val journalførClients: JournalførClients,
    val oppgaveClient: OppgaveClient,
    val kodeverk: Kodeverk,
    val simuleringClient: SimuleringClient,
    val utbetalingPublisher: UtbetalingPublisher,
    val dokDistFordeling: DokDistFordeling,
    val avstemmingPublisher: AvstemmingPublisher,
    val identClient: IdentClient,
    val kontaktOgReservasjonsregister: KontaktOgReservasjonsregister,
    val leaderPodLookup: LeaderPodLookup,
    val kafkaPublisher: KafkaPublisher,
    val klageClient: KlageClient,
    val queryJournalpostClient: QueryJournalpostClient,
    val tilbakekrevingClient: TilbakekrevingClient,
    val skatteOppslag: Skatteoppslag,
)

/**
 * Samler klientene som journalfører dokumenter/brev (skriv operasjoner).
 * Se egen type for les-operasjon: [QueryJournalpostClient]
 */
data class JournalførClients(
    val skattedokumentUtenforSak: JournalførSkattedokumentUtenforSakClient,
    val skattedokumentPåSak: JournalførSkattedokumentPåSakClient,
    val brev: JournalførBrevClient,
    val søknad: JournalførSøknadClient,
)
