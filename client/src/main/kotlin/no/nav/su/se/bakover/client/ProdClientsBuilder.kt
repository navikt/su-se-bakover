package no.nav.su.se.bakover.client

import no.nav.su.se.bakover.client.azure.AzureClient
import no.nav.su.se.bakover.client.dokarkiv.DokArkivClient
import no.nav.su.se.bakover.client.inntekt.SuInntektClient
import no.nav.su.se.bakover.client.kodeverk.KodeverkHttpClient
import no.nav.su.se.bakover.client.oppdrag.IbmMqClient
import no.nav.su.se.bakover.client.oppdrag.MqClient.MqConfig
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringConfig
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringSoapClient
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingMqClient
import no.nav.su.se.bakover.client.oppgave.OppgaveHttpClient
import no.nav.su.se.bakover.client.pdf.PdfClient
import no.nav.su.se.bakover.client.person.PersonClient
import no.nav.su.se.bakover.client.sts.StsClient
import no.nav.su.se.bakover.common.Config

object ProdClientsBuilder : ClientsBuilder {

    override fun build(): Clients {
        val oAuth = AzureClient(Config.azureClientId, Config.azureClientSecret, Config.azureWellKnownUrl)
        val kodeverk = KodeverkHttpClient(Config.kodeverkUrl, "srvsupstonad")
        val tokenOppslag = StsClient(Config.stsUrl, Config.stsUsername, Config.stsPassword)
        val personOppslag = PersonClient(kodeverk, Config.pdlUrl, tokenOppslag, Config.azureClientId, oAuth)
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
                    username = Config.stsUsername,
                    password = Config.stsPassword,
                    disableCNCheck = true
                ).wrapWithSTSSimulerFpService()
            ),
            utbetalingClient = UtbetalingMqClient(
                mqClient = Config.Utbetaling().let {
                    IbmMqClient(
                        MqConfig(
                            username = it.mqUsername,
                            password = it.mqPassword,
                            queueManager = it.mqQueueManager,
                            port = it.mqPort,
                            hostname = it.mqHostname,
                            channel = it.mqChannel,
                            sendQueue = it.mqSendQueue,
                            replyTo = it.mqReplyTo

                        )
                    )
                }
            )
        )
    }
}
