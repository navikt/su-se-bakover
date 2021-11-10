package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.journalførtSøknadMedOppgave
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.stønadsperiode
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

class GrunnlagBosituasjonEpsSkjermetRoutesTest {

    private val services = TestServicesBuilder.services()
    private val fnr = Fnr.generer()
    private val søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart =
        Søknadsbehandling.Vilkårsvurdert.Uavklart(
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
            vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling.IkkeVurdert,
            attesteringer = Attesteringshistorikk.empty(),
        )
    private val epsGrunnlag: Grunnlag.Bosituasjon.Ufullstendig.HarEps = Grunnlag.Bosituasjon.Ufullstendig.HarEps(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        periode = søknadsbehandling.periode,
        fnr = fnr,
    )

    @Test
    fun `lagre EPS når EPS har kode 6`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilBosituasjonEpsgrunnlagSkjermet(any(), any()) } doReturn epsGrunnlag.right()
        }

        withTestApplication(
            {
                testSusebakover(services = services.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/eps/skjermet",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "epsFnr": "$fnr"}""".trimIndent())
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                response.content shouldBe """{"status": "OK"}"""
                verify(søknadsbehandlingServiceMock).leggTilBosituasjonEpsgrunnlagSkjermet(søknadsbehandling.id, fnr)
                verifyNoMoreInteractions(søknadsbehandlingServiceMock)
            }
        }
    }
}
