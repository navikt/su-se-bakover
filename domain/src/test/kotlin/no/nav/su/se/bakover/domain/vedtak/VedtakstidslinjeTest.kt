package no.nav.su.se.bakover.domain.vedtak

import arrow.core.nonEmptyListOf
import behandling.domain.beregning.fradrag.FradragTilhører
import behandling.domain.beregning.fradrag.Fradragstype
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.oktober
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.oktober
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUføre
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.bosituasjonEpsOver67
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.createFromGrunnlag
import no.nav.su.se.bakover.test.empty
import no.nav.su.se.bakover.test.epsFnr
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.søknad.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vilkår.innvilgetFormueVilkår
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.lovligOppholdVilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.tilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdInnvilget
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VedtakstidslinjeTest {

    @Test
    fun `bevarer korrekte verdier ved kopiering for plassering på tidslinje - full kopi`() {
        val periode = år(2021)
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode,
            uføregrad = Uføregrad.parse(25),
            forventetInntekt = 100,
        )

        val uføreVurderingsperiode = VurderingsperiodeUføre.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            grunnlag = uføregrunnlag,
            periode = periode,
        )

        val fradragKontantstøtteEpsJanFeb = lagFradragsgrunnlag(
            type = Fradragstype.Kontantstøtte,
            månedsbeløp = 5000.0,
            periode = januar(2021)..februar(2021),
            tilhører = FradragTilhører.EPS,
        )

        val fradragArbeidsinntektBrukerJanTomOkt = lagFradragsgrunnlag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 1000.0,
            periode = januar(2021)..oktober(2021),
            tilhører = FradragTilhører.BRUKER,
        )

        val bosituasjonMedEps = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
            fnr = Fnr.generer(),
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode,
        )

        val formuevilkår = innvilgetFormueVilkår(
            periode = periode,
            bosituasjon = bosituasjonMedEps,
        )

        val (sak, _, vedtak) = iverksattSøknadsbehandlingUføre(
            stønadsperiode = Stønadsperiode.create(periode),
            customGrunnlag = listOf(
                bosituasjonMedEps,
                fradragKontantstøtteEpsJanFeb,
                fradragArbeidsinntektBrukerJanTomOkt,
            ),
            customVilkår = listOf(
                UføreVilkår.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        uføreVurderingsperiode,
                    ),
                ),
                formuevilkår,
                utenlandsoppholdInnvilget(periode = periode),
                lovligOppholdVilkårInnvilget(),
            ),
        )

        sak.vedtakstidslinje().also { tidslinje ->
            tidslinje!!.single().also { vedtakPåTidslinje ->
                vedtakPåTidslinje.copy(CopyArgs.Tidslinje.Full).let { kopi ->
                    kopi.opprettet shouldBe kopi.opprettet
                    kopi.periode shouldBe kopi.periode
                    kopi.vilkårsvurderinger.shouldBeType<Vilkårsvurderinger.Søknadsbehandling.Uføre>()
                        .let { vilkårsvurdering ->
                            vilkårsvurdering.uføre.grunnlag shouldHaveSize 1
                            vilkårsvurdering.uføre.grunnlag[0].let {
                                it.id shouldNotBe uføregrunnlag.id
                                it.periode shouldBe uføregrunnlag.periode
                                it.uføregrad shouldBe uføregrunnlag.uføregrad
                                it.forventetInntekt shouldBe uføregrunnlag.forventetInntekt
                            }
                            vilkårsvurdering.uføre.shouldBeType<UføreVilkår.Vurdert>()
                                .let { vilkårcopy ->
                                    vilkårcopy.vurderingsperioder shouldHaveSize 1
                                    vilkårcopy.vurderingsperioder[0].let { vurderingsperiodecopy ->
                                        vurderingsperiodecopy.id shouldNotBe uføreVurderingsperiode.id
                                        vurderingsperiodecopy.vurdering shouldBe uføreVurderingsperiode.vurdering
                                        vurderingsperiodecopy.periode shouldBe uføreVurderingsperiode.periode
                                        vurderingsperiodecopy.grunnlag!!.let {
                                            it.id shouldNotBe uføregrunnlag.id
                                            it.periode shouldBe uføregrunnlag.periode
                                            it.uføregrad shouldBe uføregrunnlag.uføregrad
                                            it.forventetInntekt shouldBe uføregrunnlag.forventetInntekt
                                        }
                                    }
                                }
                            vilkårsvurdering.formue.grunnlag shouldHaveSize 1
                            vilkårsvurdering.formue.grunnlag[0].let {
                                val expectedFormuegrunnlag = formuevilkår.grunnlag.first()
                                it.id shouldNotBe expectedFormuegrunnlag.id
                                it.periode shouldBe expectedFormuegrunnlag.periode
                                it.epsFormue shouldBe expectedFormuegrunnlag.epsFormue
                                it.søkersFormue shouldBe expectedFormuegrunnlag.søkersFormue
                            }
                            vilkårsvurdering.formue.shouldBeType<FormueVilkår.Vurdert>()
                                .let { vilkårcopy ->
                                    vilkårcopy.vurderingsperioder shouldHaveSize 1
                                    val expectedVurderingsperiode = formuevilkår.vurderingsperioder.first()
                                    vilkårcopy.vurderingsperioder[0].let { vurderingsperiodecopy ->
                                        vurderingsperiodecopy.id shouldNotBe expectedVurderingsperiode.id
                                        vurderingsperiodecopy.vurdering shouldBe expectedVurderingsperiode.vurdering
                                        vurderingsperiodecopy.periode shouldBe expectedVurderingsperiode.periode
                                        vurderingsperiodecopy.grunnlag.let {
                                            val expectedFormuegrunnlag = formuevilkår.grunnlag.first()
                                            it.id shouldNotBe expectedFormuegrunnlag.id
                                            it.periode shouldBe expectedFormuegrunnlag.periode
                                            it.epsFormue shouldBe expectedFormuegrunnlag.epsFormue
                                            it.søkersFormue shouldBe expectedFormuegrunnlag.søkersFormue
                                        }
                                    }
                                }

                            vilkårsvurdering.utenlandsopphold.shouldBeType<UtenlandsoppholdVilkår.Vurdert>()
                                .let { vilkårcopy ->
                                    vilkårcopy.vurderingsperioder shouldHaveSize 1
                                    vilkårcopy.vurderingsperioder[0].let { vurderingsperiodecopy ->
                                        val expectedVurderingsperiode = formuevilkår.vurderingsperioder.first()
                                        vurderingsperiodecopy.id shouldNotBe expectedVurderingsperiode.id
                                        vurderingsperiodecopy.vurdering shouldBe expectedVurderingsperiode.vurdering
                                        vurderingsperiodecopy.periode shouldBe expectedVurderingsperiode.periode
                                        vurderingsperiodecopy.grunnlag shouldBe null
                                    }
                                }

                            vilkårsvurdering.vurdering shouldBe Vilkårsvurderingsresultat.Innvilget(
                                setOf(
                                    vilkårsvurdering.uføre,
                                    vilkårsvurdering.formue,
                                    vilkårsvurdering.flyktning,
                                    vilkårsvurdering.lovligOpphold,
                                    vilkårsvurdering.fastOpphold,
                                    vilkårsvurdering.institusjonsopphold,
                                    vilkårsvurdering.utenlandsopphold,
                                    vilkårsvurdering.personligOppmøte,
                                    vilkårsvurdering.opplysningsplikt,
                                ),
                            )
                        }

                    kopi.grunnlagsdata.fradragsgrunnlag.first().fradrag shouldBe fradragKontantstøtteEpsJanFeb.fradrag
                    kopi.grunnlagsdata.fradragsgrunnlag.last().fradrag shouldBe fradragArbeidsinntektBrukerJanTomOkt.fradrag
                    kopi.originaltVedtak shouldBe vedtak
                }
            }
        }
    }

    @Test
    fun `bevarer korrekte verdier ved kopiering for plassering på tidslinje - ny periode`() {
        val tikkendeKlokke = TikkendeKlokke(fixedClock)
        val periode = år(2021)
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(tikkendeKlokke),
            periode = periode,
            uføregrad = Uføregrad.parse(25),
            forventetInntekt = 100,
        )

        val b1 = Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(tikkendeKlokke),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
        )

        val b2 = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(tikkendeKlokke),
            periode = Periode.create(fraOgMed = 1.juli(2021), tilOgMed = 31.desember(2021)),
            fnr = epsFnr,
        )

        val uføreVurderingsperiode = VurderingsperiodeUføre.create(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(tikkendeKlokke),
            vurdering = Vurdering.Innvilget,
            grunnlag = uføregrunnlag,
            periode = periode,
        )

        val f1 = lagFradragsgrunnlag(
            opprettet = Tidspunkt.now(tikkendeKlokke),
            type = Fradragstype.Kontantstøtte,
            månedsbeløp = 5000.0,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 28.februar(2021)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )

        val f2 = lagFradragsgrunnlag(
            opprettet = Tidspunkt.now(tikkendeKlokke),
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 1000.0,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.oktober(2021)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )

        val f3 = lagFradragsgrunnlag(
            opprettet = Tidspunkt.now(tikkendeKlokke),
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 2000.0,
            periode = Periode.create(fraOgMed = 1.juli(2021), tilOgMed = 31.desember(2021)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.EPS,
        )

        val formueVilkår = FormueVilkår.Vurdert.createFromGrunnlag(
            nonEmptyListOf(
                Formuegrunnlag.create(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(tikkendeKlokke),
                    periode = januar(2021)..juni(2021),
                    epsFormue = null,
                    søkersFormue = Formuegrunnlag.Verdier.empty().copy(verdiEiendommer = 1000),
                    bosituasjon = b1,
                    behandlingsPeriode = periode,
                ),
                Formuegrunnlag.create(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(tikkendeKlokke),
                    periode = juli(2021)..desember(2021),
                    epsFormue = Formuegrunnlag.Verdier.empty().copy(verdiEiendommer = 15000),
                    søkersFormue = Formuegrunnlag.Verdier.empty().copy(verdiEiendommer = 1000),
                    bosituasjon = b2,
                    behandlingsPeriode = periode,
                ),
            ),
        )

        val (sak, vedtak) = vedtakRevurdering(
            clock = tikkendeKlokke,
            stønadsperiode = Stønadsperiode.create(periode),
            vilkårOverrides = listOf(
                UføreVilkår.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        uføreVurderingsperiode,
                    ),
                ),
                formueVilkår,
                utenlandsoppholdInnvilget(periode = periode),
                tilstrekkeligDokumentert(periode = periode),
                lovligOppholdVilkårInnvilget(),
                institusjonsoppholdvilkårInnvilget(periode = periode),
            ),
            grunnlagsdataOverrides = listOf(
                b1,
                b2,
                f1,
                f2,
                f3,
            ),
        )

        sak.vedtakstidslinje()!!.krympTilPeriode(mai(2021)..juli(2021)).also { tidslinje ->
            tidslinje!!.single().also { vedtakPåTidslinje ->
                vedtakPåTidslinje.copy(CopyArgs.Tidslinje.NyPeriode(Periode.create(1.mai(2021), 31.juli(2021))))
                    .let { kopi ->
                        kopi.opprettet shouldBe vedtakPåTidslinje.opprettet
                        kopi.periode shouldBe Periode.create(1.mai(2021), 31.juli(2021))

                        kopi.vilkårsvurderinger.shouldBeType<Vilkårsvurderinger.Revurdering.Uføre>()
                            .let { vilkårsvurdering ->
                                vilkårsvurdering.uføre.grunnlag shouldHaveSize 1
                                vilkårsvurdering.uføre.grunnlag[0].let {
                                    it.id shouldNotBe uføregrunnlag.id
                                    it.periode shouldBe Periode.create(1.mai(2021), 31.juli(2021))
                                    it.uføregrad shouldBe uføregrunnlag.uføregrad
                                    it.forventetInntekt shouldBe uføregrunnlag.forventetInntekt
                                }

                                vilkårsvurdering.uføre.shouldBeType<UføreVilkår.Vurdert>()
                                    .let { vilkårcopy ->
                                        vilkårcopy.vurderingsperioder shouldHaveSize 1
                                        vilkårcopy.vurderingsperioder[0].let { vurderingsperiodecopy ->
                                            vurderingsperiodecopy.id shouldNotBe uføreVurderingsperiode.id
                                            vurderingsperiodecopy.vurdering shouldBe uføreVurderingsperiode.vurdering
                                            vurderingsperiodecopy.periode shouldBe Periode.create(
                                                1.mai(2021),
                                                31.juli(2021),
                                            )
                                            vurderingsperiodecopy.grunnlag!!.let {
                                                it.id shouldNotBe uføregrunnlag.id
                                                it.periode shouldBe Periode.create(1.mai(2021), 31.juli(2021))
                                                it.uføregrad shouldBe uføregrunnlag.uføregrad
                                                it.forventetInntekt shouldBe uføregrunnlag.forventetInntekt
                                            }
                                        }
                                    }
                                vilkårsvurdering.formue.shouldBeType<FormueVilkår.Vurdert>()
                                    .let { vilkårcopy ->
                                        vilkårcopy.vurderingsperioder shouldHaveSize 2
                                        vilkårsvurdering.formue.grunnlag shouldHaveSize 2

                                        vilkårcopy.vurderingsperioder[0].let { vurderingsperiodecopy ->
                                            val expectedVurderingsperiode = formueVilkår.vurderingsperioder.first()
                                            vurderingsperiodecopy.id shouldNotBe expectedVurderingsperiode.id
                                            vurderingsperiodecopy.vurdering shouldBe expectedVurderingsperiode.vurdering
                                            vurderingsperiodecopy.periode shouldBe Periode.create(
                                                1.mai(2021),
                                                30.juni(2021),
                                            )
                                            vurderingsperiodecopy.grunnlag.let {
                                                formueVilkår.grunnlag.map { it.id } shouldNotContain it.id
                                                it.periode shouldBe Periode.create(1.mai(2021), 30.juni(2021))
                                                it.epsFormue shouldBe null
                                                it.søkersFormue shouldBe Formuegrunnlag.Verdier.empty()
                                                    .copy(verdiEiendommer = 1000)
                                            }
                                        }

                                        vilkårcopy.vurderingsperioder[1].let { vurderingsperiodecopy ->
                                            val expectedVurderingsperiode = formueVilkår.vurderingsperioder.first()
                                            vurderingsperiodecopy.id shouldNotBe expectedVurderingsperiode.id
                                            vurderingsperiodecopy.vurdering shouldBe expectedVurderingsperiode.vurdering
                                            vurderingsperiodecopy.periode shouldBe Periode.create(
                                                1.juli(2021),
                                                31.juli(2021),
                                            )
                                            vurderingsperiodecopy.grunnlag.let {
                                                formueVilkår.grunnlag.map { it.id } shouldNotContain it.id
                                                it.periode shouldBe Periode.create(1.juli(2021), 31.juli(2021))
                                                it.epsFormue shouldBe Formuegrunnlag.Verdier.empty()
                                                    .copy(verdiEiendommer = 15000)
                                                it.søkersFormue shouldBe Formuegrunnlag.Verdier.empty()
                                                    .copy(verdiEiendommer = 1000)
                                            }
                                        }
                                    }

                                vilkårsvurdering.utenlandsopphold.shouldBeType<UtenlandsoppholdVilkår.Vurdert>()
                                    .let { vilkårcopy ->
                                        vilkårcopy.vurderingsperioder shouldHaveSize 1
                                        vilkårcopy.vurderingsperioder[0].let { vurderingsperiodecopy ->
                                            val expectedVurderingsperiode = formueVilkår.vurderingsperioder.first()
                                            vurderingsperiodecopy.id shouldNotBe expectedVurderingsperiode.id
                                            vurderingsperiodecopy.vurdering shouldBe expectedVurderingsperiode.vurdering
                                            vurderingsperiodecopy.periode shouldBe Periode.create(
                                                1.mai(2021),
                                                31.juli(2021),
                                            )
                                            vurderingsperiodecopy.grunnlag shouldBe null
                                        }
                                    }

                                vilkårsvurdering.vurdering shouldBe Vilkårsvurderingsresultat.Innvilget(
                                    setOf(
                                        vilkårsvurdering.uføre,
                                        vilkårsvurdering.formue,
                                        vilkårsvurdering.utenlandsopphold,
                                        vilkårsvurdering.opplysningsplikt,
                                        vilkårsvurdering.lovligOpphold,
                                        vilkårsvurdering.flyktning,
                                        vilkårsvurdering.fastOpphold,
                                        vilkårsvurdering.personligOppmøte,
                                        vilkårsvurdering.institusjonsopphold,
                                    ),
                                )
                            }
                        kopi.grunnlagsdata.bosituasjon.let {
                            it shouldHaveSize 2
                            it[0].shouldBeType<Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen>()
                                .let {
                                    it.id shouldNotBe b1.id
                                    it.periode shouldBe Periode.create(1.mai(2021), 30.juni(2021))
                                }
                            it[1].shouldBeType<Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre>()
                                .let {
                                    it.id shouldNotBe b1.id
                                    it.periode shouldBe juli(2021)
                                }
                        }
                        kopi.grunnlagsdata.fradragsgrunnlag.let { fradragCopy ->
                            fradragCopy shouldHaveSize 2
                            fradragCopy[0].let {
                                it.fradragstype shouldBe Fradragstype.Arbeidsinntekt
                                it.månedsbeløp shouldBe 1000.0
                                it.periode shouldBe Periode.create(1.mai(2021), 31.juli(2021))
                                it.utenlandskInntekt shouldBe null
                                it.tilhører shouldBe FradragTilhører.BRUKER
                            }
                            fradragCopy[1].let {
                                it.fradragstype shouldBe Fradragstype.Arbeidsinntekt
                                it.månedsbeløp shouldBe 2000.0
                                it.periode shouldBe juli(2021)
                                it.utenlandskInntekt shouldBe null
                                it.tilhører shouldBe FradragTilhører.EPS
                            }
                        }
                        kopi.originaltVedtak shouldBe vedtak
                    }
            }
        }
    }

    /**
     *  |––––-----| a
     *      |–––––| b
     *  |---|-----| resultat
     */
    @Test
    fun `lager tidslinje for flere vedtak`() {
        val (sak, _, første) = iverksattSøknadsbehandlingUføre(
            stønadsperiode = Stønadsperiode.create(år(2021)),
            customGrunnlag = listOf(
                bosituasjongrunnlagEnslig(periode = år(2021)),
                fradragsgrunnlagArbeidsinntekt(periode = år(2021), arbeidsinntekt = 5000.0),
            ),
        )

        val (sakMedToVedtak, andre) = vedtakRevurdering(
            revurderingsperiode = mai(2021)..desember(2021),
            sakOgVedtakSomKanRevurderes = sak to første as VedtakSomKanRevurderes,
            grunnlagsdataOverrides = listOf(
                bosituasjonEpsOver67(periode = mai(2021)..desember(2021)),
                fradragsgrunnlagArbeidsinntekt(periode = mai(2021)..desember(2021), arbeidsinntekt = 1000.0),
            ),
        )

        sakMedToVedtak.vedtakstidslinje().also { tidslinje ->
            tidslinje!! shouldHaveSize 2
            tidslinje.first().also { a ->
                val førstePeriode = januar(2021)..april(2021)
                a.periode shouldBe førstePeriode
                a.grunnlagsdata.bosituasjon.all { it.periode == førstePeriode } shouldBe true
                a.grunnlagsdata.bosituasjon.all { it is Grunnlag.Bosituasjon.Fullstendig.Enslig } shouldBe true
                a.grunnlagsdata.fradragsgrunnlag.all { it.periode == førstePeriode } shouldBe true
                a.grunnlagsdata.fradragsgrunnlag.all { it.månedsbeløp == 5000.0 } shouldBe true
                a.vilkårsvurderinger.vilkår.map { it.perioder.single() }.all { it == førstePeriode } shouldBe true
                a.vilkårsvurderinger.erLik(første.behandling.vilkårsvurderinger)
                a.originaltVedtak shouldBe første
            }
            tidslinje.last().also { b ->
                val senestePeriode = mai(2021)..desember(2021)
                b.periode shouldBe senestePeriode
                b.grunnlagsdata.bosituasjon.all { it.periode == senestePeriode } shouldBe true
                b.grunnlagsdata.bosituasjon.all { it is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre } shouldBe true
                b.grunnlagsdata.fradragsgrunnlag.all { it.periode == senestePeriode } shouldBe true
                b.grunnlagsdata.fradragsgrunnlag.all { it.månedsbeløp == 1000.0 } shouldBe true
                b.vilkårsvurderinger.vilkår.map { it.perioder.single() }.all { it == senestePeriode } shouldBe true
                b.vilkårsvurderinger.erLik(andre.behandling.vilkårsvurderinger)
                b.originaltVedtak shouldBe andre
            }
        }
    }

    @Test
    fun `vedtak som overskrives av nye er ikke synlige på tidslinjen`() {
        val tikkendeKlokke = TikkendeKlokke(fixedClock)
        val (sak, _, første) = iverksattSøknadsbehandlingUføre(
            clock = tikkendeKlokke,
            stønadsperiode = Stønadsperiode.create(år(2021)),
        )

        val (sakMedToVedtak, andre) = vedtakRevurdering(
            clock = tikkendeKlokke,
            revurderingsperiode = år(2021),
            sakOgVedtakSomKanRevurderes = sak to første as VedtakSomKanRevurderes,
        )

        sakMedToVedtak.vedtakstidslinje().also { tidslinje ->
            tidslinje!!.single().also {
                it.periode shouldBe år(2021)
                it.originaltVedtak shouldBe andre
            }
        }
    }

    @Test
    fun `stønadperiode x3 - stans over 2 stønadsperioder`() {
        val tikkendeKlokke = TikkendeKlokke()
        val (sakFørste, _, _) = iverksattSøknadsbehandlingUføre(
            clock = tikkendeKlokke,
            stønadsperiode = Stønadsperiode.create(år(2022)),
        )
        val (sakAndre, _, _) = iverksattSøknadsbehandlingUføre(
            clock = tikkendeKlokke,
            stønadsperiode = Stønadsperiode.create(år(2023)),
            sakOgSøknad = sakFørste to nySøknadJournalførtMedOppgave(
                sakId = sakFørste.id,
                fnr = sakFørste.fnr,
            ),
        )
        val (sakTredje, _, tredje) = iverksattSøknadsbehandlingUføre(
            clock = tikkendeKlokke,
            stønadsperiode = Stønadsperiode.create(januar(2024)..februar(2024)),
            sakOgSøknad = sakAndre to nySøknadJournalførtMedOppgave(
                sakId = sakFørste.id,
                fnr = sakFørste.fnr,
            ),
        )
        val (sakFjerde, _, _) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            // Kan bare stanse inneværende eller neste måned.
            clock = fixedClockAt(1.februar(2023)),
            sakOgVedtakSomKanRevurderes = sakTredje to tredje as VedtakSomKanRevurderes,
            periode = februar(2023)..februar(2024),
        )
        sakFjerde.vedtakstidslinje().also { tidslinje ->
            tidslinje!!.size shouldBe 3
            tidslinje[0].let {
                it.periode shouldBe år(2022)
                it.originaltVedtak.shouldBeType<VedtakInnvilgetSøknadsbehandling>()
            }
            tidslinje[1].let {
                it.periode shouldBe januar(2023)
                it.originaltVedtak.shouldBeType<VedtakInnvilgetSøknadsbehandling>()
            }
            tidslinje[2].let {
                it.periode shouldBe februar(2023)..februar(2024)
                it.originaltVedtak.shouldBeType<VedtakStansAvYtelse>()
            }
        }
    }
}
