package no.nav.su.se.bakover.client

import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.dkif.DigitalKontaktinformasjon
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.client.kodeverk.Kodeverk
import no.nav.su.se.bakover.client.maskinporten.MaskinportenClient
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.skatteetaten.Skatteoppslag
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.klage.KlageClient
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingClient
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.person.PersonOppslag

interface ClientsBuilder {
    fun build(applicationConfig: ApplicationConfig): Clients
}

data class Clients(
    val oauth: OAuth,
    val personOppslag: PersonOppslag,
    val tokenOppslag: TokenOppslag,
    val pdfGenerator: PdfGenerator,
    val dokArkiv: DokArkiv,
    val oppgaveClient: OppgaveClient,
    val kodeverk: Kodeverk,
    val simuleringClient: SimuleringClient,
    val utbetalingPublisher: UtbetalingPublisher,
    val dokDistFordeling: DokDistFordeling,
    val avstemmingPublisher: AvstemmingPublisher,
    val identClient: IdentClient,
    val digitalKontaktinformasjon: DigitalKontaktinformasjon,
    val leaderPodLookup: LeaderPodLookup,
    val kafkaPublisher: KafkaPublisher,
    val klageClient: KlageClient,
    val journalpostClient: JournalpostClient,
    val tilbakekrevingClient: TilbakekrevingClient,
    val skatteOppslag: Skatteoppslag,
    val maskinportenClient: MaskinportenClient,
)
