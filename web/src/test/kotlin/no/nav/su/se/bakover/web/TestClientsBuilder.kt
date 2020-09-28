package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.ClientsBuilder
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.kodeverk.KodeverkHttpClient
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiClient
import no.nav.su.se.bakover.client.stubs.dokarkiv.DokArkivStub
import no.nav.su.se.bakover.client.stubs.dokdistfordeling.DokDistFordelingStub
import no.nav.su.se.bakover.client.stubs.inntekt.InntektOppslagStub
import no.nav.su.se.bakover.client.stubs.oppdrag.AvstemmingStub
import no.nav.su.se.bakover.client.stubs.oppdrag.SimuleringStub
import no.nav.su.se.bakover.client.stubs.oppdrag.UtbetalingStub
import no.nav.su.se.bakover.client.stubs.oppgave.OppgaveClientStub
import no.nav.su.se.bakover.client.stubs.pdf.PdfGeneratorStub
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.common.Config
import org.json.JSONObject

object TestClientsBuilder : ClientsBuilder {

    val testClients = Clients(
        oauth = OauthStub(),
        personOppslag = PersonOppslagStub,
        inntektOppslag = InntektOppslagStub,
        tokenOppslag = TokenOppslagStub,
        pdfGenerator = PdfGeneratorStub,
        dokArkiv = DokArkivStub,
        oppgaveClient = OppgaveClientStub,
        kodeverk = KodeverkHttpClient(Config.kodeverkUrl, "kodeverkConsumerId"),
        simuleringClient = SimuleringStub,
        utbetalingPublisher = UtbetalingStub,
        dokDistFordeling = DokDistFordelingStub,
        avstemmingPublisher = AvstemmingStub,
        microsoftGraphApiClient = MicrosoftGraphApiClient(OauthStub()),
    )

    override fun build(): Clients = testClients

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
    }
}
