package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingIkkeVurdert
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.journalførtSøknadMedOppgave
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.sakId
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.stønadsperiode
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.søknadId
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
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
            saksnummer = Saksnummer(2021),
            søknad = journalførtSøknadMedOppgave,
            oppgaveId = OppgaveId("oppgaveId"),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            fnr = Fnr.generer(),
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdert(),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående.kanIkke(),
        )
        val saksbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { opprett(any()) } doReturn søknadsbehandling.right()
        }

        testApplication {
            application {
                testSusebakover(services = services.copy(søknadsbehandling = saksbehandlingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                requestPath,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "soknadId": "$søknadId", "stønadsperiode": {"fraOgMed" : "${stønadsperiode.periode.fraOgMed}", "tilOgMed": "${stønadsperiode.periode.tilOgMed}"}}""".trimIndent())
            }.apply {
                status shouldBe HttpStatusCode.Created
                val actualResponse = objectMapper.readValue<BehandlingJson>(bodyAsText())
                verify(saksbehandlingServiceMock).opprett(argThat { it shouldBe SøknadsbehandlingService.OpprettRequest(søknadId) })
                verifyNoMoreInteractions(saksbehandlingServiceMock)
                actualResponse.søknad.id shouldBe søknadId.toString()
            }
        }
    }
}
