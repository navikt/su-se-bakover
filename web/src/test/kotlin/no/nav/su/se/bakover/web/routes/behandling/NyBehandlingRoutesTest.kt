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
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.behandling.BehandlingTestUtils.journalførtSøknadMedOppgave
import no.nav.su.se.bakover.web.routes.behandling.BehandlingTestUtils.sakId
import no.nav.su.se.bakover.web.routes.behandling.BehandlingTestUtils.søknadId
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.util.UUID

class NyBehandlingRoutesTest {

    private val requestPath = "$sakPath/$sakId/behandlinger"
    private val services = TestServicesBuilder.services()

    @Test
    fun `kan opprette behandling`() {
        val søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart = Søknadsbehandling.Vilkårsvurdert.Uavklart(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(0),
            søknad = journalførtSøknadMedOppgave,
            oppgaveId = OppgaveId("oppgaveId"),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            fnr = FnrGenerator.random(),
        )
        val saksbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { opprett(any()) } doReturn søknadsbehandling.right()
        }

        withTestApplication({
            testSusebakover(services = services.copy(søknadsbehandling = saksbehandlingServiceMock))
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
                verify(saksbehandlingServiceMock).opprett(argThat { it shouldBe SøknadsbehandlingService.OpprettRequest(søknadId) })
                verifyNoMoreInteractions(saksbehandlingServiceMock)
                actualResponse.søknad.id shouldBe søknadId.toString()
            }
        }
    }
}
