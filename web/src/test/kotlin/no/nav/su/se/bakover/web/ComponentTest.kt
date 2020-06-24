package no.nav.su.se.bakover.web

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.su.se.bakover.client.*
import no.nav.su.se.bakover.client.stubs.PersonOppslagStub
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

internal open class ComponentTest {
    internal var jwt = "Bearer is not set"

    internal val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
    internal val azureStub by lazy { AzureStub(wireMockServer) }

    fun buildClients(
            azure: OAuth = ClientBuilder.azure(wellknownUrl = "${wireMockServer.baseUrl()}$AZURE_WELL_KNOWN_URL"),
            personOppslag: PersonOppslag = PersonOppslagStub,
            inntektOppslag: InntektOppslag = ClientBuilder.inntekt(baseUrl = wireMockServer.baseUrl(), oAuth = azure, personOppslag = personOppslag)
    ): Clients {
        return ClientBuilder.build(azure, personOppslag, inntektOppslag)
    }

    @BeforeEach
    fun start() {
        wireMockServer.start()
        WireMock.stubFor(azureStub.jwkProvider())
        WireMock.stubFor(azureStub.config())
        WireMock.stubFor(azureStub.onBehalfOfToken())
        WireMock.stubFor(azureStub.token())
        jwt = Jwt.create(azureStub)
    }

    @AfterEach
    fun stop() {
        wireMockServer.stop()
    }
}