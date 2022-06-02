package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.getOrHandle
import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

class SøknadsbehandlingServiceLeggTilFradragsgrunnlagTest {

    @Test
    fun `lagreFradrag happy case`() {
        val behandling = søknadsbehandlingVilkårsvurdertInnvilget().second

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val søknadsbehandlingService = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        )

        val fradragsgrunnlag = listOf(
            lagFradragsgrunnlag(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 5000.0,
                periode = behandling.periode,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = behandling.id,
            fradragsgrunnlag = fradragsgrunnlag,
        )

        val actual = søknadsbehandlingService.leggTilFradragsgrunnlag(request).getOrHandle { fail { "uventet respons" } }

        actual shouldBe Søknadsbehandling.Vilkårsvurdert.Innvilget(
            behandling.id,
            behandling.opprettet,
            behandling.sakId,
            behandling.saksnummer,
            behandling.søknad,
            behandling.oppgaveId,
            behandling.behandlingsinformasjon,
            behandling.fnr,
            behandling.fritekstTilBrev,
            behandling.stønadsperiode,
            grunnlagsdata = Grunnlagsdata.create(
                fradragsgrunnlag = fradragsgrunnlag,
                bosituasjon = behandling.grunnlagsdata.bosituasjon,
            ),
            behandling.vilkårsvurderinger,
            behandling.attesteringer,
            behandling.avkorting,
            behandling.sakstype,
        )

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandling.id })
        verify(søknadsbehandlingRepoMock).defaultTransactionContext()
        verify(søknadsbehandlingRepoMock).lagre(
            argThat {
                it shouldBe behandling.copy(grunnlagsdata = behandling.grunnlagsdata.copy(fradragsgrunnlag = fradragsgrunnlag))
            },
            anyOrNull()
        )
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `lagreFradrag finner ikke behandlingen`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val søknadsbehandlingService = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        )
        val søknadsbehandlingId = UUID.randomUUID()
        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = søknadsbehandlingId,
            fradragsgrunnlag = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 0.0,
                    periode = år(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        søknadsbehandlingService.leggTilFradragsgrunnlag(
            request,
        ) shouldBe SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling.left()

        inOrder(
            søknadsbehandlingRepoMock,
        ) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe søknadsbehandlingId })
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `lagreFradrag har en status som gjør at man ikke kan legge til fradrag`() {
        val uavklart = søknadsbehandlingVilkårsvurdertUavklart().second

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn uavklart
        }

        val søknadsbehandlingService = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = uavklart.id,
            fradragsgrunnlag = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 0.0,
                    periode = år(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        søknadsbehandlingService.leggTilFradragsgrunnlag(
            request,
        ) shouldBe SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand(
            fra = uavklart::class,
            til = Søknadsbehandling.Vilkårsvurdert.Innvilget::class
        ).left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe uavklart.id })

        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `lagreFradrag har et fradrag med en periode som er utenfor behandlingen sin periode`() {

        val behandling = søknadsbehandlingVilkårsvurdertInnvilget().second

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val søknadsbehandlingService = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = behandling.id,
            fradragsgrunnlag = listOf(
                lagFradragsgrunnlag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 0.0,
                    periode = Periode.create(
                        fraOgMed = behandling.periode.fraOgMed.minusMonths(3),
                        tilOgMed = behandling.periode.tilOgMed,
                    ),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        søknadsbehandlingService.leggTilFradragsgrunnlag(
            request,
        ) shouldBe SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag.GrunnlagetMåVæreInnenforBehandlingsperioden.left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandling.id })
    }
}
