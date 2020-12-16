package no.nav.su.se.bakover.client

import no.nav.su.se.bakover.client.azure.AzureClient
import no.nav.su.se.bakover.client.dkif.DkifClient
import no.nav.su.se.bakover.client.dokarkiv.DokArkivClient
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordelingClient
import no.nav.su.se.bakover.client.kodeverk.KodeverkHttpClient
import no.nav.su.se.bakover.client.nais.LeaderPodLookupClient
import no.nav.su.se.bakover.client.oppdrag.IbmMqPublisher
import no.nav.su.se.bakover.client.oppdrag.MqPublisher.MqPublisherConfig
import no.nav.su.se.bakover.client.oppdrag.avstemming.AvstemmingMqPublisher
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringConfig
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringSoapClient
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingMqPublisher
import no.nav.su.se.bakover.client.oppgave.OppgaveHttpClient
import no.nav.su.se.bakover.client.pdf.PdfClient
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiClient
import no.nav.su.se.bakover.client.person.PersonClient
import no.nav.su.se.bakover.client.skjerming.SkjermingClient
import no.nav.su.se.bakover.client.statistikk.KafkaStatistikkProducer
import no.nav.su.se.bakover.client.sts.StsClient
import no.nav.su.se.bakover.common.Config
import java.time.Clock
import javax.jms.JMSContext

data class ProdClientsBuilder(internal val jmsContext: JMSContext) : ClientsBuilder {

    override fun build(): Clients {
        val consumerId = "srvsupstonad"

        val oAuth = AzureClient(Config.azureClientId, Config.azureClientSecret, Config.azureWellKnownUrl)
        val kodeverk = KodeverkHttpClient(Config.kodeverkUrl, consumerId)
        val tokenOppslag = StsClient(Config.stsUrl, Config.serviceUser.username, Config.serviceUser.password)
        val dkif = DkifClient(Config.dkifUrl, tokenOppslag, consumerId)
        val personOppslag =
            PersonClient(Config.pdlUrl, kodeverk, SkjermingClient(Config.skjermingUrl), dkif, tokenOppslag)

        return Clients(
            oauth = oAuth,
            personOppslag = personOppslag,
            tokenOppslag = tokenOppslag,
            pdfGenerator = PdfClient(Config.pdfgenUrl),
            dokArkiv = DokArkivClient(Config.dokarkivUrl, tokenOppslag),
            oppgaveClient = OppgaveHttpClient(
                baseUrl = Config.oppgaveUrl,
                exchange = oAuth,
                oppgaveClientId = Config.oppgaveClientId,
                clock = Clock.systemUTC(),
            ),
            kodeverk = kodeverk,
            simuleringClient = SimuleringSoapClient(
                SimuleringConfig(
                    simuleringServiceUrl = Config.oppdrag.simulering.url,
                    stsSoapUrl = Config.oppdrag.simulering.stsSoapUrl,
                    disableCNCheck = true,
                    serviceUser = Config.serviceUser
                ).wrapWithSTSSimulerFpService()
            ),
            utbetalingPublisher = UtbetalingMqPublisher(
                mqPublisher = Config.Oppdrag(serviceUser = Config.serviceUser).let {
                    IbmMqPublisher(
                        MqPublisherConfig(
                            sendQueue = it.utbetaling.mqSendQueue,
                            replyTo = it.utbetaling.mqReplyTo
                        ),
                        jmsContext = jmsContext
                    )
                }
            ),
            dokDistFordeling = DokDistFordelingClient(Config.dokDistUrl, tokenOppslag),
            avstemmingPublisher = AvstemmingMqPublisher(
                mqPublisher = IbmMqPublisher(
                    MqPublisherConfig(
                        sendQueue = Config.Oppdrag(serviceUser = Config.serviceUser).avstemming.mqSendQueue
                    ),
                    jmsContext = jmsContext
                )
            ),
            microsoftGraphApiClient = MicrosoftGraphApiClient(oAuth),
            digitalKontaktinformasjon = dkif,
            leaderPodLookup = LeaderPodLookupClient(Config.leaderPodLookupPath),
            statistikkProducer = KafkaStatistikkProducer()
        )
    }
}
