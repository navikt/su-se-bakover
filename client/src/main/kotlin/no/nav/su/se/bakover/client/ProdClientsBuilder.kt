package no.nav.su.se.bakover.client

import no.nav.su.se.bakover.client.azure.AzureClient
import no.nav.su.se.bakover.client.dkif.DkifClient
import no.nav.su.se.bakover.client.dokarkiv.DokArkivClient
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordelingClient
import no.nav.su.se.bakover.client.kodeverk.KodeverkHttpClient
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
import no.nav.su.se.bakover.client.sts.StsClient
import no.nav.su.se.bakover.common.Config
import javax.jms.JMSContext

data class ProdClientsBuilder(internal val jmsContext: JMSContext) : ClientsBuilder {

    override fun build(): Clients {
        val consumerId = "srvsupstonad"

        val oAuth = AzureClient(Config.azureClientId, Config.azureClientSecret, Config.azureWellKnownUrl)
        val kodeverk = KodeverkHttpClient(Config.kodeverkUrl, consumerId)
        val tokenOppslag = StsClient(Config.stsUrl, Config.serviceUser.username, Config.serviceUser.password)
        val dkif = DkifClient(Config.dkifUrl, tokenOppslag, consumerId)
        val personOppslag = PersonClient(Config.pdlUrl, kodeverk, SkjermingClient(Config.skjermingUrl), dkif, tokenOppslag)

        return Clients(
            oauth = oAuth,
            personOppslag = personOppslag,
            tokenOppslag = tokenOppslag,
            pdfGenerator = PdfClient(Config.pdfgenUrl),
            dokArkiv = DokArkivClient(Config.dokarkivUrl, tokenOppslag),
            oppgaveClient = OppgaveHttpClient(Config.oppgaveUrl, tokenOppslag),
            kodeverk = kodeverk,
            simuleringClient = SimuleringSoapClient(
                SimuleringConfig(
                    simuleringServiceUrl = Config.Simulering().url,
                    stsSoapUrl = Config.Simulering().stsSoapUrl,
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
                mqPublisher = Config.Oppdrag(serviceUser = Config.serviceUser).let {
                    IbmMqPublisher(
                        MqPublisherConfig(
                            sendQueue = it.avstemming.mqSendQueue
                        ),
                        jmsContext = jmsContext
                    )
                }
            ),
            microsoftGraphApiClient = MicrosoftGraphApiClient(oAuth),
            digitalKontaktinformasjon = dkif,
        )
    }
}
