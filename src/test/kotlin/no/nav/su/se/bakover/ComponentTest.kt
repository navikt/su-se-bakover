package no.nav.su.se.bakover

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

internal open class ComponentTest {
    internal var jwt = "Bearer is not set"

    internal val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
    internal val jwtStub by lazy { JwtStub(wireMockServer) }

    @BeforeEach
    fun start() {
        wireMockServer.start()
        WireMock.stubFor(jwtStub.stubbedJwkProvider())
        WireMock.stubFor(jwtStub.stubbedConfigProvider())
        WireMock.stubFor(jwtStub.stubbedOnBehalfOfToken())
        WireMock.stubFor(jwtStub.stubbedToken())
        jwt = "Bearer ${jwtStub.createTokenFor()}"
    }

    @AfterEach
    fun stop() {
        wireMockServer.stop()
    }
}