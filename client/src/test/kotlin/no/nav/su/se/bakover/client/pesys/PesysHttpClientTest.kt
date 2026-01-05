package no.nav.su.se.bakover.client.pesys

import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.ClientsConfig.PesysConfig
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class PesysHttpClientTest {

    @Test
    fun `hent aktørid inneholder errors`() {
        val adMock = mock<AzureAd> { on { getSystemToken(any()) } doReturn "systemtoken" }
        PesysConfig.createLocalConfig() // ?
        val client = PesysHttpClient(adMock, "url", "clientid")
        startedWireMockServerWithCorrelationId {
        }
    }
}
