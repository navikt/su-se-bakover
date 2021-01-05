package no.nav.su.se.bakover.web.routes.me

import arrow.core.Either
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.client.person.MicrosoftGraphResponse
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.jwtStub
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test

internal class MeRoutesKtTest {

    @Test
    fun `GET me should return NAVIdent and roller`() {
        val microsoftGraphResponse =
            MicrosoftGraphResponse(
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
                clients = TestClientsBuilder.testClients.copy(
                    microsoftGraphApiClient = object : MicrosoftGraphApiOppslag {
                        override fun hentBrukerinformasjon(userToken: String): Either<MicrosoftGraphApiOppslagFeil, MicrosoftGraphResponse> =
                            Either.Right(microsoftGraphResponse)

                        override fun hentBrukerinformasjonForNavIdent(navIdent: String): Either<MicrosoftGraphApiOppslagFeil, MicrosoftGraphResponse> {
                            TODO("Not yet implemented")
                        }
                    }
                )
            )
        }) {
            handleRequest(
                HttpMethod.Get,
                "/me"
            ) {
                addHeader(
                    HttpHeaders.Authorization,
                    jwtStub.create(
                        subject = "random",
                        roller = listOf(Brukerrolle.Attestant)
                    )
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
                deserialize<UserData>(response.content!!).let {
                    it.navIdent shouldBe microsoftGraphResponse.onPremisesSamAccountName
                    it.roller.shouldContainExactly(Brukerrolle.Attestant)
                }
            }
        }
    }
}
