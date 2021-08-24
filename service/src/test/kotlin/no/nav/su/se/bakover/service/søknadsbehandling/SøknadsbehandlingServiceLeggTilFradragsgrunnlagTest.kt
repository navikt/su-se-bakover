package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.getOrHandle
import arrow.core.left
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.søknadsbehandlingId
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.internal.verification.Times

class SøknadsbehandlingServiceLeggTilFradragsgrunnlagTest {
    @Test
    fun `lagreFradrag happy case`() {
        val behandling = søknadsbehandlingVilkårsvurdertInnvilget().second

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val grunnlagServiceMock = mock<GrunnlagService>()

        val søknadsbehandlingService = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            grunnlagService = grunnlagServiceMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = behandling.id,
            fradragsrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag.tryCreate(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 5000.0,
                        periode = behandling.periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    opprettet = fixedTidspunkt,
                ).orNull()!!,
            ),
        )

        søknadsbehandlingService.leggTilFradragGrunnlag(request).getOrHandle { fail { "uventet respons" } }

        verify(søknadsbehandlingRepoMock, Times(2)).hent(argThat { it shouldBe behandling.id })
        verify(grunnlagServiceMock).lagreFradragsgrunnlag(
            argThat { it shouldBe behandling.id },
            argThat { it shouldBe request.fradragsrunnlag },
        )
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, grunnlagServiceMock)
    }

    @Test
    fun `lagreFradrag finner ikke behandlingen`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val grunnlagServiceMock = mock<GrunnlagService>()

        val søknadsbehandlingService = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            grunnlagService = grunnlagServiceMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = søknadsbehandlingId,
            fradragsrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag.tryCreate(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 0.0,
                        periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    opprettet = fixedTidspunkt,
                ).orNull()!!,
            ),
        )

        søknadsbehandlingService.leggTilFradragGrunnlag(
            request,
        ) shouldBe SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling.left()

        inOrder(
            søknadsbehandlingRepoMock,
            grunnlagServiceMock,
        ) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe søknadsbehandlingId })
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, grunnlagServiceMock)
    }

    @Test
    fun `lagreFradrag har en status som gjør at man ikke kan legge til fradrag`() {

        val uavklart = søknadsbehandlingVilkårsvurdertUavklart().second

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn uavklart
        }

        val grunnlagServiceMock = mock<GrunnlagService>()

        val søknadsbehandlingService = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            grunnlagService = grunnlagServiceMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = uavklart.id,
            fradragsrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag.tryCreate(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 0.0,
                        periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    opprettet = fixedTidspunkt,
                ).orNull()!!,
            ),
        )

        søknadsbehandlingService.leggTilFradragGrunnlag(
            request,
        ) shouldBe SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand(
            fra = uavklart::class,
        ).left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe uavklart.id })

        verifyNoMoreInteractions(søknadsbehandlingRepoMock, grunnlagServiceMock)
    }

    @Test
    fun `lagreFradrag har et fradrag med en periode som er utenfor behandlingen sin periode`() {

        val behandling = søknadsbehandlingVilkårsvurdertInnvilget().second

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val grunnlagServiceMock = mock<GrunnlagService>()

        val søknadsbehandlingService = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            grunnlagService = grunnlagServiceMock,
        )

        val request = LeggTilFradragsgrunnlagRequest(
            behandlingId = behandling.id,
            fradragsrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag.tryCreate(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 0.0,
                        periode = Periode.create(
                            fraOgMed = behandling.periode.fraOgMed.minusMonths(3),
                            tilOgMed = behandling.periode.tilOgMed,
                        ),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    opprettet = fixedTidspunkt,
                ).orNull()!!,
            ),
        )

        søknadsbehandlingService.leggTilFradragGrunnlag(
            request,
        ) shouldBe SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag.FradragsgrunnlagUtenforPeriode.left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandling.id })

        verifyNoMoreInteractions(søknadsbehandlingRepoMock, grunnlagServiceMock)
    }
}
