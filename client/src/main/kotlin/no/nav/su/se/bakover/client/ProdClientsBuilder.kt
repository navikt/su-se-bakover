package no.nav.su.se.bakover.client

import no.nav.su.se.bakover.client.azure.AzureClient
import no.nav.su.se.bakover.client.journalfør.skatt.påsak.JournalførSkattedokumentPåSakHttpClient
import no.nav.su.se.bakover.client.journalfør.skatt.utenforsak.JournalførSkattedokumentUtenforSakHttpClient
import no.nav.su.se.bakover.client.journalpost.QueryJournalpostHttpClient
import no.nav.su.se.bakover.client.kabal.KabalHttpClient
import no.nav.su.se.bakover.client.kafka.KafkaPublisherClient
import no.nav.su.se.bakover.client.kodeverk.KodeverkHttpClient
import no.nav.su.se.bakover.client.krr.KontaktOgReservasjonsregisterClient
import no.nav.su.se.bakover.client.nais.LeaderPodLookupClient
import no.nav.su.se.bakover.client.oppdrag.IbmMqPublisher
import no.nav.su.se.bakover.client.oppdrag.MqPublisher.MqPublisherConfig
import no.nav.su.se.bakover.client.oppdrag.avstemming.AvstemmingMqPublisher
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringConfig
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringSoapClient
import no.nav.su.se.bakover.client.oppdrag.tilbakekrevingUnderRevurdering.TilbakekrevingUnderRevurderingSoapClient
import no.nav.su.se.bakover.client.oppdrag.tilbakekrevingUnderRevurdering.TilbakekrevingUnderRevurderingSoapClientConfig
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingMqPublisher
import no.nav.su.se.bakover.client.oppgave.OppgaveHttpClient
import no.nav.su.se.bakover.client.pdf.PdfClient
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiClient
import no.nav.su.se.bakover.client.person.PdlClientConfig
import no.nav.su.se.bakover.client.person.PersonClient
import no.nav.su.se.bakover.client.person.PersonClientConfig
import no.nav.su.se.bakover.client.skatteetaten.SkatteClient
import no.nav.su.se.bakover.client.skjerming.SkjermingClient
import no.nav.su.se.bakover.client.sts.StsClient
import no.nav.su.se.bakover.common.SU_SE_BAKOVER_CONSUMER_ID
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.jms.JmsConfig
import no.nav.su.se.bakover.dokument.infrastructure.database.distribuering.DokDistFordelingClient
import no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.brev.createJournalførBrevHttpClient
import no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.søknad.createJournalførSøknadHttpClient
import no.nav.su.se.bakover.domain.metrics.ClientMetrics
import java.time.Clock

data class ProdClientsBuilder(
    private val jmsConfig: JmsConfig,
    private val clock: Clock,
    private val metrics: ClientMetrics,
) : ClientsBuilder {

    override fun build(applicationConfig: ApplicationConfig): Clients {
        val clientsConfig = applicationConfig.clientsConfig
        val azureConfig = applicationConfig.azure
        val oAuth = AzureClient(
            thisClientId = azureConfig.clientId,
            thisClientSecret = azureConfig.clientSecret,
            wellknownUrl = azureConfig.wellKnownUrl,
        )
        val kodeverk = KodeverkHttpClient(
            baseUrl = clientsConfig.kodeverkUrl,
            consumerId = SU_SE_BAKOVER_CONSUMER_ID,
        )
        val serviceUser = applicationConfig.serviceUser
        val tokenOppslag = StsClient(
            baseUrl = clientsConfig.stsUrl,
            username = serviceUser.username,
            password = serviceUser.password,
            clock = clock,
        )
        val kontaktOgReservasjonsregisterClient = KontaktOgReservasjonsregisterClient(
            config = clientsConfig.kontaktOgReservasjonsregisterConfig,
            azure = oAuth,
        )
        val skjermingClient = SkjermingClient(clientsConfig.skjermingUrl)
        val pdlClientConfig = PdlClientConfig(
            vars = clientsConfig.pdlConfig,
            azureAd = oAuth,
        )
        val personOppslag = PersonClient(
            PersonClientConfig(
                kodeverk = kodeverk,
                skjerming = skjermingClient,
                kontaktOgReservasjonsregister = kontaktOgReservasjonsregisterClient,
                pdlClientConfig = pdlClientConfig,
            ),
        )
        val klageClient = KabalHttpClient(
            kabalConfig = applicationConfig.clientsConfig.kabalConfig,
            exchange = oAuth,
        )
        val journalpostClient = QueryJournalpostHttpClient(
            safConfig = applicationConfig.clientsConfig.safConfig,
            azureAd = oAuth,
            sts = tokenOppslag,
            metrics = metrics.journalpostClientMetrics,
        )

        return Clients(
            oauth = oAuth,
            personOppslag = personOppslag,
            tokenOppslag = tokenOppslag,
            pdfGenerator = PdfClient(clientsConfig.pdfgenUrl),
            journalførClients = run {
                val client = no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.JournalførHttpClient(
                    baseUrl = clientsConfig.dokarkivUrl,
                    tokenOppslag = tokenOppslag,
                )
                JournalførClients(
                    skattedokumentUtenforSak = JournalførSkattedokumentUtenforSakHttpClient(
                        client,
                    ),
                    skattedokumentPåSak = JournalførSkattedokumentPåSakHttpClient(
                        client,
                    ),
                    brev = createJournalførBrevHttpClient(client),
                    søknad = createJournalførSøknadHttpClient(client),
                )
            },
            oppgaveClient = OppgaveHttpClient(
                connectionConfig = applicationConfig.clientsConfig.oppgaveConfig,
                exchange = oAuth,
                tokenoppslagForSystembruker = tokenOppslag,
                clock = clock,
            ),
            kodeverk = kodeverk,
            simuleringClient = SimuleringSoapClient(
                SimuleringConfig(
                    simuleringServiceUrl = applicationConfig.oppdrag.simulering.url,
                    stsSoapUrl = applicationConfig.oppdrag.simulering.stsSoapUrl,
                    disableCNCheck = true,
                    serviceUser = serviceUser,
                ).wrapWithSTSSimulerFpService(),
                clock = clock,
            ),
            utbetalingPublisher = UtbetalingMqPublisher(
                mqPublisher = applicationConfig.oppdrag.let {
                    IbmMqPublisher(
                        MqPublisherConfig(
                            sendQueue = it.utbetaling.mqSendQueue,
                            replyTo = it.utbetaling.mqReplyTo,
                        ),
                        jmsContext = jmsConfig.jmsContext,
                    )
                },
            ),
            dokDistFordeling = DokDistFordelingClient(clientsConfig.dokDistUrl, tokenOppslag),
            avstemmingPublisher = AvstemmingMqPublisher(
                mqPublisher = IbmMqPublisher(
                    MqPublisherConfig(
                        sendQueue = applicationConfig.oppdrag.avstemming.mqSendQueue,
                    ),
                    jmsContext = jmsConfig.jmsContext,
                ),
            ),
            identClient = MicrosoftGraphApiClient(oAuth),
            kontaktOgReservasjonsregister = kontaktOgReservasjonsregisterClient,
            leaderPodLookup = LeaderPodLookupClient(applicationConfig.leaderPodLookupPath),
            kafkaPublisher = KafkaPublisherClient(applicationConfig.kafkaConfig.producerCfg),
            klageClient = klageClient,
            queryJournalpostClient = journalpostClient,
            tilbakekrevingClient = TilbakekrevingUnderRevurderingSoapClient(
                tilbakekrevingPortType = TilbakekrevingUnderRevurderingSoapClientConfig(
                    tilbakekrevingServiceUrl = applicationConfig.oppdrag.tilbakekreving.soap.url,
                    stsSoapUrl = applicationConfig.oppdrag.simulering.stsSoapUrl,
                    disableCNCheck = true,
                    serviceUser = serviceUser,
                ).tilbakekrevingSoapService(),
                clock = clock,
            ),
            skatteOppslag = SkatteClient(
                skatteetatenConfig = applicationConfig.clientsConfig.skatteetatenConfig,
                azureAd = oAuth,
            ),
        )
    }
}
