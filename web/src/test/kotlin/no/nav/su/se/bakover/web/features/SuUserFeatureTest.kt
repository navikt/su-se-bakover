package no.nav.su.se.bakover.web.features

import arrow.core.Either
import arrow.core.left
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.client.person.MicrosoftGraphResponse
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.web.TestClientsBuilder.testClients
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SuUserFeatureTest {

    @Test
    fun `should run in the application pipeline`() {
        val response = MicrosoftGraphResponse(
            onPremisesSamAccountName = "heisann",
            displayName = "dp",
            givenName = "gn",
            mail = "mail",
            officeLocation = "ol",
            surname = "sn",
            userPrincipalName = "upn",
            id = "id",
            jobTitle = "jt"
        )

        withTestApplication({
            testSusebakover(
                clients = testClients.copy(
                    microsoftGraphApiClient = object : MicrosoftGraphApiOppslag {
                        override fun hentBrukerinformasjon(userToken: String): Either<MicrosoftGraphApiOppslagFeil, MicrosoftGraphResponse> =
                            Either.right(response)

                        override fun hentBrukerinformasjonForNavIdent(navIdent: NavIdentBruker) = throw NotImplementedError("ikke i bruk")
                    }
                )
            )
        }) {
            defaultRequest(HttpMethod.Get, "/saker/${UUID.randomUUID()}", listOf(Brukerrolle.Veileder)).apply {
                this.suUserContext.user shouldBe response
                this.suUserContext.getNAVIdent() shouldBe "heisann"
            }
        }
    }

    @Test
    fun `should not respond with 500 if fetching from Microsoft Graph API fails but endpoint does not access it`() {
        withTestApplication({
            testSusebakover(
                clients = testClients.copy(
                    microsoftGraphApiClient = object : MicrosoftGraphApiOppslag {
                        override fun hentBrukerinformasjon(userToken: String): Either<MicrosoftGraphApiOppslagFeil, MicrosoftGraphResponse> =
                            MicrosoftGraphApiOppslagFeil.KallTilMicrosoftGraphApiFeilet.left()

                        override fun hentBrukerinformasjonForNavIdent(navIdent: NavIdentBruker) = throw NotImplementedError("ikke i bruk")
                    }
                )
            )
        }) {
            defaultRequest(HttpMethod.Get, "/saker/${UUID.randomUUID()}", listOf(Brukerrolle.Veileder)).apply {
                this.response.status() shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun `should respond with 500 if fetching from Microsoft Graph API and endpoint needs that data`() {
        withTestApplication({
            testSusebakover(
                clients = testClients.copy(
                    microsoftGraphApiClient = object : MicrosoftGraphApiOppslag {
                        override fun hentBrukerinformasjon(userToken: String): Either<MicrosoftGraphApiOppslagFeil, MicrosoftGraphResponse> =
                            MicrosoftGraphApiOppslagFeil.FeilVedHentingAvOnBehalfOfToken.left()

                        override fun hentBrukerinformasjonForNavIdent(navIdent: NavIdentBruker) = throw NotImplementedError("ikke i bruk")
                    }
                )
            )
        }) {
            defaultRequest(HttpMethod.Get, "/me", listOf(Brukerrolle.Veileder)).apply {
                this.response.status() shouldBe HttpStatusCode.InternalServerError
                shouldThrow<KallMotMicrosoftGraphApiFeilet> { suUserContext.user }
                shouldThrow<KallMotMicrosoftGraphApiFeilet> { suUserContext.getNAVIdent() }
            }
        }
    }
}
