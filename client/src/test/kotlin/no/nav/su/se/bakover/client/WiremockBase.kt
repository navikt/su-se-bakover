package no.nav.su.se.bakover.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.BeforeEach
import org.slf4j.MDC

interface WiremockBase {
    companion object {

        val wireMockServer: WireMockServer by lazy {
            WireMockServer(
                WireMockConfiguration.options().dynamicPort(),
            ).also {
                it.start()
                // Denne kan da ikke overskrives fra testene.
                putCorrelationId()
            }
        }

        fun removeCorrelationId() {
            MDC.remove("X-Correlation-ID")
        }

        fun putCorrelationId() {
            MDC.put("X-Correlation-ID", "correlationId")
        }
    }

    @BeforeEach
    fun wiremockBeforeEach() {
        wireMockServer.resetAll()
    }
}
