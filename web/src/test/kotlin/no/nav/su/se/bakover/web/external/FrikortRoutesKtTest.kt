package no.nav.su.se.bakover.web.external

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.web.DEFAULT_CALL_ID
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.applicationConfig
import no.nav.su.se.bakover.web.jwtStub
import no.nav.su.se.bakover.web.stubs.asBearerToken
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.Clock
import java.time.ZoneOffset
import kotlin.test.assertEquals

internal class FrikortRoutesKtTest {
    internal val testServices = TestServicesBuilder.services()

    @Test
    fun `secure endpoint krever autentisering`() {
        withTestApplication({
            testSusebakover()
        }) {
            handleRequest(HttpMethod.Get, frikortPath)
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `secure endpoint ok med gyldig token`() {
        val vedtakServiceMock = mock<VedtakService> {
            on { hentAktive(any()) } doReturn emptyList()
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
            assertEquals(HttpStatusCode.OK, response.status())
        }
    }

    @Test
    fun `sjekk default dato`() {
        val frikortJsonString = """{"dato":"2021-01","fnr":["42920322544"]}"""

        val behandlingMock = mock<Behandling> {
            on { fnr } doReturn Fnr("42920322544")
        }
        val vedtakMock = mock<Vedtak.InnvilgetStønad> {
            on { behandling } doReturn behandlingMock
        }

        val vedtakServiceMock = mock<VedtakService> {
            on { hentAktive(any()) } doReturn listOf(vedtakMock)
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

        val behandlingMock = mock<Behandling> {
            on { fnr } doReturn Fnr("42920322544")
        }
        val vedtakMock = mock<Vedtak.InnvilgetStønad> {
            on { behandling } doReturn behandlingMock
        }

        val vedtakServiceMock = mock<VedtakService> {
            on { hentAktive(any()) } doReturn listOf(vedtakMock)
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
