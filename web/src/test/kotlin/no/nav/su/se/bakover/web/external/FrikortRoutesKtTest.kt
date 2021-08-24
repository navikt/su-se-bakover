package no.nav.su.se.bakover.web.external

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.web.DEFAULT_CALL_ID
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.applicationConfig
import no.nav.su.se.bakover.web.jwtStub
import no.nav.su.se.bakover.web.stubs.asBearerToken
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.time.Clock
import java.time.ZoneOffset

internal class FrikortRoutesKtTest {
    internal val testServices = TestServicesBuilder.services()

    @Test
    fun `secure endpoint krever autentisering`() {
        withTestApplication({
            testSusebakover()
        }) {
            handleRequest(HttpMethod.Get, frikortPath)
        }.apply {
            response.status() shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `secure endpoint ok med gyldig token`() {
        val vedtakServiceMock = mock<VedtakService> {
            on { hentAktiveFnr(any()) } doReturn emptyList()
        }

        withTestApplication({
            testSusebakover(services = testServices.copy(vedtakService = vedtakServiceMock))
        }) {
            handleRequest(HttpMethod.Get, frikortPath) {
                addHeader(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
                addHeader(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(subject = applicationConfig.frikort.serviceUsername).asBearerToken()
                )
            }
        }.apply {
            response.status() shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `sjekk feilmelding ved ugyldig dato`() {
        withTestApplication({
            testSusebakover()
        }) {
            handleRequest(HttpMethod.Get, "$frikortPath/202121") {
                addHeader(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
                addHeader(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(subject = applicationConfig.frikort.serviceUsername).asBearerToken()
                )
            }
        }.apply {
            response.status() shouldBe HttpStatusCode.BadRequest
            response.content shouldContain ("Ugyldig dato - dato må være på format YYYY-MM")
        }
    }

    @Test
    fun `sjekk default dato`() {
        val frikortJsonString = """{"dato":"2021-01","fnr":["42920322544"]}"""

        val vedtakServiceMock = mock<VedtakService> {
            on { hentAktiveFnr(any()) } doReturn listOf(Fnr("42920322544"))
        }

        withTestApplication({
            testSusebakover(services = testServices.copy(vedtakService = vedtakServiceMock), clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC))
        }) {
            handleRequest(HttpMethod.Get, frikortPath) {
                addHeader(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
                addHeader(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(subject = applicationConfig.frikort.serviceUsername).asBearerToken()
                )
            }
        }.apply {
            JSONAssert.assertEquals(frikortJsonString, response.content, true)
        }
    }

    @Test
    fun `sjekk angitt dato`() {
        val frikortJsonString = """{"dato":"2021-02","fnr":["42920322544"]}"""

        val vedtakServiceMock = mock<VedtakService> {
            on { hentAktiveFnr(any()) } doReturn listOf(Fnr("42920322544"))
        }

        withTestApplication({
            testSusebakover(services = testServices.copy(vedtakService = vedtakServiceMock), clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC))
        }) {
            handleRequest(HttpMethod.Get, "$frikortPath/2021-02") {
                addHeader(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
                addHeader(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(subject = applicationConfig.frikort.serviceUsername).asBearerToken()
                )
            }
        }.apply {
            JSONAssert.assertEquals(frikortJsonString, response.content, true)
        }
    }

    @Test
    fun `ingen funnet gir tomt array`() {
        val frikortJsonString = """{"dato":"2021-02","fnr":[]}"""

        val vedtakServiceMock = mock<VedtakService> {
            on { hentAktiveFnr(any()) } doReturn emptyList()
        }

        withTestApplication({
            testSusebakover(services = testServices.copy(vedtakService = vedtakServiceMock), clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC))
        }) {
            handleRequest(HttpMethod.Get, "$frikortPath/2021-02") {
                addHeader(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
                addHeader(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(subject = applicationConfig.frikort.serviceUsername).asBearerToken()
                )
            }
        }.apply {
            JSONAssert.assertEquals(frikortJsonString, response.content, true)
        }
    }
}
