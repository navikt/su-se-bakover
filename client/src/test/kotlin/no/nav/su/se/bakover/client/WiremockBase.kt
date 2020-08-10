package no.nav.su.se.bakover.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.slf4j.MDC

interface WiremockBase {
    companion object {
        val wireMockServer = WireMockServer(
            WireMockConfiguration.options().dynamicPort()
        )

        @JvmStatic
        @BeforeAll
        fun wiremockBaseBeforeAll() {
            wireMockServer.start()
            MDC.put("X-Correlation-ID", "correlationId")
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            wireMockServer.stop()
        }
    }

    @BeforeEach
    fun wiremockBeforeEach() {
        wireMockServer.resetAll()
    }
}
