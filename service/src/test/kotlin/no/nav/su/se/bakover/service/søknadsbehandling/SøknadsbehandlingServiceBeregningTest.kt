package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.tidspunkt
import no.nav.su.se.bakover.service.beregning.BeregningService
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit
import java.util.UUID

class SøknadsbehandlingServiceBeregningTest {
    private val sakId = UUID.randomUUID()
    private val behandlingId = UUID.randomUUID()
    private val stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.desember(2021)))
    private val vilkårsvurdertBehandling = Søknadsbehandling.Vilkårsvurdert.Innvilget(
        id = UUID.randomUUID(),
        opprettet = tidspunkt,
        sakId = sakId,
        saksnummer = Saksnummer(2021),
        søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = OppgaveId("o"),
            journalpostId = JournalpostId("j"),
        ),
        oppgaveId = OppgaveId("o"),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
        fnr = FnrGenerator.random(),
        fritekstTilBrev = "",
        stønadsperiode = stønadsperiode,
        grunnlagsdata = Grunnlagsdata(
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = stønadsperiode.periode,
                    begrunnelse = null,
                ),
            ),
        ),
        vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
    )

    @Test
    fun `oppretter beregning`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn vilkårsvurdertBehandling
        }
        val beregningServiceMock = mock<BeregningService> {
            on { beregn(any(), any(), any()) } doReturn TestBeregning
        }

        val fradragRequest = SøknadsbehandlingService.BeregnRequest.FradragRequest(
            periode = stønadsperiode.periode,
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        val request = SøknadsbehandlingService.BeregnRequest(
            behandlingId = behandlingId,
            fradrag = listOf(
                fradragRequest,
            ),
            begrunnelse = "her er en begrunnelse",
        )

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            beregningService = beregningServiceMock,
        ).beregn(
            request,
        )

        val expected = Søknadsbehandling.Beregnet.Innvilget(
            id = vilkårsvurdertBehandling.id,
            opprettet = vilkårsvurdertBehandling.opprettet,
            behandlingsinformasjon = vilkårsvurdertBehandling.behandlingsinformasjon,
            søknad = vilkårsvurdertBehandling.søknad,
            sakId = vilkårsvurdertBehandling.sakId,
            saksnummer = vilkårsvurdertBehandling.saksnummer,
            fnr = vilkårsvurdertBehandling.fnr,
            oppgaveId = vilkårsvurdertBehandling.oppgaveId,
            beregning = TestBeregning,
            fritekstTilBrev = "",
            stønadsperiode = vilkårsvurdertBehandling.stønadsperiode,
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = response.orNull()!!.grunnlagsdata.bosituasjon[0].id,
                        opprettet = fixedTidspunkt,
                        periode = vilkårsvurdertBehandling.stønadsperiode.periode,
                        begrunnelse = null,
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        )

        response shouldBe expected.right()

        inOrder(søknadsbehandlingRepoMock, beregningServiceMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
            verify(beregningServiceMock).beregn(
                søknadsbehandling = argThat { it shouldBe vilkårsvurdertBehandling },
                fradrag = argThat {
                    it shouldBe listOf(
                        FradragFactory.ny(
                            type = fradragRequest.type,
                            månedsbeløp = fradragRequest.månedsbeløp,
                            periode = fradragRequest.periode!!,
                            utenlandskInntekt = fradragRequest.utenlandskInntekt,
                            tilhører = fradragRequest.tilhører,
                        ),
                    )
                },
                begrunnelse = argThat { it shouldBe "her er en begrunnelse" },
            )
            verify(søknadsbehandlingRepoMock).lagre(expected)
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `fradragsperiode kan ikke være utenfor stønadsperioden`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn vilkårsvurdertBehandling
        }
        val beregningServiceMock = mock<BeregningService>()
        val request = SøknadsbehandlingService.BeregnRequest(
            behandlingId = behandlingId,
            fradrag = listOf(
                SøknadsbehandlingService.BeregnRequest.FradragRequest(
                    periode = stønadsperiode.periode.copy(
                        fraOgMed = stønadsperiode.periode.fraOgMed.minus(
                            1,
                            ChronoUnit.MONTHS,
                        ),
                    ),
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 12000.0,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            begrunnelse = "her er en begrunnelse",
        )

        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            beregningService = beregningServiceMock,
        ).beregn(request) shouldBe SøknadsbehandlingService.KunneIkkeBeregne.IkkeLovMedFradragUtenforPerioden.left()

        inOrder(søknadsbehandlingRepoMock, beregningServiceMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, beregningServiceMock)
    }

    @Test
    fun `kan ikke hente behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo>()

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).beregn(
            SøknadsbehandlingService.BeregnRequest(
                behandlingId = behandlingId,
                fradrag = emptyList(),
                begrunnelse = null,
            ),
        )

        response shouldBe SøknadsbehandlingService.KunneIkkeBeregne.FantIkkeBehandling.left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }
}
