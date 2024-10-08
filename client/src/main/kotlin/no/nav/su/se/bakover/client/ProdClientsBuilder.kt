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
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringSoapClient
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingMqPublisher
import no.nav.su.se.bakover.client.oppgave.OppgaveHttpClient
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiClient
import no.nav.su.se.bakover.client.person.PdlClientConfig
import no.nav.su.se.bakover.client.person.PersonClient
import no.nav.su.se.bakover.client.person.PersonClientConfig
import no.nav.su.se.bakover.client.skjerming.SkjermingClient
import no.nav.su.se.bakover.client.sts.StsClient
import no.nav.su.se.bakover.common.SU_SE_BAKOVER_CONSUMER_ID
import no.nav.su.se.bakover.common.domain.auth.SamlTokenProvider
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.jms.JmsConfig
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfClient
import no.nav.su.se.bakover.dokument.infrastructure.client.distribuering.DokDistFordelingClient
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalførHttpClient
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.brev.createJournalførBrevHttpClient
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.søknad.createJournalførSøknadHttpClient
import vilkår.skatt.infrastructure.client.SkatteClient
import java.time.Clock

data class ProdClientsBuilder(
    private val jmsConfig: JmsConfig,
    private val clock: Clock,
    private val samlTokenProvider: SamlTokenProvider,
    private val suMetrics: SuMetrics,
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
            baseUrl = clientsConfig.kodeverkConfig.url,
            consumerId = SU_SE_BAKOVER_CONSUMER_ID,
            kodeverkClientId = clientsConfig.kodeverkConfig.clientId,
            azureAd = oAuth,
        )
        val tokenOppslag = StsClient(
            baseUrl = clientsConfig.stsUrl,
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
            suMetrics = suMetrics,
        )
        val klageClient = KabalHttpClient(
            kabalConfig = applicationConfig.clientsConfig.kabalConfig,
            exchange = oAuth,
        )
        val journalpostClient = QueryJournalpostHttpClient(
            safConfig = applicationConfig.clientsConfig.safConfig,
            azureAd = oAuth,
            suMetrics = suMetrics,
        )

        return Clients(
            oauth = oAuth,
            personOppslag = personOppslag,
            tokenOppslag = tokenOppslag,
            pdfGenerator = PdfClient(clientsConfig.pdfgenUrl),
            journalførClients = run {
                val client = JournalførHttpClient(
                    dokArkivConfig = applicationConfig.clientsConfig.dokArkivConfig,
                    azureAd = oAuth,
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
                clock = clock,
            ),
            kodeverk = kodeverk,
            simuleringClient = SimuleringSoapClient(
                baseUrl = applicationConfig.oppdrag.simulering.url,
                samlTokenProvider = samlTokenProvider,
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
            dokDistFordeling = DokDistFordelingClient(clientsConfig.dokDistConfig, azureAd = oAuth),
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
            skatteOppslag = SkatteClient(
                skatteetatenConfig = applicationConfig.clientsConfig.skatteetatenConfig,
                azureAd = oAuth,
            ),
        )
    }
}
