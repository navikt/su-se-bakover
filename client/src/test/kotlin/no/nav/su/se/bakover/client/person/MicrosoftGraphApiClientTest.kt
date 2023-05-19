package no.nav.su.se.bakover.client.person

import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class MicrosoftGraphApiClientTest : WiremockBase {

    @Test
    fun hentNavnForNavIdent() {
        //language=JSON
        val suksessResponseJson =
            """
            {
              "value":[
                {
                  "displayName":"displayName",
                  "givenName":"givenName",
                  "mail":"mail",
                  "officeLocation":"officeLocation",
                  "surname":"surname",
                  "userPrincipalName":"userPrincipalName",
                  "id":"id",
                  "jobTitle":"jobTitle"
                }
              ]
            }
            """.trimIndent()
        val azureAdMock = mock<AzureAd> {
            on { getSystemToken(any()) } doReturn tokenOppslag.token().value
        }

        WiremockBase.wireMockServer.stubFor(
            wiremockBuilderSystembruker("Bearer ${tokenOppslag.token().value}")
                .willReturn(WireMock.ok(suksessResponseJson)),
        )

        val client = MicrosoftGraphApiClient(
            exchange = azureAdMock,
            baseUrl = WiremockBase.wireMockServer.baseUrl(),
        )
        client.hentNavnForNavIdent(NavIdentBruker.Saksbehandler("saksbehandler")) shouldBe "displayName".right()
    }

    private val tokenOppslag = TokenOppslagStub

    private fun wiremockBuilderSystembruker(authorization: String) = WireMock.get(
        WireMock.urlEqualTo(
            "/users?%24select=onPremisesSamAccountName%2CdisplayName%2CgivenName%2Cmail%2CofficeLocation%2Csurname%2CuserPrincipalName%2Cid%2CjobTitle&%24filter=onPremisesSamAccountName+eq+%27saksbehandler%27&%24count=true",
        ),
    )
        .withHeader("Authorization", WireMock.equalTo(authorization))
        .withHeader("ConsistencyLevel", WireMock.equalTo("eventual"))
}
