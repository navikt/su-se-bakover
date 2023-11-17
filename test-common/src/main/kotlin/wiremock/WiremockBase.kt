package no.nav.su.se.bakover.test.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.su.se.bakover.common.infrastructure.mdc.putInMdcIfMissing

/**
 * Starts a new WireMock server on a random port and sets the MDC variable 'X-Correlation-ID' to "correlationId".
 */
fun startedWireMockServerWithCorrelationId(block: WireMockServer.() -> Unit) {
    WireMockServer(
        WireMockConfiguration.options().dynamicPort(),
    ).also {
        it.start()
        putInMdcIfMissing("Authorization", "Bearer token")
        putInMdcIfMissing("X-Correlation-ID", "correlationId")
        block(it)
        // Siden testene kjører parallellt (men gjenbruker tråder), kan vi ikke fjerne X-Correlation-ID og Authorization, i tilfelle en annen test kjører block(it) etter vi fjerner.
        it.stop()
    }
}
