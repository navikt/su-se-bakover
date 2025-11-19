package no.nav.su.se.bakover.test.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.su.se.bakover.common.domain.auth.Kontekst
import no.nav.su.se.bakover.common.domain.auth.TokenContext
import no.nav.su.se.bakover.common.infrastructure.mdc.putInMdcIfMissing

/**
 * Starts a new WireMock server on a random port and sets the MDC variable 'X-Correlation-ID' to "correlationId".
 */
fun startedWireMockServerWithCorrelationId(token: String = "Bearer token", block: WireMockServer.() -> Unit) {
    runBlocking {
        val server = WireMockServer(WireMockConfiguration.options().dynamicPort())
        server.start()
        putInMdcIfMissing("X-Correlation-ID", "correlationId")

        val tokenContextElement = Kontekst.asContextElement(TokenContext(token))

        try {
            withContext(tokenContextElement) {
                block(server)
            }
        } finally {
            server.stop()
        }
    }
}
