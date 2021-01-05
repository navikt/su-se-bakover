package no.nav.su.se.bakover.web

import com.nhaarman.mockitokotlin2.mock
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.ClientsBuilder
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.stubs.dkif.DkifClientStub
import no.nav.su.se.bakover.client.stubs.dokarkiv.DokArkivStub
import no.nav.su.se.bakover.client.stubs.dokdistfordeling.DokDistFordelingStub
import no.nav.su.se.bakover.client.stubs.kafka.KafkaPublisherStub
import no.nav.su.se.bakover.client.stubs.nais.LeaderPodLookupStub
import no.nav.su.se.bakover.client.stubs.oppdrag.AvstemmingStub
import no.nav.su.se.bakover.client.stubs.oppdrag.SimuleringStub
import no.nav.su.se.bakover.client.stubs.oppdrag.UtbetalingStub
import no.nav.su.se.bakover.client.stubs.oppgave.OppgaveClientStub
import no.nav.su.se.bakover.client.stubs.pdf.PdfGeneratorStub
import no.nav.su.se.bakover.client.stubs.person.MicrosoftGraphApiClientStub
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.common.ApplicationConfig
import org.json.JSONObject

object TestClientsBuilder : ClientsBuilder {

    val testClients = Clients(
        oauth = OauthStub(),
        personOppslag = PersonOppslagStub,
        tokenOppslag = TokenOppslagStub,
        pdfGenerator = PdfGeneratorStub,
        dokArkiv = DokArkivStub,
        oppgaveClient = OppgaveClientStub,
        kodeverk = mock(),
        simuleringClient = SimuleringStub,
        utbetalingPublisher = UtbetalingStub,
        dokDistFordeling = DokDistFordelingStub,
        avstemmingPublisher = AvstemmingStub,
        microsoftGraphApiClient = MicrosoftGraphApiClientStub(),
        digitalKontaktinformasjon = DkifClientStub,
        leaderPodLookup = LeaderPodLookupStub,
        kafkaPublisher = KafkaPublisherStub
    )

    fun build(): Clients = testClients
    override fun build(applicationConfig: ApplicationConfig): Clients = testClients

    internal class OauthStub : OAuth {
        override fun onBehalfOFToken(originalToken: String, otherAppId: String) = "ONBEHALFOFTOKEN"
        override fun refreshTokens(refreshToken: String) =
            JSONObject("""{"access_token":"abc","refresh_token":"cba"}""")

        override fun jwkConfig() = JSONObject(
            """
            {
                "jwks_uri": "http://localhost/keys",
                "token_endpoint": "http://localhost/token",
                "issuer": "azure",
                "authorization_endpoint": "http://localhost/authorize"
            }
            """.trimIndent()
        )

        override fun getSystemToken(otherAppId: String): String = "supert systemtoken"
    }
}
