package no.nav.su.se.bakover.web

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.auth.TokenContext
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.jwt.asBearerToken
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant
import java.util.Date

// TODO jah: Se på navngivningen her.
const val SECURE_ENDPOINT = "/me"

internal class AuthenticationTest {
    @Test
    fun `brukerkontekstens tekstrepresentasjon røper ikke token`() {
        val token = "test-token"
        val kontekst = TokenContext(token)

        kontekst.toString() shouldNotContain token
    }

    @Test
    fun `ukjent Azure-gruppe gir ikke applikasjonstilgang alene`() {
        val ukjentGruppe = "ukjent-gruppe"
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            client.get(SECURE_ENDPOINT) {
                header(
                    Authorization,
                    jwtStub.createJwtToken(
                        roller = emptyList(),
                        extraGroups = listOf(ukjentGruppe),
                    ).asBearerToken(),
                )
            }.status shouldBe Unauthorized
        }
    }

    @Test
    fun `secure endpoint krever autentisering`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(Get, SECURE_ENDPOINT).apply {
                this.status shouldBe Unauthorized
            }
        }
    }

    @Test
    fun `secure endpoint ok med gyldig token`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(Get, SECURE_ENDPOINT, listOf(Brukerrolle.Veileder)).apply {
                status shouldBe OK
            }
        }
    }

    @Test
    fun `svarer med 500 hvis brukerinformasjon ikke lar seg hente`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            client.get(SECURE_ENDPOINT) {
                headers {
                    append(HttpHeaders.Authorization, jwtStub.createJwtToken(navIdent = null).asBearerToken())
                }
            }.apply {
                status shouldBe InternalServerError
                bodyAsText() shouldContain "Ukjent feil ved uthenting av brukerinformasjon"
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = Brukerrolle::class, names = ["Veileder", "Statistikk"])
    fun `forespørsel uten påkrevet audience skal svare med 401`(rolle: Brukerrolle) {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            client.get(SECURE_ENDPOINT) {
                header(
                    Authorization,
                    jwtStub.createJwtToken(audience = "wrong_audience", roller = listOf(rolle)).asBearerToken(),
                )
            }.apply {
                status shouldBe Unauthorized
            }
        }
    }

    @Test
    fun `forespørsel uten medlemskap i påkrevet gruppe skal svare med 401`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            client.get(SECURE_ENDPOINT) {
                header(Authorization, jwtStub.createJwtToken(roller = emptyList()).asBearerToken())
            }.apply {
                status shouldBe Unauthorized
            }
        }
    }

    @Test
    fun `forespørsel med utgått token skal svare med 401`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            client.get(SECURE_ENDPOINT) {
                header(
                    Authorization,
                    jwtStub.createJwtToken(expiresAt = Date.from(Instant.now(fixedClock).minusSeconds(1)))
                        .asBearerToken(),
                )
            }.apply {
                status shouldBe Unauthorized
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = Brukerrolle::class, names = ["Veileder", "Statistikk"])
    fun `forespørsel med feil issuer skal svare med 401`(rolle: Brukerrolle) {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            client.get(SECURE_ENDPOINT) {
                header(
                    Authorization,
                    jwtStub.createJwtToken(issuer = "wrong_issuer", roller = listOf(rolle)).asBearerToken(),
                )
            }.apply {
                status shouldBe Unauthorized
            }
        }
    }
}
