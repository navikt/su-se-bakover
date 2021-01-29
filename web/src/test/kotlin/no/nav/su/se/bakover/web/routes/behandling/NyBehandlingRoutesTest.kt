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
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
import no.nav.su.se.bakover.service.OpprettSøknadsbehandlingRequest
import no.nav.su.se.bakover.service.SaksbehandlingService
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.behandling.BehandlingTestUtils.nySøknadsbehandling
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
        statistikk = mock(),
        saksbehandling = mock()
    )

    @Test
    fun `kan opprette behandling`() {
        val søknadsbehandling: Søknadsbehandling = nySøknadsbehandling()
        val saksbehandlingServiceMock = mock<SaksbehandlingService> {
            on { opprett(any()) } doReturn søknadsbehandling.right()
        }

        withTestApplication({
            testSusebakover(services = services.copy(saksbehandling = saksbehandlingServiceMock))
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
                verify(saksbehandlingServiceMock).opprett(argThat { it shouldBe OpprettSøknadsbehandlingRequest(søknadId) })
                verifyNoMoreInteractions(saksbehandlingServiceMock)
                actualResponse.søknad.id shouldBe søknadId.toString()
            }
        }
    }
}
