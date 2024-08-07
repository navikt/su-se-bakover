package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.august
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.november
import no.nav.su.se.bakover.common.tid.periode.oktober
import no.nav.su.se.bakover.common.tid.periode.september
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandling
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import vilkår.common.domain.Vurdering
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.uføre.domain.UføreVilkår
import vilkår.uføre.domain.Uføregrad
import vilkår.uføre.domain.Uføregrunnlag
import vilkår.uføre.domain.VurderingsperiodeUføre
import java.util.UUID

class SøknadsbehandlingServiceBeregningTest {

    @Test
    fun `oppretter beregning`() {
        val (sak, vilkårsvurdert) = vilkårsvurdertSøknadsbehandling(
            customGrunnlag = nonEmptyListOf(
                Fradragsgrunnlag.create(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    fradrag = FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 12000.0,
                        periode = år(2021),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ),
        )

        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock(),
            sakService = mock {
                on { hentSakForSøknadsbehandling(any()) } doReturn sak
            },
        ).let {
            val beregnet = it.søknadsbehandlingService.beregn(
                SøknadsbehandlingService.BeregnRequest(
                    behandlingId = vilkårsvurdert.id,
                    begrunnelse = "koko",
                    saksbehandler = saksbehandler,
                ),
            ).getOrFail()

            beregnet.beregning.let { beregning ->
                beregning.getFradrag() shouldBe listOf(
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 12000.0,
                        periode = vilkårsvurdert.periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.ForventetInntekt,
                        månedsbeløp = 0.0,
                        periode = vilkårsvurdert.periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                )
                beregning.getBegrunnelse() shouldBe "koko"
            }

            verify(it.sakService).hentSakForSøknadsbehandling(vilkårsvurdert.id)
            verify(it.søknadsbehandlingRepo).defaultTransactionContext()
            verify(it.søknadsbehandlingRepo).lagre(eq(beregnet), anyOrNull())
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `beregner med flere forskjellige IEU innenfor samme beregning`() {
        val janAprilIEU = 24000
        val maiDesIEU = 48000

        val (sak, søknadsbehandling) = vilkårsvurdertSøknadsbehandling(
            customVilkår = listOf(
                UføreVilkår.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        VurderingsperiodeUføre.create(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            vurdering = Vurdering.Innvilget,
                            grunnlag = Uføregrunnlag(
                                id = UUID.randomUUID(),
                                opprettet = fixedTidspunkt,
                                periode = Periode.create(1.januar(2021), 30.april(2021)),
                                uføregrad = Uføregrad.parse(50),
                                forventetInntekt = janAprilIEU,
                            ),
                            periode = Periode.create(1.januar(2021), 30.april(2021)),
                        ),
                        VurderingsperiodeUføre.create(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            vurdering = Vurdering.Innvilget,
                            grunnlag = Uføregrunnlag(
                                id = UUID.randomUUID(),
                                opprettet = fixedTidspunkt,
                                periode = Periode.create(1.mai(2021), 31.desember(2021)),
                                uføregrad = Uføregrad.parse(50),
                                forventetInntekt = maiDesIEU,
                            ),
                            periode = Periode.create(1.mai(2021), 31.desember(2021)),
                        ),
                    ),
                ),
            ),
        )
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock(),
            sakService = mock {
                on { hentSakForSøknadsbehandling(any()) } doReturn sak
            },
        ).let {
            it.søknadsbehandlingService.beregn(
                request = SøknadsbehandlingService.BeregnRequest(
                    behandlingId = søknadsbehandling.id,
                    begrunnelse = "god",
                    saksbehandler = saksbehandler,
                ),
            ).getOrFail().let {
                it.beregning.getMånedsberegninger()
                    .associateBy { it.periode }
                    .let { periodeTilFradrag ->
                        val januarApril = janAprilIEU / 12.0
                        val maiDesember = maiDesIEU / 12.0
                        periodeTilFradrag[januar(2021)]!!.getSumFradrag() shouldBe (januarApril)
                        periodeTilFradrag[februar(2021)]!!.getSumFradrag() shouldBe (januarApril)
                        periodeTilFradrag[mars(2021)]!!.getSumFradrag() shouldBe (januarApril)
                        periodeTilFradrag[april(2021)]!!.getSumFradrag() shouldBe (januarApril)
                        periodeTilFradrag[mai(2021)]!!.getSumFradrag() shouldBe (maiDesember)
                        periodeTilFradrag[juni(2021)]!!.getSumFradrag() shouldBe (maiDesember)
                        periodeTilFradrag[juli(2021)]!!.getSumFradrag() shouldBe (maiDesember)
                        periodeTilFradrag[august(2021)]!!.getSumFradrag() shouldBe (maiDesember)
                        periodeTilFradrag[september(2021)]!!.getSumFradrag() shouldBe (maiDesember)
                        periodeTilFradrag[oktober(2021)]!!.getSumFradrag() shouldBe (maiDesember)
                        periodeTilFradrag[november(2021)]!!.getSumFradrag() shouldBe (maiDesember)
                        periodeTilFradrag[desember(2021)]!!.getSumFradrag() shouldBe (maiDesember)
                    }
            }
        }
    }
}
