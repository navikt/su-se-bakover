package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Nel
import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.StatusovergangVisitor
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.behandlingsinformasjon
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.fixedTidspunkt
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class SøknadsbehandlingServiceOppdaterStønadsperiodeTest {

    private val sakId = UUID.randomUUID()
    private val behandlingId = UUID.randomUUID()
    private val oppgaveId = OppgaveId("o")
    private val stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.desember(2021)))

    @Test
    fun `svarer med feil hvis man ikke finner behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).oppdaterStønadsperiode(
            SøknadsbehandlingService.OppdaterStønadsperiodeRequest(behandlingId, stønadsperiode),
        )

        response shouldBe SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode.FantIkkeBehandling.left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `kaster exception ved hvs søknadsbehandling er i ugyldig tilstand for oppdatering av stønadsperiode`() {
        val tilAttestering = Søknadsbehandling.Vilkårsvurdert.Avslag(
            id = behandlingId,
            opprettet = Tidspunkt.now(),
            sakId = sakId,
            saksnummer = Saksnummer(2021),
            søknad = Søknad.Journalført.MedOppgave(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.EPOCH,
                sakId = sakId,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                oppgaveId = oppgaveId,
                journalpostId = JournalpostId("j"),
            ),
            oppgaveId = oppgaveId,
            behandlingsinformasjon = behandlingsinformasjon,
            fnr = FnrGenerator.random(),
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        ).tilAttestering(Saksbehandler("saksa"), "")

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn tilAttestering
        }

        assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
            createSøknadsbehandlingService(
                søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            ).oppdaterStønadsperiode(
                SøknadsbehandlingService.OppdaterStønadsperiodeRequest(behandlingId, stønadsperiode),
            )
        }
    }

    @Test
    fun `happy case`() {
        val uavklart = Søknadsbehandling.Vilkårsvurdert.Uavklart(
            id = behandlingId,
            opprettet = Tidspunkt.now(),
            sakId = sakId,
            saksnummer = Saksnummer(2021),
            søknad = Søknad.Journalført.MedOppgave(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.EPOCH,
                sakId = sakId,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                oppgaveId = oppgaveId,
                journalpostId = JournalpostId("j"),
            ),
            oppgaveId = oppgaveId,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            fnr = FnrGenerator.random(),
            fritekstTilBrev = "",
            stønadsperiode = null,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        )
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn uavklart
        }

        val expected = uavklart.copy(
            stønadsperiode = stønadsperiode,
        )

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).oppdaterStønadsperiode(
            SøknadsbehandlingService.OppdaterStønadsperiodeRequest(behandlingId, stønadsperiode),
        )

        response shouldBe expected.right()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        verify(søknadsbehandlingRepoMock).lagre(argThat { it shouldBe expected })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `endrer stønadsperioden på behandling og lagrer ny periode på grunnlag og vilkårsvurdering`() {
        val vilkårsvurderingId = UUID.randomUUID()
        val grunnlagId = UUID.randomUUID()
        val uavklart = Søknadsbehandling.Vilkårsvurdert.Uavklart(
            id = behandlingId,
            opprettet = Tidspunkt.now(),
            sakId = sakId,
            saksnummer = Saksnummer(2021),
            søknad = Søknad.Journalført.MedOppgave(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.EPOCH,
                sakId = sakId,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                oppgaveId = oppgaveId,
                journalpostId = JournalpostId("j"),
            ),
            oppgaveId = oppgaveId,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            fnr = FnrGenerator.random(),
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            vilkårsvurderinger = createVilkårsvurdering(stønadsperiode.periode, vilkårsvurderingId, grunnlagId),
        )
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn uavklart
            on { lagre(any()) }.doNothing()
        }
        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService> {
            on { lagre(any(), any()) }.doNothing()
        }

        val nyPeriode = Periode.create(1.februar(2021), 31.mars(2021))
        val expected = uavklart.copy(
            vilkårsvurderinger = createVilkårsvurdering(nyPeriode, vilkårsvurderingId, grunnlagId),
            stønadsperiode = uavklart.stønadsperiode?.copy(periode = nyPeriode)
        )

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock
        ).oppdaterStønadsperiode(
            SøknadsbehandlingService.OppdaterStønadsperiodeRequest(behandlingId, uavklart.stønadsperiode!!.copy(periode = nyPeriode)),
        )

        response shouldBe expected.right()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        verify(søknadsbehandlingRepoMock).lagre(argThat { it shouldBe expected })
        verify(vilkårsvurderingServiceMock).lagre(argThat { it shouldBe behandlingId }, argThat { it shouldBe expected.vilkårsvurderinger })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, vilkårsvurderingServiceMock)
    }

    private fun createVilkårsvurdering(periode: Periode, vilkårsvurderingId: UUID, grunnlagId: UUID) = Vilkårsvurderinger(
        uføre = Vilkår.Vurdert.Uførhet.create(
            vurderingsperioder = Nel.of(
                Vurderingsperiode.Uføre.create(
                    id = vilkårsvurderingId,
                    opprettet = fixedTidspunkt,
                    resultat = Resultat.Innvilget,
                    grunnlag = Grunnlag.Uføregrunnlag(
                        id = grunnlagId,
                        opprettet = fixedTidspunkt,
                        periode = periode,
                        uføregrad = Uføregrad.parse(20),
                        forventetInntekt = 10,
                    ),
                    periode = periode,
                    begrunnelse = "ok2k",
                ),
            ),
        ),
    )
}
