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
 * Hvis token her er null antar man at det er en systembruker da kun personbruker token ligger på konteksten
 */
fun startedWireMockServerWithCorrelationId(token: String? = "Bearer token", block: WireMockServer.() -> Unit) {
    runBlocking {
        val server = WireMockServer(WireMockConfiguration.options().dynamicPort())
        server.start()
        putInMdcIfMissing("X-Correlation-ID", "correlationId")

        try {
            if (token != null) {
                val tokenContextElement = Kontekst.asContextElement(TokenContext(token))
                withContext(tokenContextElement) {
                    block(server)
                }
            } else {
                block(server)
            }
        } finally {
            server.stop()
        }
    }
}
