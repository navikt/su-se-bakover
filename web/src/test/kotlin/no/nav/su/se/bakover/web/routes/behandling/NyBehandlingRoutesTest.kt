package no.nav.su.se.bakover.web.routes.behandling

import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.behandling.BehandlingTestUtils.nyBehandling
import no.nav.su.se.bakover.web.routes.behandling.BehandlingTestUtils.sakId
import no.nav.su.se.bakover.web.routes.behandling.BehandlingTestUtils.søknadId
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test

class NyBehandlingRoutesTest {

    private val requestPath = "$sakPath/$sakId/behandlinger"
    private val services = Services(
        avstemming = mock(),
        utbetaling = mock(),
        behandling = mock(),
        sak = mock(),
        søknad = mock(),
        brev = mock(),
        lukkSøknad = mock(),
        oppgave = mock(),
        person = mock(),
        statistikk = mock()
    )

    @Test
    fun `kan opprette behandling`() {
        val behandling: Behandling = nyBehandling()
        val behandlingServiceMock = mock<BehandlingService> {
            on { opprettSøknadsbehandling(any()) } doReturn behandling.right()
        }

        withTestApplication({
            testSusebakover(services = services.copy(behandling = behandlingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                requestPath,
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody("""{ "soknadId": "$søknadId" }""")
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                val actualResponse = objectMapper.readValue<BehandlingJson>(response.content!!)
                verify(behandlingServiceMock).opprettSøknadsbehandling(argThat { it shouldBe søknadId })
                verifyNoMoreInteractions(behandlingServiceMock)
                actualResponse.søknad.id shouldBe søknadId.toString()
            }
        }
    }
}
