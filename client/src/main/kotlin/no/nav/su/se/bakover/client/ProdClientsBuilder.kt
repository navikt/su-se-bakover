package no.nav.su.se.bakover.client

import no.nav.su.se.bakover.client.azure.AzureClient
import no.nav.su.se.bakover.client.dokarkiv.DokArkivClient
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordelingClient
import no.nav.su.se.bakover.client.inntekt.SuInntektClient
import no.nav.su.se.bakover.client.kodeverk.KodeverkHttpClient
import no.nav.su.se.bakover.client.oppdrag.IbmMqPublisher
import no.nav.su.se.bakover.client.oppdrag.MqPublisher.MqPublisherConfig
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringConfig
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringSoapClient
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingMqPublisher
import no.nav.su.se.bakover.client.oppgave.OppgaveHttpClient
import no.nav.su.se.bakover.client.pdf.PdfClient
import no.nav.su.se.bakover.client.person.PersonClient
import no.nav.su.se.bakover.client.sts.StsClient
import no.nav.su.se.bakover.common.Config
import javax.jms.Connection

data class ProdClientsBuilder(private val jmsConnection: Connection) : ClientsBuilder {

    override fun build(): Clients {
        val oAuth = AzureClient(Config.azureClientId, Config.azureClientSecret, Config.azureWellKnownUrl)
        val kodeverk = KodeverkHttpClient(Config.kodeverkUrl, "srvsupstonad")
        val tokenOppslag = StsClient(Config.stsUrl, Config.serviceUser.username, Config.serviceUser.password)
        val personOppslag = PersonClient(kodeverk, Config.pdlUrl, tokenOppslag)

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
            ),
            dokDistFordeling = DokDistFordelingClient(Config.dokDistUrl, tokenOppslag)
        )
    }
}
