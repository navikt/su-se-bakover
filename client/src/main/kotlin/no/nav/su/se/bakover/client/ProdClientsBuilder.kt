package no.nav.su.se.bakover.client

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.wmq.WMQConstants
import no.nav.su.se.bakover.client.azure.AzureClient
import no.nav.su.se.bakover.client.dokarkiv.DokArkivClient
import no.nav.su.se.bakover.client.inntekt.SuInntektClient
import no.nav.su.se.bakover.client.kodeverk.KodeverkHttpClient
import no.nav.su.se.bakover.client.oppdrag.IbmMqPublisher
import no.nav.su.se.bakover.client.oppdrag.MqPublisher.MqPublisherConfig
import no.nav.su.se.bakover.client.oppdrag.kvittering.KvitteringIbmMqConsumer
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringConfig
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringSoapClient
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingMqPublisher
import no.nav.su.se.bakover.client.oppgave.OppgaveHttpClient
import no.nav.su.se.bakover.client.pdf.PdfClient
import no.nav.su.se.bakover.client.person.PersonClient
import no.nav.su.se.bakover.client.sts.StsClient
import no.nav.su.se.bakover.common.Config

object ProdClientsBuilder : ClientsBuilder {

    override fun build(): Clients {
        val oAuth = AzureClient(Config.azureClientId, Config.azureClientSecret, Config.azureWellKnownUrl)
        val kodeverk = KodeverkHttpClient(Config.kodeverkUrl, "srvsupstonad")
        val tokenOppslag = StsClient(Config.stsUrl, Config.serviceUser.username, Config.serviceUser.password)
        val personOppslag = PersonClient(kodeverk, Config.pdlUrl, tokenOppslag)
        val jmsConnection = MQConnectionFactory().apply {
            Config.utbetaling.let {
                hostName = it.mqHostname
                port = it.mqPort
                channel = it.mqChannel
                queueManager = it.mqQueueManager
                transportType = WMQConstants.WMQ_CM_CLIENT
            }
        }.createConnection(Config.serviceUser.username, Config.serviceUser.password)
        KvitteringIbmMqConsumer(
            kvitteringQueueName = Config.utbetaling.mqReplyTo,
            connection = jmsConnection
        )
        return Clients(
            oauth = oAuth,
            personOppslag = personOppslag,
            inntektOppslag = SuInntektClient(
                Config.suInntektUrl,
                Config.suInntektAzureClientId,
                oAuth,
                personOppslag
            ),
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
                mqPublisher = Config.Utbetaling(serviceUser = Config.serviceUser).let {
                    IbmMqPublisher(
                        MqPublisherConfig(
                            sendQueue = it.mqSendQueue,
                            replyTo = it.mqReplyTo
                        ),
                        connection = jmsConnection
                    )
                }
            )
        )
    }
}
