package no.nav.su.se.bakover.client

import com.github.kittinunf.fuel.core.FuelManager
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.BeforeEach
import org.slf4j.MDC

interface WiremockBase {
    companion object {

        val wireMockServer: WireMockServer by lazy {
            WireMockServer(
                WireMockConfiguration.options().dynamicPort()
            ).also {
                it.start()
                // Denne kan da ikke overskrives fra testene.
                MDC.put("X-Correlation-ID", "correlationId")
                // https://fuel.gitbook.io/documentation/core/fuel
                FuelManager.instance.forceMethods = true
            }
        }
    }

    @BeforeEach
    fun wiremockBeforeEach() {
        wireMockServer.resetAll()
    }
}
