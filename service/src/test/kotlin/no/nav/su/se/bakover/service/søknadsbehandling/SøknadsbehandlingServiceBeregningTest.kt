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
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
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
        grunnlagsdata = Grunnlagsdata.tryCreate(
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = stønadsperiode.periode,
                    begrunnelse = null,
                ),
            ),
            fradragsgrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 12000.0,
                        periode = stønadsperiode.periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ).orNull()!!,
            ),
        ),
        vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
        attesteringer = Attesteringshistorikk.empty(),
    )

    @Test
    fun `oppretter beregning`() {
        val fradrag = FradragFactory.ny(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 12000.0,
            periode = stønadsperiode.periode,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )

        val behandling = vilkårsvurdertBehandling.copy(
            grunnlagsdata = Grunnlagsdata.tryCreate(
                bosituasjon = vilkårsvurdertBehandling.grunnlagsdata.bosituasjon,
                fradragsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag.tryCreate(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = fradrag,
                    ).orNull()!!,
                ),
            ),
        )

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val beregningServiceMock = mock<BeregningService> {
            on { beregn(any(), any(), any()) } doReturn TestBeregning
        }

        val request = SøknadsbehandlingService.BeregnRequest(
            behandlingId = behandlingId,
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
            grunnlagsdata = Grunnlagsdata.tryCreate(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = response.orNull()!!.grunnlagsdata.bosituasjon[0].id,
                        opprettet = fixedTidspunkt,
                        periode = vilkårsvurdertBehandling.stønadsperiode.periode,
                        begrunnelse = null,
                    ),
                ),
                fradragsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag.tryCreate(
                        id = response.orNull()!!.grunnlagsdata.fradragsgrunnlag[0].id,
                        opprettet = fixedTidspunkt,
                        fradrag = fradrag,
                    ).orNull()!!,
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            attesteringer = Attesteringshistorikk.empty(),
        )

        response shouldBe expected.right()

        inOrder(søknadsbehandlingRepoMock, beregningServiceMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
            verify(beregningServiceMock).beregn(
                søknadsbehandling = argThat { it shouldBe behandling },
                fradrag = argThat {
                    it shouldBe listOf(
                        fradrag,
                    )
                },
                begrunnelse = argThat { it shouldBe "her er en begrunnelse" },
            )
            verify(søknadsbehandlingRepoMock).lagre(expected)
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `kan ikke hente behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo>()

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).beregn(
            SøknadsbehandlingService.BeregnRequest(
                behandlingId = behandlingId,
                begrunnelse = null,
            ),
        )

        response shouldBe SøknadsbehandlingService.KunneIkkeBeregne.FantIkkeBehandling.left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }
}
