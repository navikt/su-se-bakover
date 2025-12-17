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
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuerlingProxyClientGcp
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringSoapClient
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingMqPublisher
import no.nav.su.se.bakover.client.oppgave.OppgaveHttpClient
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiClient
import no.nav.su.se.bakover.client.person.PdlClientConfig
import no.nav.su.se.bakover.client.person.PersonClient
import no.nav.su.se.bakover.client.person.PersonClientConfig
import no.nav.su.se.bakover.client.pesys.PesysHttpClient
import no.nav.su.se.bakover.client.proxy.SUProxyClientImpl
import no.nav.su.se.bakover.client.skjerming.SkjermingClient
import no.nav.su.se.bakover.common.SU_SE_BAKOVER_CONSUMER_ID
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.auth.SamlTokenProvider
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.config.isGCP
import no.nav.su.se.bakover.common.infrastructure.jms.JmsConfig
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfClient
import no.nav.su.se.bakover.dokument.infrastructure.client.distribuering.DokDistFordelingClient
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalførHttpClient
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.brev.createJournalførBrevHttpClient
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.søknad.createJournalførSøknadHttpClient
import vilkår.skatt.infrastructure.client.SkatteClient
import økonomi.domain.simulering.SimuleringClient
import java.time.Clock

data class ProdClientsBuilder(
    private val jmsConfig: JmsConfig,
    private val clock: Clock,
    private val samlTokenProvider: SamlTokenProvider,
    private val suMetrics: SuMetrics,
) : ClientsBuilder {

    fun createSimuleringClient(
        applicationConfig: ApplicationConfig,
        samlTokenProvider: SamlTokenProvider,
        azure: AzureAd,
        clock: Clock,
    ): SimuleringClient =
        if (isGCP()) {
            SimuerlingProxyClientGcp(
                config = applicationConfig.clientsConfig.suProxyConfig,
                azureAd = azure,
                clock = clock,
            )
        } else {
            SimuleringSoapClient(
                baseUrl = applicationConfig.oppdrag.simulering.url,
                samlTokenProvider = samlTokenProvider,
                clock = clock,
            )
        }
    override fun build(applicationConfig: ApplicationConfig): Clients {
        val clientsConfig = applicationConfig.clientsConfig
        val azureConfig = applicationConfig.azure
        val azureAd = AzureClient(
            thisClientId = azureConfig.clientId,
            thisClientSecret = azureConfig.clientSecret,
            wellknownUrl = azureConfig.wellKnownUrl,
        )
        val kodeverk = KodeverkHttpClient(
            baseUrl = clientsConfig.kodeverkConfig.url,
            consumerId = SU_SE_BAKOVER_CONSUMER_ID,
            kodeverkClientId = clientsConfig.kodeverkConfig.clientId,
            azureAd = azureAd,
        )
        val kontaktOgReservasjonsregisterClient = KontaktOgReservasjonsregisterClient(
            config = clientsConfig.kontaktOgReservasjonsregisterConfig,
            azure = azureAd,
        )
        val skjermingClient = SkjermingClient(skjermingUrl = clientsConfig.skjermingConfig.url, skjermingClientId = clientsConfig.skjermingConfig.clientId, azureAd = azureAd)
        val pdlClientConfig = PdlClientConfig(
            vars = clientsConfig.pdlConfig,
            azureAd = azureAd,
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
            exchange = azureAd,
        )
        val journalpostClient = QueryJournalpostHttpClient(
            safConfig = applicationConfig.clientsConfig.safConfig,
            azureAd = azureAd,
            suMetrics = suMetrics,
        )

        return Clients(
            oauth = azureAd,
            personOppslag = personOppslag,
            pdfGenerator = PdfClient(clientsConfig.pdfgenUrl),
            journalførClients = run {
                val client = JournalførHttpClient(
                    dokArkivConfig = applicationConfig.clientsConfig.dokArkivConfig,
                    azureAd = azureAd,
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
                exchange = azureAd,
                clock = clock,
            ),
            kodeverk = kodeverk,
            simuleringClient = createSimuleringClient(
                applicationConfig = applicationConfig,
                samlTokenProvider = samlTokenProvider,
                azure = azureAd,
                clock = clock,
            ),
            utbetalingPublisher = UtbetalingMqPublisher(
                mqPublisher = applicationConfig.oppdrag.let {
                    IbmMqPublisher(
                        MqPublisherConfig(
                            sendQueue = it.utbetaling.mqSendQueue,
                            replyTo = it.utbetaling.mqReplyTo,
                        ),
                        jmsContext = jmsConfig.jmsContext ?: throw IllegalArgumentException("Må ha jmscontext for prod"),
                    )
                },
            ),
            dokDistFordeling = DokDistFordelingClient(clientsConfig.dokDistConfig, azureAd = azureAd),
            avstemmingPublisher = AvstemmingMqPublisher(
                mqPublisher = IbmMqPublisher(
                    MqPublisherConfig(
                        sendQueue = applicationConfig.oppdrag.avstemming.mqSendQueue,
                    ),
                    jmsContext = jmsConfig.jmsContext ?: throw IllegalArgumentException("Må ha jmscontext for prod"),
                ),
            ),
            identClient = MicrosoftGraphApiClient(azureAd),
            kontaktOgReservasjonsregister = kontaktOgReservasjonsregisterClient,
            leaderPodLookup = LeaderPodLookupClient(applicationConfig.leaderPodLookupPath),
            kafkaPublisher = KafkaPublisherClient(applicationConfig.kafkaConfig.producerCfg),
            klageClient = klageClient,
            queryJournalpostClient = journalpostClient,
            skatteOppslag = SkatteClient(
                skatteetatenConfig = applicationConfig.clientsConfig.skatteetatenConfig,
                azureAd = azureAd,
            ),
            pesysklient = PesysHttpClient(
                azureAd = azureAd,
                url = applicationConfig.clientsConfig.pesysConfig.url,
                clientId = applicationConfig.clientsConfig.pesysConfig.clientId,
            ),
            suProxyClient = SUProxyClientImpl(applicationConfig.clientsConfig.suProxyConfig, azure = azureAd),
        )
    }
}
