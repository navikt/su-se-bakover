package no.nav.su.se.bakover.web

import io.ktor.client.request.header
import io.ktor.client.utils.EmptyContent.status
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.stubs.asBearerToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Date

const val secureEndpoint = "/me"

internal class AuthenticationTest {

    @Test
    fun `secure endpoint krever autentisering`() {
        testApplication {
            application {
                testSusebakover()
            }
            defaultRequest(Get, secureEndpoint).apply {
                assertEquals(Unauthorized, this.status)
            }
        }
    }

    @Test
    fun `secure endpoint ok med gyldig token`() {
        testApplication {
            application {
                testSusebakover()
            }
            defaultRequest(Get, secureEndpoint, listOf(Brukerrolle.Veileder)).apply {
                assertEquals(OK, status)
            }
        }
    }

    @Test
    fun `forespørsel uten påkrevet audience skal svare med 401`() {
        testApplication {
            application {
                testSusebakover()
            }
            defaultRequest(Get, secureEndpoint) {
                header(Authorization, jwtStub.createJwtToken(audience = "wrong_audience").asBearerToken())
            }
        }.apply {
            assertEquals(Unauthorized, status)
        }
    }

    @Test
    fun `forespørsel uten medlemskap i påkrevet gruppe skal svare med 401`() {
        testApplication {
            application {
                testSusebakover()
            }
            defaultRequest(Get, secureEndpoint) {
                header(Authorization, jwtStub.createJwtToken(roller = emptyList()).asBearerToken())
            }
        }.apply {
            assertEquals(Unauthorized, status)
        }
    }

    @Test
    fun `forespørsel med utgått token skal svare med 401`() {
        testApplication {
            application {
                testSusebakover()
            }
            defaultRequest(Get, secureEndpoint) {
                header(
                    Authorization,
                    jwtStub.createJwtToken(expiresAt = Date.from(Instant.now().minusSeconds(1))).asBearerToken(),
                )
            }.apply {
                assertEquals(Unauthorized, status)
            }
        }
    }

    @Test
    fun `forespørsel med feil issuer skal svare med 401`() {
        testApplication {
            application {
                testSusebakover()
            }
            defaultRequest(Get, secureEndpoint) {
                header(Authorization, jwtStub.createJwtToken(issuer = "wrong_issuer").asBearerToken())
            }
        }.apply {
            assertEquals(Unauthorized, status)
        }
    }
}
