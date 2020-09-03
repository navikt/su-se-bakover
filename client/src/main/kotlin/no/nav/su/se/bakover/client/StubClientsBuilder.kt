package no.nav.su.se.bakover.client

import no.nav.su.se.bakover.client.azure.AzureClient
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordelingClient
import no.nav.su.se.bakover.client.inntekt.InntektOppslag
import no.nav.su.se.bakover.client.kodeverk.KodeverkHttpClient
import no.nav.su.se.bakover.client.pdf.PdfClient
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.client.stubs.dokarkiv.DokArkivStub
import no.nav.su.se.bakover.client.stubs.dokdistfordeling.DokDistFordelingStub
import no.nav.su.se.bakover.client.stubs.inntekt.InntektOppslagStub
import no.nav.su.se.bakover.client.stubs.oppdrag.SimuleringStub
import no.nav.su.se.bakover.client.stubs.oppdrag.UtbetalingStub
import no.nav.su.se.bakover.client.stubs.oppgave.OppgaveClientStub
import no.nav.su.se.bakover.client.stubs.pdf.PdfGeneratorStub
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import org.slf4j.LoggerFactory

object StubClientsBuilder : ClientsBuilder {

    override fun build(): Clients {
        return Clients(
            oauth = AzureClient(Config.azureClientId, Config.azureClientSecret, Config.azureWellKnownUrl),
            personOppslag = PersonOppslagStub.also { logger.warn("********** Using stub for ${PersonOppslag::class.java} **********") },
            inntektOppslag = InntektOppslagStub.also { logger.warn("********** Using stub for ${InntektOppslag::class.java} **********") },
            tokenOppslag = TokenOppslagStub.also { logger.warn("********** Using stub for ${TokenOppslag::class.java} **********") },
            pdfGenerator = if (Config.pdfgenLocal) {
                PdfClient("http://localhost:8081")
            } else {
                PdfGeneratorStub.also { logger.warn("********** Using stub for ${PdfGenerator::class.java} **********") }
            },
            dokArkiv = DokArkivStub.also { logger.warn("********** Using stub for ${DokArkiv::class.java} **********") },
            oppgaveClient = OppgaveClientStub.also { logger.warn("********** Using stub for ${OppgaveClient::class.java} **********") },
            kodeverk = KodeverkHttpClient(Config.kodeverkUrl, "srvsupstonad"),
            simuleringClient = SimuleringStub.also { logger.warn("********** Using stub for ${SimuleringClient::class.java} **********") },
            utbetalingPublisher = UtbetalingStub.also { logger.warn("********** Using stub for ${UtbetalingPublisher::class.java} **********") },
            dokDistFordeling= DokDistFordelingStub.also { logger.warn("********** Using stub for ${DokDistFordelingClient::class.java} **********") },
        )
    }

    private val logger = LoggerFactory.getLogger(StubClientsBuilder::class.java)
}
