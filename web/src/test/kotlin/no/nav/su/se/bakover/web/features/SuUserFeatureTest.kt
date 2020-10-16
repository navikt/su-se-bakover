package no.nav.su.se.bakover.web.features

import arrow.core.Either
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.application.call
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphResponse
import no.nav.su.se.bakover.client.person.PdlFeil
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.TestClientsBuilder.testClients
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.lesFnr
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test

@KtorExperimentalAPI
internal class SuUserFeatureTest {
    @Test
    fun `should run in the application pipeline`() {
        val microsoftGraphResponse = MicrosoftGraphResponse(
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
                        override fun hentBrukerinformasjon(userToken: String): Either<String, MicrosoftGraphResponse> =
                            Either.right(microsoftGraphResponse)
                    }
                )
            )
            routing {
                withUser {
                    get("/test") {
                        call.respond(HttpStatusCode.OK, call.suUserContext.user)
                    }
                }
            }
        }) {
            defaultRequest(HttpMethod.Get, "/test", listOf(Brukerrolle.Veileder)).apply {
                response.content shouldContain microsoftGraphResponse.onPremisesSamAccountName!!
                this.suUserContext.user shouldBe microsoftGraphResponse
                this.suUserContext.getNAVIdent() shouldBe "heisann"
            }
        }
    }

    @Test
    fun `should not respond with 500 if fetching from Microsoft Graph API fails but endpoint does not access it`() {
        val response = Either.left(
            "Feil"
        )

        withTestApplication({
            testSusebakover(
                clients = testClients.copy(
                    microsoftGraphApiClient = object : MicrosoftGraphApiOppslag {
                        override fun hentBrukerinformasjon(userToken: String): Either<String, MicrosoftGraphResponse> =
                            response
                    }
                )
            )
            routing {
                withUser {
                    get("/test") {
                        call.respond(HttpStatusCode.NotFound, "oof")
                    }
                }
            }
        }) {
            defaultRequest(HttpMethod.Get, "/test", listOf(Brukerrolle.Veileder)).apply {
                this.response.status() shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun `should respond with 500 if fetching from Microsoft Graph API and endpoint needs that data`() {
        val response = Either.left(
            "Feil"
        )

        withTestApplication({
            testSusebakover(
                clients = testClients.copy(
                    microsoftGraphApiClient = object : MicrosoftGraphApiOppslag {
                        override fun hentBrukerinformasjon(userToken: String): Either<String, MicrosoftGraphResponse> =
                            response
                    }
                )
            )
            routing {
                withUser {
                    get("/test") {
                        call.respond(HttpStatusCode.OK, call.suUserContext.user)
                    }
                }
            }
        }) {
            defaultRequest(HttpMethod.Get, "/test", listOf(Brukerrolle.Veileder)).apply {
                this.response.status() shouldBe HttpStatusCode.InternalServerError
                shouldThrow<KallMotMicrosoftGraphApiFeilet> { suUserContext.user }
                shouldThrow<KallMotMicrosoftGraphApiFeilet> { suUserContext.getNAVIdent() }
            }
        }
    }

    @Test
    fun `Endepunkt som kaller assertBrukerHarTilgangTilPerson gir 403 dersom PDL gir 403`() {
        withTestApplication({
            testSusebakover(
                clients = testClients.copy(
                    personOppslag = object : PersonOppslag {
                        override fun person(fnr: Fnr): Either<PdlFeil, Person> =
                            Either.Left(PdlFeil.IkkeTilgangTilPerson)

                        override fun aktørId(fnr: Fnr): Either<PdlFeil, AktørId> {
                            TODO("Not yet implemented")
                        }
                    }
                )
            )
            routing {
                withUser {
                    get("/test/{fnr}") {
                        call.lesFnr("fnr")
                            .map { fnr ->
                                call.suUserContext
                                    .assertBrukerHarTilgangTilPerson(fnr)
                            }

                        call.respond(HttpStatusCode.InternalServerError, "oof")
                    }
                }
            }
        }) {
            defaultRequest(HttpMethod.Get, "/test/${FnrGenerator.random()}", listOf(Brukerrolle.Veileder)).apply {
                this.response.status() shouldBe HttpStatusCode.Forbidden
            }
        }
    }
}
