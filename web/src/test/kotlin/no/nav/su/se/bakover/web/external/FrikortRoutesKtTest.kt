package no.nav.su.se.bakover.web.external

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.roller.Eksternrolle
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.startOfDay
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.vedtak.InnvilgetForMåned
import no.nav.su.se.bakover.test.jwt.DEFAULT_IDENT
import no.nav.su.se.bakover.test.jwt.asBearerToken
import no.nav.su.se.bakover.vedtak.application.VedtakService
import no.nav.su.se.bakover.web.DEFAULT_CALL_ID
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.customizeableJwtRequest
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.jwtStub
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.skyscreamer.jsonassert.JSONAssert
import java.time.Clock
import java.time.ZoneOffset

internal class FrikortRoutesKtTest {
    private val fnr = Fnr("42920322544")
    private val klokke = Clock.fixed(31.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    @Test
    fun `secure endpoint krever autentisering`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            client.get(FRIKORT_PATH) {
                header(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
            }.apply {
                this.status shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    fun `Krever riktig rolle, ingen brukerroller skal gi tilgang til frikorttjensten, må være frikort som kaller oss`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(HttpMethod.Get, FRIKORT_PATH, navIdent = DEFAULT_IDENT, roller = Brukerrolle.entries).apply {
                this.status shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    fun `Må ha audience`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            customizeableJwtRequest(
                HttpMethod.Get,
                FRIKORT_PATH,
                externalRoles = listOf(Eksternrolle.FRIKORT.rolle),
            ).apply {
                this.status shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    fun `Må ha riktig audience, feil gir unauth`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            customizeableJwtRequest(
                HttpMethod.Get,
                FRIKORT_PATH,
                audience = "noe-annet-enn-su",
                externalRoles = listOf(Eksternrolle.FRIKORT.rolle),
            ).apply {
                this.status shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    fun `Riktig audience men feil rolle`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(
                HttpMethod.Get,
                FRIKORT_PATH,
                navIdent = DEFAULT_IDENT,
                externalRoles = listOf("etterlatte"),
            ).apply {
                this.status shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    fun `secure endpoint ok med gyldig token`() {
        val vedtakServiceMock = mock<VedtakService> {
            on { hentInnvilgetFnrForMåned(any()) } doReturn InnvilgetForMåned(januar(2021), emptyList())
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(vedtakService = vedtakServiceMock))
            }
            client.get(FRIKORT_PATH) {
                header(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
                header(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(externalRoles = listOf(Eksternrolle.FRIKORT.rolle), subject = "frikorttjenester").asBearerToken(),
                )
            }.apply {
                this.status shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `sjekk feilmelding ved ugyldig dato`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            client.get("$FRIKORT_PATH/202121") {
                header(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
                header(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(externalRoles = listOf(Eksternrolle.FRIKORT.rolle), subject = "frikorttjenester").asBearerToken(),
                )
            }.apply {
                this.status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain ("Ugyldig dato - dato må være på format yyyy-MM")
            }
        }
    }

    @Test
    fun `sjekk default dato`() {
        val frikortJsonString = """{"dato":"2021-01","fnr":["$fnr"]}"""

        val vedtakServiceMock = mock<VedtakService> {
            on { hentInnvilgetFnrForMåned(any()) } doReturn InnvilgetForMåned(januar(2021), listOf(fnr))
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    clock = klokke,
                    services = TestServicesBuilder.services(vedtakService = vedtakServiceMock),
                )
            }
            val response = client.get(FRIKORT_PATH) {
                header(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
                header(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(externalRoles = listOf(Eksternrolle.FRIKORT.rolle), subject = "frikorttjenester").asBearerToken(),
                )
            }
            JSONAssert.assertEquals(frikortJsonString, response.bodyAsText(), true)
            verify(vedtakServiceMock).hentInnvilgetFnrForMåned(januar(2021))
            verifyNoMoreInteractions(vedtakServiceMock)
        }
    }

    @Test
    fun `sjekk angitt dato`() {
        val frikortJsonString = """{"dato":"2021-02","fnr":["$fnr"]}"""

        val vedtakServiceMock = mock<VedtakService> {
            on { hentInnvilgetFnrForMåned(any()) } doReturn InnvilgetForMåned(februar(2021), listOf(fnr))
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    clock = klokke,
                    services = TestServicesBuilder.services(vedtakService = vedtakServiceMock),
                )
            }

            val response = client.get("$FRIKORT_PATH/2021-02") {
                header(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
                header(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(externalRoles = listOf(Eksternrolle.FRIKORT.rolle), subject = "frikorttjenester").asBearerToken(),
                )
            }
            JSONAssert.assertEquals(frikortJsonString, response.bodyAsText(), true)
            verify(vedtakServiceMock).hentInnvilgetFnrForMåned(februar(2021))
            verifyNoMoreInteractions(vedtakServiceMock)
        }
    }

    @Test
    fun `ingen funnet gir tomt array`() {
        val frikortJsonString = """{"dato":"2021-02","fnr":[]}"""

        val vedtakServiceMock = mock<VedtakService> {
            on { hentInnvilgetFnrForMåned(any()) } doReturn InnvilgetForMåned(februar(2021), emptyList())
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    clock = klokke,
                    services = TestServicesBuilder.services(vedtakService = vedtakServiceMock),
                )
            }
            val response = client.get("$FRIKORT_PATH/2021-02") {
                header(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
                header(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(externalRoles = listOf(Eksternrolle.FRIKORT.rolle), subject = "frikorttjenester").asBearerToken(),
                )
            }
            JSONAssert.assertEquals(frikortJsonString, response.bodyAsText(), true)
            verify(vedtakServiceMock).hentInnvilgetFnrForMåned(februar(2021))
            verifyNoMoreInteractions(vedtakServiceMock)
        }
    }
}
