package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.august
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.november
import no.nav.su.se.bakover.common.periode.oktober
import no.nav.su.se.bakover.common.periode.september
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUføre
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

class SøknadsbehandlingServiceBeregningTest {

    @Test
    fun `oppretter beregning`() {
        val vilkårsvurdert = søknadsbehandlingVilkårsvurdertInnvilget().let { (_, vilkårsvurdert) ->
            vilkårsvurdert.leggTilFradragsgrunnlag(
                fradragsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 12000.0,
                            periode = vilkårsvurdert.periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
            ).getOrFail()
        }

        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn vilkårsvurdert
            },
            avkortingsvarselRepo = mock {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
        ).let {
            val beregnet = it.søknadsbehandlingService.beregn(
                SøknadsbehandlingService.BeregnRequest(
                    behandlingId = vilkårsvurdert.id,
                    begrunnelse = "koko",
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

            verify(it.søknadsbehandlingRepo).hent(vilkårsvurdert.id)
            verify(it.søknadsbehandlingRepo).defaultTransactionContext()
            verify(it.søknadsbehandlingRepo).lagre(eq(beregnet), anyOrNull())
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kan ikke hente behandling`() {
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn null
            },
        ).let {
            it.søknadsbehandlingService.beregn(
                SøknadsbehandlingService.BeregnRequest(
                    behandlingId = UUID.randomUUID(),
                    begrunnelse = null,
                ),
            ) shouldBe SøknadsbehandlingService.KunneIkkeBeregne.FantIkkeBehandling.left()

            verify(it.søknadsbehandlingRepo).hent(any())
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `håndtering av utestående avkorting for innvilget beregning`() {
        val uteståendeAvkorting = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
            objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                sakId = UUID.randomUUID(),
                revurderingId = UUID.randomUUID(),
                simulering = simuleringFeilutbetaling(
                    juni(2021),
                ),
            ),
        )

        val (_, vilkårsvurdert) = søknadsbehandlingVilkårsvurdertInnvilget(
            avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
                avkortingsvarsel = uteståendeAvkorting,
            ).kanIkke(),
        )

        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn vilkårsvurdert
            },
        ).let {
            vilkårsvurdert.avkorting shouldBe beOfType<AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting>()

            val beregnet = it.søknadsbehandlingService.beregn(
                request = SøknadsbehandlingService.BeregnRequest(
                    behandlingId = vilkårsvurdert.id,
                    begrunnelse = "du skal avkortes",
                ),
            ).getOrFail()

            beregnet shouldBe beOfType<Søknadsbehandling.Beregnet.Innvilget>()
            beregnet.avkorting shouldBe AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående(
                avkortingsvarsel = uteståendeAvkorting,
            )
            beregnet.grunnlagsdata shouldNotBe vilkårsvurdert.grunnlagsdata
            beregnet.grunnlagsdata.fradragsgrunnlag
                .any { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold } shouldBe true
            beregnet.beregning.getFradrag()
                .any { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold } shouldBe true

            verify(it.søknadsbehandlingRepo).hent(vilkårsvurdert.id)
            verify(it.søknadsbehandlingRepo).defaultTransactionContext()
            verify(it.søknadsbehandlingRepo).lagre(eq(beregnet), anyOrNull())
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `håndtering av utestående avkorting for avslag beregning`() {
        val uteståendeAvkorting = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
            objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                sakId = UUID.randomUUID(),
                revurderingId = UUID.randomUUID(),
                simulering = simuleringFeilutbetaling(
                    juni(2021),
                ),
            ),
        )

        val (_, vilkårsvurdert) = søknadsbehandlingVilkårsvurdertInnvilget(
            avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
                uteståendeAvkorting,
            ),
        ).let { (sak, vilkårsvurdert) ->
            sak to vilkårsvurdert.leggTilFradragsgrunnlag(
                fradragsgrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = vilkårsvurdert.periode.måneder().first(),
                        arbeidsinntekt = 25000.0,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ).getOrFail()
        }

        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn vilkårsvurdert
            },
        ).let {
            vilkårsvurdert.avkorting shouldBe beOfType<AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting>()

            val beregnet = it.søknadsbehandlingService.beregn(
                request = SøknadsbehandlingService.BeregnRequest(
                    behandlingId = vilkårsvurdert.id,
                    begrunnelse = "du skal avkortes",
                ),
            ).getOrFail()

            beregnet shouldBe beOfType<Søknadsbehandling.Beregnet.Avslag>()
            beregnet.avkorting shouldBe AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere(
                håndtert = AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående(
                    avkortingsvarsel = uteståendeAvkorting,
                ),
            )
            beregnet.grunnlagsdata shouldNotBe vilkårsvurdert.grunnlagsdata
            beregnet.grunnlagsdata.fradragsgrunnlag
                .any { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold } shouldBe true
            beregnet.beregning.getFradrag()
                .any { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold } shouldBe true

            verify(it.søknadsbehandlingRepo).hent(vilkårsvurdert.id)
            verify(it.søknadsbehandlingRepo).defaultTransactionContext()
            verify(it.søknadsbehandlingRepo).lagre(eq(beregnet), anyOrNull())
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `beregner med flere forskjellige IEU innenfor samme beregning`() {
        val janAprilIEU = 24000
        val maiDesIEU = 48000

        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn søknadsbehandlingVilkårsvurdertInnvilget().let { (_, innvilget) ->
                    innvilget.leggTilUførevilkår(
                        uførhet = UføreVilkår.Vurdert.create(
                            vurderingsperioder = nonEmptyListOf(
                                VurderingsperiodeUføre.create(
                                    id = UUID.randomUUID(),
                                    opprettet = fixedTidspunkt,
                                    vurdering = Vurdering.Innvilget,
                                    grunnlag = Grunnlag.Uføregrunnlag(
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
                                    grunnlag = Grunnlag.Uføregrunnlag(
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
                    ).getOrFail()
                }
            },
        ).let {
            it.søknadsbehandlingService.beregn(
                request = SøknadsbehandlingService.BeregnRequest(
                    behandlingId = UUID.randomUUID(),
                    begrunnelse = "god",
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
