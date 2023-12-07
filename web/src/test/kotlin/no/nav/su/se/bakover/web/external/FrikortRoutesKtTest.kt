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
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.startOfDay
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.vedtak.InnvilgetForMåned
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.jwt.asBearerToken
import no.nav.su.se.bakover.vedtak.application.VedtakService
import no.nav.su.se.bakover.web.DEFAULT_CALL_ID
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.jwtStub
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.time.Clock
import java.time.ZoneOffset

internal class FrikortRoutesKtTest {

    @Test
    fun `secure endpoint krever autentisering`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(HttpMethod.Get, FRIKORT_PATH, navIdent = "Z990Lokal", jwtSubject = "unknownSubject").apply {
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
                    jwtStub.createJwtToken(subject = applicationConfig().frikort.serviceUsername.first()).asBearerToken(),
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
                    jwtStub.createJwtToken(subject = applicationConfig().frikort.serviceUsername.first()).asBearerToken(),
                )
            }.apply {
                this.status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain ("Ugyldig dato - dato må være på format yyyy-MM")
            }
        }
    }

    @Test
    fun `sjekk default dato`() {
        val frikortJsonString = """{"dato":"2021-02","fnr":["42920322544"]}"""

        val vedtakServiceMock = mock<VedtakService> {
            on { hentInnvilgetFnrForMåned(any()) } doReturn InnvilgetForMåned(februar(2021), listOf(Fnr("42920322544")))
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    clock = Clock.fixed(31.januar(2021).startOfDay().instant, ZoneOffset.UTC),
                    services = TestServicesBuilder.services(vedtakService = vedtakServiceMock),
                )
            }
            val response = client.get(FRIKORT_PATH) {
                header(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
                header(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(subject = applicationConfig().frikort.serviceUsername.first()).asBearerToken(),
                )
            }
            JSONAssert.assertEquals(frikortJsonString, response.bodyAsText(), true)
        }
    }

    @Test
    fun `sjekk angitt dato`() {
        val frikortJsonString = """{"dato":"2021-02","fnr":["42920322544"]}"""

        val vedtakServiceMock = mock<VedtakService> {
            on { hentInnvilgetFnrForMåned(any()) } doReturn InnvilgetForMåned(februar(2021), listOf(Fnr("42920322544")))
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    clock = Clock.fixed(31.januar(2021).startOfDay().instant, ZoneOffset.UTC),
                    services = TestServicesBuilder.services(vedtakService = vedtakServiceMock),
                )
            }

            val response = client.get("$FRIKORT_PATH/2021-02") {
                header(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
                header(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(subject = applicationConfig().frikort.serviceUsername.first()).asBearerToken(),
                )
            }
            JSONAssert.assertEquals(frikortJsonString, response.bodyAsText(), true)
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
                    clock = Clock.fixed(31.januar(2021).startOfDay().instant, ZoneOffset.UTC),
                    services = TestServicesBuilder.services(vedtakService = vedtakServiceMock),
                )
            }
            val response = client.get("$FRIKORT_PATH/2021-02") {
                header(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
                header(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(subject = applicationConfig().frikort.serviceUsername.first()).asBearerToken(),
                )
            }
            JSONAssert.assertEquals(frikortJsonString, response.bodyAsText(), true)
        }
    }
}
