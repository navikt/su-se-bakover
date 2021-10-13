package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.beregning.BeregningMedFradragBeregnetMånedsvis
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.IkkePeriodisertFradrag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.tidspunkt
import no.nav.su.se.bakover.service.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
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
        søknad = Søknad.Journalført.MedOppgave.IkkeLukket(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = OppgaveId("o"),
            journalpostId = JournalpostId("j"),
        ),
        oppgaveId = OppgaveId("o"),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
        fnr = Fnr.generer(),
        fritekstTilBrev = "",
        stønadsperiode = stønadsperiode,
        grunnlagsdata = Grunnlagsdata.create(
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = stønadsperiode.periode,
                    begrunnelse = null,
                ),
            ),
            fradragsgrunnlag = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 12000.0,
                    periode = stønadsperiode.periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
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
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = vilkårsvurdertBehandling.grunnlagsdata.bosituasjon,
                fradragsgrunnlag = listOf(
                    lagFradragsgrunnlag(
                        type = fradrag.fradragstype,
                        månedsbeløp = fradrag.månedsbeløp,
                        periode = fradrag.periode,
                        utenlandskInntekt = fradrag.utenlandskInntekt,
                        tilhører = fradrag.tilhører,
                    ),
                ),
            ),
        )

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val request = SøknadsbehandlingService.BeregnRequest(
            behandlingId = behandlingId,
            begrunnelse = "her er en begrunnelse",
        )

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
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
            beregning = BeregningMedFradragBeregnetMånedsvis(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = stønadsperiode2021.periode,
                sats = Sats.HØY,
                fradrag = listOf(
                    IkkePeriodisertFradrag(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 12000.0,
                        periode = stønadsperiode2021.periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    IkkePeriodisertFradrag(
                        type = Fradragstype.ForventetInntekt,
                        månedsbeløp = 0.0,
                        periode = stønadsperiode2021.periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                fradragStrategy = FradragStrategy.Enslig,
                begrunnelse = "her er en begrunnelse",
            ),
            fritekstTilBrev = "",
            stønadsperiode = vilkårsvurdertBehandling.stønadsperiode,
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = response.orNull()!!.grunnlagsdata.bosituasjon[0].id,
                        opprettet = fixedTidspunkt,
                        periode = vilkårsvurdertBehandling.stønadsperiode.periode,
                        begrunnelse = null,
                    ),
                ),
                fradragsgrunnlag = listOf(
                    lagFradragsgrunnlag(
                        id = response.orNull()!!.grunnlagsdata.fradragsgrunnlag[0].id,
                        type = fradrag.fradragstype,
                        månedsbeløp = fradrag.månedsbeløp,
                        periode = fradrag.periode,
                        utenlandskInntekt = fradrag.utenlandskInntekt,
                        tilhører = fradrag.tilhører,
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            attesteringer = Attesteringshistorikk.empty(),
        )

        response shouldBe expected.right()

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
            verify(søknadsbehandlingRepoMock).defaultSessionContext()
            verify(søknadsbehandlingRepoMock).lagre(eq(expected), anyOrNull())
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
