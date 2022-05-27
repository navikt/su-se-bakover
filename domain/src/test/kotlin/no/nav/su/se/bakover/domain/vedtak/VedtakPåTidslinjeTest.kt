package no.nav.su.se.bakover.domain.vedtak

import arrow.core.nonEmptyListOf
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.epsFnr
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.innvilgetFormueVilkår
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.tilstrekkeligDokumentert
import no.nav.su.se.bakover.test.utenlandsoppholdInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.UUID

internal class VedtakPåTidslinjeTest {

    @Test
    fun `bevarer korrekte verdier ved kopiering for plassering på tidslinje - full kopi`() {
        // TODO("endre denne testen til å bruke en faktisk")
        val originaltVedtak = mock<VedtakSomKanRevurderes.EndringIYtelse>()

        val periode = år(2021)
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode,
            uføregrad = Uføregrad.parse(25),
            forventetInntekt = 100,
        )

        val uføreVurderingsperiode = Vurderingsperiode.Uføre.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = uføregrunnlag,
            periode = periode,
        )

        val f1 = lagFradragsgrunnlag(
            type = Fradragstype.Kontantstøtte,
            månedsbeløp = 5000.0,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 28.februar(2021)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.EPS,
        )

        val f2 = lagFradragsgrunnlag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 1000.0,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.oktober(2021)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )

        val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
            fnr = Fnr.generer(),
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode,
        )

        val formuevilkår = innvilgetFormueVilkår(periode = periode)
        val original = VedtakSomKanRevurderes.VedtakPåTidslinje(
            opprettet = fixedTidspunkt,
            periode = periode,
            grunnlagsdata = Grunnlagsdata.create(
                fradragsgrunnlag = listOf(f1, f2),
                bosituasjon = listOf(bosituasjon),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        uføreVurderingsperiode,
                    ),
                ),
                formue = formuevilkår,
                utenlandsopphold = utenlandsoppholdInnvilget(periode = periode),
                opplysningsplikt = tilstrekkeligDokumentert(periode = periode),
            ),
            originaltVedtak = originaltVedtak,
        )
        original.copy(CopyArgs.Tidslinje.Full).let { vedtakPåTidslinje ->
            vedtakPåTidslinje.opprettet shouldBe original.opprettet
            vedtakPåTidslinje.periode shouldBe original.periode
            vedtakPåTidslinje.vilkårsvurderinger.shouldBeType<Vilkårsvurderinger.Revurdering>()
                .let { vilkårsvurdering ->
                    vilkårsvurdering.uføre.grunnlag shouldHaveSize 1
                    vilkårsvurdering.uføre.grunnlag[0].let {
                        it.id shouldNotBe uføregrunnlag.id
                        it.periode shouldBe uføregrunnlag.periode
                        it.uføregrad shouldBe uføregrunnlag.uføregrad
                        it.forventetInntekt shouldBe uføregrunnlag.forventetInntekt
                    }
                    vilkårsvurdering.uføre.shouldBeType<Vilkår.Uførhet.Vurdert>()
                        .let { vilkårcopy ->
                            vilkårcopy.vurderingsperioder shouldHaveSize 1
                            vilkårcopy.vurderingsperioder[0].let { vurderingsperiodecopy ->
                                vurderingsperiodecopy.id shouldNotBe uføreVurderingsperiode.id
                                vurderingsperiodecopy.resultat shouldBe uføreVurderingsperiode.resultat
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
                    vilkårsvurdering.formue.shouldBeType<Vilkår.Formue.Vurdert>()
                        .let { vilkårcopy ->
                            vilkårcopy.vurderingsperioder shouldHaveSize 1
                            val expectedVurderingsperiode = formuevilkår.vurderingsperioder.first()
                            vilkårcopy.vurderingsperioder[0].let { vurderingsperiodecopy ->
                                vurderingsperiodecopy.id shouldNotBe expectedVurderingsperiode.id
                                vurderingsperiodecopy.resultat shouldBe expectedVurderingsperiode.resultat
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
                                vurderingsperiodecopy.resultat shouldBe expectedVurderingsperiode.resultat
                                vurderingsperiodecopy.periode shouldBe expectedVurderingsperiode.periode
                                vurderingsperiodecopy.grunnlag shouldBe null
                            }
                        }

                    vilkårsvurdering.resultat shouldBe Vilkårsvurderingsresultat.Innvilget(
                        setOf(
                            vilkårsvurdering.uføre,
                            vilkårsvurdering.formue,
                            vilkårsvurdering.utenlandsopphold,
                            vilkårsvurdering.opplysningsplikt,
                        ),
                    )
                }

            vedtakPåTidslinje.grunnlagsdata.fradragsgrunnlag.first().fradrag shouldBe f1.fradrag
            vedtakPåTidslinje.grunnlagsdata.fradragsgrunnlag.last().fradrag shouldBe f2.fradrag
            vedtakPåTidslinje.originaltVedtak shouldBe originaltVedtak
        }
    }

    @Test
    fun `bevarer korrekte verdier ved kopiering for plassering på tidslinje - ny periode`() {
        // TODO("endre denne testen til å bruke en faktisk")
        val originaltVedtak = mock<VedtakSomKanRevurderes.EndringIYtelse>()

        val periode = år(2021)
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode,
            uføregrad = Uføregrad.parse(25),
            forventetInntekt = 100,
        )

        val b1 = Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
        )

        val b2 = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(fraOgMed = 1.juli(2021), tilOgMed = 31.desember(2021)),
            fnr = epsFnr,
        )

        val vurderingsperiode = Vurderingsperiode.Uføre.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = uføregrunnlag,
            periode = periode,
        )

        val f1 = lagFradragsgrunnlag(
            type = Fradragstype.Kontantstøtte,
            månedsbeløp = 5000.0,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 28.februar(2021)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )

        val f2 = lagFradragsgrunnlag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 1000.0,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.oktober(2021)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )

        val f3 = lagFradragsgrunnlag(
            type = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 2000.0,
            periode = Periode.create(fraOgMed = 1.juli(2021), tilOgMed = 31.desember(2021)),
            utenlandskInntekt = null,
            tilhører = FradragTilhører.EPS,
        )

        val formuevilkår = innvilgetFormueVilkår(periode = periode)

        val original = VedtakSomKanRevurderes.VedtakPåTidslinje(
            opprettet = fixedTidspunkt,
            periode = periode,
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = listOf(b1, b2),
                fradragsgrunnlag = listOf(f1, f2, f3),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        vurderingsperiode,
                    ),
                ),
                formue = formuevilkår,
                utenlandsopphold = utenlandsoppholdInnvilget(periode = periode),
                opplysningsplikt = tilstrekkeligDokumentert(periode = periode),
            ),
            originaltVedtak = originaltVedtak,
        )

        original.copy(CopyArgs.Tidslinje.NyPeriode(Periode.create(1.mai(2021), 31.juli(2021))))
            .let { vedtakPåTidslinje ->
                vedtakPåTidslinje.opprettet shouldBe original.opprettet
                vedtakPåTidslinje.periode shouldBe Periode.create(1.mai(2021), 31.juli(2021))

                vedtakPåTidslinje.vilkårsvurderinger.shouldBeType<Vilkårsvurderinger.Revurdering>()
                    .let { vilkårsvurdering ->
                        vilkårsvurdering.uføre.grunnlag shouldHaveSize 1
                        vilkårsvurdering.uføre.grunnlag[0].let {
                            it.id shouldNotBe uføregrunnlag.id
                            it.periode shouldBe Periode.create(1.mai(2021), 31.juli(2021))
                            it.uføregrad shouldBe uføregrunnlag.uføregrad
                            it.forventetInntekt shouldBe uføregrunnlag.forventetInntekt
                        }

                        vilkårsvurdering.formue.grunnlag shouldHaveSize 1
                        vilkårsvurdering.formue.grunnlag[0].let {
                            val expectedFormuegrunnlag = formuevilkår.grunnlag.first()
                            it.id shouldNotBe expectedFormuegrunnlag.id
                            it.periode shouldBe Periode.create(1.mai(2021), 31.juli(2021))
                            it.epsFormue shouldBe expectedFormuegrunnlag.epsFormue
                            it.søkersFormue shouldBe expectedFormuegrunnlag.søkersFormue
                        }
                        vilkårsvurdering.uføre.shouldBeType<Vilkår.Uførhet.Vurdert>()
                            .let { vilkårcopy ->
                                vilkårcopy.vurderingsperioder shouldHaveSize 1
                                vilkårcopy.vurderingsperioder[0].let { vurderingsperiodecopy ->
                                    vurderingsperiodecopy.id shouldNotBe vurderingsperiode.id
                                    vurderingsperiodecopy.resultat shouldBe vurderingsperiode.resultat
                                    vurderingsperiodecopy.periode shouldBe Periode.create(1.mai(2021), 31.juli(2021))
                                    vurderingsperiodecopy.grunnlag!!.let {
                                        it.id shouldNotBe uføregrunnlag.id
                                        it.periode shouldBe Periode.create(1.mai(2021), 31.juli(2021))
                                        it.uføregrad shouldBe uføregrunnlag.uføregrad
                                        it.forventetInntekt shouldBe uføregrunnlag.forventetInntekt
                                    }
                                }
                            }
                        vilkårsvurdering.formue.shouldBeType<Vilkår.Formue.Vurdert>()
                            .let { vilkårcopy ->
                                vilkårcopy.vurderingsperioder shouldHaveSize 1
                                vilkårcopy.vurderingsperioder[0].let { vurderingsperiodecopy ->
                                    val expectedVurderingsperiode = formuevilkår.vurderingsperioder.first()
                                    vurderingsperiodecopy.id shouldNotBe expectedVurderingsperiode.id
                                    vurderingsperiodecopy.resultat shouldBe expectedVurderingsperiode.resultat
                                    vurderingsperiodecopy.periode shouldBe Periode.create(1.mai(2021), 31.juli(2021))
                                    vurderingsperiodecopy.grunnlag.let {
                                        val expectedFormuegrunnlag = formuevilkår.grunnlag.first()
                                        it.id shouldNotBe expectedFormuegrunnlag.id
                                        it.periode shouldBe Periode.create(1.mai(2021), 31.juli(2021))
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
                                    vurderingsperiodecopy.resultat shouldBe expectedVurderingsperiode.resultat
                                    vurderingsperiodecopy.periode shouldBe Periode.create(1.mai(2021), 31.juli(2021))
                                    vurderingsperiodecopy.grunnlag shouldBe null
                                }
                            }

                        vilkårsvurdering.resultat shouldBe Vilkårsvurderingsresultat.Innvilget(
                            setOf(
                                vilkårsvurdering.uføre,
                                vilkårsvurdering.formue,
                                vilkårsvurdering.utenlandsopphold,
                                vilkårsvurdering.opplysningsplikt,
                            ),
                        )
                    }
                vedtakPåTidslinje.grunnlagsdata.bosituasjon.let {
                    it shouldHaveSize 2
                    it[0].shouldBeType<Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen>().let {
                        it.id shouldNotBe b1.id
                        it.periode shouldBe Periode.create(1.mai(2021), 30.juni(2021))
                    }
                    it[1].shouldBeType<Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre>()
                        .let {
                            it.id shouldNotBe b1.id
                            it.periode shouldBe juli(2021)
                        }
                }
                vedtakPåTidslinje.grunnlagsdata.fradragsgrunnlag.let { fradragCopy ->
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
                vedtakPåTidslinje.originaltVedtak shouldBe originaltVedtak
            }
    }
}
