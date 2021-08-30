package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.behandlingsinformasjon
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.fixedTidspunkt
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag
import no.nav.su.se.bakover.service.vilkår.BosituasjonValg
import no.nav.su.se.bakover.service.vilkår.FullførBosituasjonRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class SøknadsbehandlingServiceGrunnlagBosituasjonTest {

    private val sakId = UUID.randomUUID()
    private val behandlingId = UUID.randomUUID()
    private val oppgaveId = OppgaveId("o")
    private val stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.desember(2021)))

    @Test
    fun `ufullstendig svarer med feil hvis man ikke finner behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val request = LeggTilBosituasjonEpsRequest(
            behandlingId = behandlingId,
            epsFnr = null,
        )

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).leggTilBosituasjonEpsgrunnlag(request)

        response shouldBe KunneIkkeLeggeTilBosituasjonEpsGrunnlag.FantIkkeBehandling.left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `ufullstendig gir error hvis behandling er i ugyldig tilstand`() {
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
            fnr = Fnr.generer(),
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            attesteringer = Attesteringshistorikk.empty(),
        ).tilAttestering(Saksbehandler("saksa"), "")

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn tilAttestering
        }

        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).leggTilBosituasjonEpsgrunnlag(
            LeggTilBosituasjonEpsRequest(behandlingId = behandlingId, epsFnr = null),
        ) shouldBe KunneIkkeLeggeTilBosituasjonEpsGrunnlag.UgyldigTilstand(
            fra = Søknadsbehandling.TilAttestering.Avslag.UtenBeregning::class,
            til = Søknadsbehandling.Vilkårsvurdert::class,
        ).left()
    }

    @Test
    fun `ufullstendig happy case`() {
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
            fnr = Fnr.generer(),
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            attesteringer = Attesteringshistorikk.empty(),
        )

        val bosituasjon = Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = stønadsperiode.periode,
        )

        val expected = uavklart.copy(
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(
                    bosituasjon,
                ),
            ),
            behandlingsinformasjon = uavklart.behandlingsinformasjon.copy(
                bosituasjon = Behandlingsinformasjon.Bosituasjon(
                    ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle,
                    delerBolig = null,
                    ektemakeEllerSamboerUførFlyktning = null,
                    begrunnelse = null,
                ),
                ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle,
            ),
        )

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturnConsecutively listOf(
                uavklart,
                uavklart,
                expected,
            )
        }

        val grunnlagServiceMock = mock<GrunnlagService>()

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            grunnlagService = grunnlagServiceMock,
            clock = fixedClock,
        ).leggTilBosituasjonEpsgrunnlag(
            LeggTilBosituasjonEpsRequest(behandlingId = behandlingId, epsFnr = null),
        ).orNull()!!

        response shouldBe expected.copy(
            grunnlagsdata = expected.grunnlagsdata.copy(
                bosituasjon = listOf(
                    bosituasjon.copy(
                        id = (response as Søknadsbehandling.Vilkårsvurdert).grunnlagsdata.bosituasjon.first().id,
                    ),
                ),
            ),
        )

        verify(søknadsbehandlingRepoMock, Times(2)).hent(argThat { it shouldBe behandlingId })
        verify(søknadsbehandlingRepoMock).lagre((any())) // Testene til søknadsbehandling vilkårsvurder dekker dette
        verify(grunnlagServiceMock).lagreBosituasjongrunnlag(
            argThat { it shouldBe behandlingId },
            argThat { it shouldBe listOf(bosituasjon.copy(id = it.first().id, opprettet = it.first().opprettet)) },
        )
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, grunnlagServiceMock)
    }

    @Test
    fun `fullfør bosituasjon svarer med feil hvis man ikke finner behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val request = FullførBosituasjonRequest(
            behandlingId = behandlingId,
            bosituasjon = BosituasjonValg.BOR_ALENE,
            begrunnelse = "begrunnelse",
        )

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).fullførBosituasjongrunnlag(request)

        response shouldBe KunneIkkeFullføreBosituasjonGrunnlag.FantIkkeBehandling.left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `fullfør gir error hvis behandling er i ugyldig tilstand`() {
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
            fnr = Fnr.generer(),
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            attesteringer = Attesteringshistorikk.empty(),
        ).tilAttestering(Saksbehandler("saksa"), "")

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn tilAttestering
        }

        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).fullførBosituasjongrunnlag(
            FullførBosituasjonRequest(
                behandlingId = behandlingId,
                bosituasjon = BosituasjonValg.BOR_ALENE,
                "begrunnelse",
            ),
        ) shouldBe KunneIkkeFullføreBosituasjonGrunnlag.UgyldigTilstand(
            fra = Søknadsbehandling.TilAttestering.Avslag.UtenBeregning::class,
            til = Søknadsbehandling.Vilkårsvurdert::class,
        ).left()
    }

    @Test
    fun `fullfør happy case`() {
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
            fnr = Fnr.generer(),
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = stønadsperiode.periode,
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            attesteringer = Attesteringshistorikk.empty(),
        )

        val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = stønadsperiode.periode,
            begrunnelse = "begrunnelse",
        )

        val expected = uavklart.copy(
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(
                    bosituasjon,
                ),
            ),
            behandlingsinformasjon = uavklart.behandlingsinformasjon.copy(
                bosituasjon = Behandlingsinformasjon.Bosituasjon(
                    ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle,
                    delerBolig = false,
                    ektemakeEllerSamboerUførFlyktning = null,
                    begrunnelse = "begrunnelse",
                ),
                ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle,
            ),
        )

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn uavklart
            on { hent(any()) } doReturnConsecutively listOf(
                uavklart,
                expected,
            )
        }

        val grunnlagServiceMock = mock<GrunnlagService>()

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            grunnlagService = grunnlagServiceMock,
            clock = fixedClock,
        ).fullførBosituasjongrunnlag(
            FullførBosituasjonRequest(
                behandlingId = behandlingId,
                bosituasjon = BosituasjonValg.BOR_ALENE,
                begrunnelse = "begrunnelse",
            ),
        ).orNull()!!

        response shouldBe expected.copy(
            grunnlagsdata = expected.grunnlagsdata.copy(
                bosituasjon = listOf(
                    bosituasjon.copy(
                        id = (response as Søknadsbehandling.Vilkårsvurdert).grunnlagsdata.bosituasjon.first().id,
                    ),
                ),
            ),
        )

        verify(søknadsbehandlingRepoMock, Times(2)).hent(argThat { it shouldBe behandlingId })
        verify(søknadsbehandlingRepoMock).lagre((any()))
        verify(grunnlagServiceMock).lagreBosituasjongrunnlag(
            argThat { it shouldBe behandlingId },
            argThat { it shouldBe listOf(bosituasjon.copy(id = it.first().id, opprettet = it.first().opprettet)) },
        )
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, grunnlagServiceMock)
    }
}
