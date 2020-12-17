package no.nav.su.se.bakover.client

import no.nav.su.se.bakover.client.azure.AzureClient
import no.nav.su.se.bakover.client.dkif.DigitalKontaktinformasjon
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordelingClient
import no.nav.su.se.bakover.client.kodeverk.KodeverkHttpClient
import no.nav.su.se.bakover.client.pdf.PdfClient
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiClient
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.client.stubs.dkif.DkifClientStub
import no.nav.su.se.bakover.client.stubs.dokarkiv.DokArkivStub
import no.nav.su.se.bakover.client.stubs.dokdistfordeling.DokDistFordelingStub
import no.nav.su.se.bakover.client.stubs.nais.LeaderPodLookupStub
import no.nav.su.se.bakover.client.stubs.oppdrag.AvstemmingStub
import no.nav.su.se.bakover.client.stubs.oppdrag.SimuleringStub
import no.nav.su.se.bakover.client.stubs.oppdrag.UtbetalingStub
import no.nav.su.se.bakover.client.stubs.oppgave.OppgaveClientStub
import no.nav.su.se.bakover.client.stubs.pdf.PdfGeneratorStub
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.person.PersonOppslag
import org.slf4j.LoggerFactory

object StubClientsBuilder : ClientsBuilder {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun build(azureConfig: Config.AzureConfig): Clients {

        val azureClient =
            AzureClient(azureConfig.clientId, azureConfig.clientSecret, azureConfig.wellKnownUrl)
        return Clients(
            oauth = azureClient,
            personOppslag = PersonOppslagStub.also { log.warn("********** Using stub for ${PersonOppslag::class.java} **********") },
            tokenOppslag = TokenOppslagStub.also { log.warn("********** Using stub for ${TokenOppslag::class.java} **********") },
            pdfGenerator = if (Config.pdfgenLocal) {
                PdfClient("http://localhost:8081")
            } else {
                PdfGeneratorStub.also { log.warn("********** Using stub for ${PdfGenerator::class.java} **********") }
            },
            dokArkiv = DokArkivStub.also { log.warn("********** Using stub for ${DokArkiv::class.java} **********") },
            oppgaveClient = OppgaveClientStub.also { log.warn("********** Using stub for ${OppgaveClient::class.java} **********") },
            kodeverk = KodeverkHttpClient(Config.kodeverkUrl, "srvsupstonad"),
            simuleringClient = SimuleringStub.also { log.warn("********** Using stub for ${SimuleringClient::class.java} **********") },
            utbetalingPublisher = UtbetalingStub.also { log.warn("********** Using stub for ${UtbetalingPublisher::class.java} **********") },
            dokDistFordeling = DokDistFordelingStub.also { log.warn("********** Using stub for ${DokDistFordelingClient::class.java} **********") },
            avstemmingPublisher = AvstemmingStub.also { log.warn("********** Using stub for ${AvstemmingPublisher::class.java} **********") },
            microsoftGraphApiClient = MicrosoftGraphApiClient(azureClient),
            digitalKontaktinformasjon = DkifClientStub.also { log.warn("********** Using stub for ${DigitalKontaktinformasjon::class.java} **********") },
            leaderPodLookup = LeaderPodLookupStub.also { log.warn("********** Using stub for ${LeaderPodLookup::class.java} **********") }
        )
    }
}
