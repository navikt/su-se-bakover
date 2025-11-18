package no.nav.su.se.bakover.test.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.su.se.bakover.common.domain.auth.Kontekst
import no.nav.su.se.bakover.common.domain.auth.TokenContext
import no.nav.su.se.bakover.common.infrastructure.mdc.putInMdcIfMissing

/**
 * Starts a new WireMock server on a random port and sets the MDC variable 'X-Correlation-ID' to "correlationId".
 */
fun startedWireMockServerWithCorrelationId(token: String = "Bearer token", block: WireMockServer.() -> Unit) {
    WireMockServer(WireMockConfiguration.options().dynamicPort()).also { server ->
        server.start()
        putInMdcIfMissing("X-Correlation-ID", "correlationId")
        Kontekst.set(TokenContext(token)) // temporary, remove after block
        try {
            block(server)
        } finally {
            Kontekst.remove()
            server.stop()
        }
    }
}
