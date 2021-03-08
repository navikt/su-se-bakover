package no.nav.su.se.bakover.web.external

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

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
            defaultRequest(HttpMethod.Get, frikortPath, listOf(Brukerrolle.Drift))
        }.apply {
            assertEquals(HttpStatusCode.OK, response.status())
        }
    }
}
