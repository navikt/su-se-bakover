package no.nav.su.se.bakover.domain.vedtak

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.FnrGenerator
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.fixedClock
import no.nav.su.se.bakover.domain.fixedTidspunkt
import no.nav.su.se.bakover.domain.formueVilkår
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.test.assertEqualsIgnoreId
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.empty
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class VedtakTest {
    private fun lagUføreVurderingsperiode(
        id: UUID,
        vurderingsperiode: Periode,
        uføregrunnlagPeriode: Periode,
    ): Vurderingsperiode.Uføre {
        return Vurderingsperiode.Uføre.create(
            id = id,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = Grunnlag.Uføregrunnlag(
                id = id,
                opprettet = fixedTidspunkt,
                periode = uføregrunnlagPeriode,
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
            periode = vurderingsperiode,
            begrunnelse = null,
        )
    }

    private fun lagFormueVurderingsperiode(
        id: UUID,
        vurderingsperiode: Periode,
        formuegrunnlagPeriode: Periode,
        behandlingsperiode: Periode,
        bosituasjon: Grunnlag.Bosituasjon.Fullstendig,
    ): Vurderingsperiode.Formue {
        return Vurderingsperiode.Formue.create(
            id = id,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = Formuegrunnlag.create(
                id = id,
                opprettet = fixedTidspunkt,
                periode = formuegrunnlagPeriode,
                epsFormue = null,
                søkersFormue = Formuegrunnlag.Verdier.empty(),
                begrunnelse = null,
                bosituasjon = bosituasjon,
                behandlingsPeriode = behandlingsperiode,
            ),
            periode = vurderingsperiode,
        )
    }

    @Test
    fun `lager tidslinje for enkelt vedtak`() {
        val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            begrunnelse = null,
        )
        val vedtak = lagVedtak(
            rekkefølge = 1,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(
                    bosituasjon,
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = NonEmptyList.fromListUnsafe(
                        listOf(
                            lagUføreVurderingsperiode(
                                UUID.randomUUID(),
                                Periode.create(1.januar(2021), 31.desember(2021)),
                                Periode.create(1.januar(2021), 31.desember(2021)),
                            ),
                        ),
                    ),
                ),
                formue = Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
                    vurderingsperioder = NonEmptyList.fromListUnsafe(
                        listOf(
                            lagFormueVurderingsperiode(
                                id = UUID.randomUUID(),
                                vurderingsperiode = Periode.create(1.januar(2021), 31.desember(2021)),
                                formuegrunnlagPeriode = Periode.create(1.januar(2021), 31.desember(2021)),
                                behandlingsperiode = Periode.create(1.januar(2021), 31.desember(2021)),
                                bosituasjon = bosituasjon,
                            ),
                        ),
                    ),
                ),
            ),
        )
        listOf(vedtak).lagTidslinje(
            Periode.create(1.januar(2021), 31.desember(2021)),
            fixedClock,
        ).tidslinje.let { tidslinje ->
            tidslinje.size shouldBe 1
            tidslinje[0].shouldBeEqualToIgnoringFields(
                Vedtak.VedtakPåTidslinje(
                    opprettet = vedtak.opprettet,
                    periode = vedtak.periode,
                    grunnlagsdata = vedtak.behandling.grunnlagsdata, // ignorert - denne testes under
                    vilkårsvurderinger = vedtak.behandling.vilkårsvurderinger, // ignorert - denne testes under
                    fradrag = vedtak.beregning.getFradrag(),
                    originaltVedtak = vedtak,
                ),
                Vedtak.VedtakPåTidslinje::grunnlagsdata, Vedtak.VedtakPåTidslinje::vilkårsvurderinger,
            )
            tidslinje[0].let { vedtakPåTidslinje ->
                vedtakPåTidslinje.vilkårsvurderinger.uføre shouldBe Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = NonEmptyList.fromListUnsafe(
                        listOf(
                            Vurderingsperiode.Uføre.create(
                                id = (vedtakPåTidslinje.vilkårsvurderinger.uføre as Vilkår.Uførhet.Vurdert).vurderingsperioder.first().id,
                                opprettet = fixedTidspunkt,
                                resultat = Resultat.Innvilget,
                                grunnlag = Grunnlag.Uføregrunnlag(
                                    id = (vedtakPåTidslinje.vilkårsvurderinger.uføre as Vilkår.Uførhet.Vurdert).vurderingsperioder.first().grunnlag!!.id,
                                    opprettet = fixedTidspunkt,
                                    periode = Periode.create(1.januar(2021), 31.desember(2021)),
                                    uføregrad = Uføregrad.parse(50),
                                    forventetInntekt = 0,
                                ),
                                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                                begrunnelse = null,
                            ),
                        ),
                    ),
                )
                vedtakPåTidslinje.grunnlagsdata.bosituasjon shouldBe listOf(
                    bosituasjon.copy(
                        id = vedtakPåTidslinje.grunnlagsdata.bosituasjon[0].id,
                    ),
                )
                vedtakPåTidslinje.grunnlagsdata.fradragsgrunnlag shouldBe emptyList()
                vedtakPåTidslinje.vilkårsvurderinger.formue shouldBe Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
                    vurderingsperioder = NonEmptyList.fromListUnsafe(
                        listOf(
                            Vurderingsperiode.Formue.create(
                                id = (vedtakPåTidslinje.vilkårsvurderinger.formue as Vilkår.Formue.Vurdert).vurderingsperioder.first().id,
                                opprettet = (vedtakPåTidslinje.vilkårsvurderinger.formue as Vilkår.Formue.Vurdert).vurderingsperioder.first().opprettet,
                                resultat = Resultat.Innvilget,
                                grunnlag = Formuegrunnlag.create(
                                    id = (vedtakPåTidslinje.vilkårsvurderinger.formue as Vilkår.Formue.Vurdert).vurderingsperioder.first().grunnlag.id,
                                    opprettet = (vedtakPåTidslinje.vilkårsvurderinger.formue as Vilkår.Formue.Vurdert).vurderingsperioder.first().grunnlag.opprettet,
                                    periode = Periode.create(1.januar(2021), 31.desember(2021)),
                                    epsFormue = null,
                                    søkersFormue = Formuegrunnlag.Verdier.empty(),
                                    begrunnelse = null,
                                    bosituasjon = bosituasjon,
                                    behandlingsPeriode = Periode.create(1.januar(2021), 31.desember(2021)),
                                ),
                                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                            ),
                        ),
                    ),
                )
            }
        }
    }

    /**
     *  |––––|      a
     *      |–––––| b
     *  |---|-----| resultat
     */
    @Test
    fun `lager tidslinje for flere vedtak`() {
        val uføreIdA = UUID.randomUUID()
        val formueIdA = UUID.randomUUID()
        val uføreIdB = UUID.randomUUID()
        val formueIdB = UUID.randomUUID()
        val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            begrunnelse = null,
        )
        val a = lagVedtak(
            rekkefølge = 1,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            grunnlagsdata = Grunnlagsdata(bosituasjon = listOf(bosituasjon)),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = NonEmptyList.fromListUnsafe(
                        listOf(
                            lagUføreVurderingsperiode(
                                id = uføreIdA,
                                vurderingsperiode = Periode.create(1.januar(2021), 31.desember(2021)),
                                uføregrunnlagPeriode = Periode.create(1.januar(2021), 31.desember(2021)),
                            ),
                        ),
                    ),
                ),
                formue = Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
                    vurderingsperioder = NonEmptyList.fromListUnsafe(
                        listOf(
                            lagFormueVurderingsperiode(
                                id = formueIdA,
                                vurderingsperiode = Periode.create(1.januar(2021), 31.desember(2021)),
                                formuegrunnlagPeriode = Periode.create(1.januar(2021), 31.desember(2021)),
                                behandlingsperiode = Periode.create(1.januar(2021), 31.desember(2021)),
                                bosituasjon = bosituasjon,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val b = lagVedtak(
            rekkefølge = 2,
            fraDato = 1.mai(2021),
            tilDato = 31.desember(2021),
            grunnlagsdata = Grunnlagsdata(bosituasjon = listOf(bosituasjon)),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = NonEmptyList.fromListUnsafe(
                        listOf(
                            lagUføreVurderingsperiode(
                                id = uføreIdB,
                                vurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                                uføregrunnlagPeriode = Periode.create(1.mai(2021), 31.desember(2021)),
                            ),
                        ),
                    ),
                ),
                formue = Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
                    vurderingsperioder = NonEmptyList.fromListUnsafe(
                        listOf(
                            lagFormueVurderingsperiode(
                                id = formueIdB,
                                vurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                                formuegrunnlagPeriode = Periode.create(1.mai(2021), 31.desember(2021)),
                                behandlingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                                bosituasjon = bosituasjon,
                            ),
                        ),
                    ),
                ),
            ),
        )
        listOf(a, b).lagTidslinje(
            Periode.create(
                1.januar(2021),
                31.desember(2021),
            ),
            clock = no.nav.su.se.bakover.domain.fixedClock,
        ).tidslinje.let {
            it.first().let { first ->
                first.opprettet shouldBe a.opprettet
                first.periode shouldBe Periode.create(1.januar(2021), 30.april(2021))
                first.grunnlagsdata.let { grunnlagsdata ->
                    grunnlagsdata.bosituasjon.first().shouldBeTypeOf<Grunnlag.Bosituasjon.Fullstendig.Enslig>()
                    grunnlagsdata.bosituasjon.first().id shouldNotBe bosituasjon.id
                    grunnlagsdata.bosituasjon.first().opprettet shouldBe bosituasjon.opprettet
                    (grunnlagsdata.bosituasjon.first() as Grunnlag.Bosituasjon.Fullstendig.Enslig).begrunnelse shouldBe bosituasjon.begrunnelse
                    grunnlagsdata.fradragsgrunnlag shouldBe emptyList()
                }

                first.vilkårsvurderinger.let { vilkårsvurderinger ->
                    vilkårsvurderinger.uføre.shouldBeTypeOf<Vilkår.Uførhet.Vurdert>()
                    (vilkårsvurderinger.uføre as Vilkår.Uførhet.Vurdert).let { uføre ->
                        val expectedUføre = lagUføreVurderingsperiode(
                            id = uføreIdA,
                            vurderingsperiode = Periode.create(1.januar(2021), 30.april(2021)),
                            uføregrunnlagPeriode = Periode.create(1.januar(2021), 30.april(2021)),
                        )
                        uføre.vurderingsperioder.first().shouldBeEqualToIgnoringFields(
                            expectedUføre,
                            Vurderingsperiode.Uføre::id,
                            Vurderingsperiode.Uføre::grunnlag,
                        )
                        uføre.vurderingsperioder.first().id shouldNotBe expectedUføre.id
                        uføre.vurderingsperioder.first().grunnlag!!.id shouldNotBe expectedUføre.grunnlag!!.id
                    }
                    vilkårsvurderinger.formue.shouldBeTypeOf<Vilkår.Formue.Vurdert>()
                    (vilkårsvurderinger.formue as Vilkår.Formue.Vurdert).let { formue ->
                        val expectedFormue = lagFormueVurderingsperiode(
                            id = formueIdA,
                            vurderingsperiode = Periode.create(1.januar(2021), 30.april(2021)),
                            formuegrunnlagPeriode = Periode.create(1.januar(2021), 30.april(2021)),
                            behandlingsperiode = Periode.create(1.januar(2021), 30.april(2021)),
                            bosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
                                id = bosituasjon.id,
                                opprettet = fixedTidspunkt,
                                periode = Periode.create(1.januar(2021), 30.april(2021)),
                                begrunnelse = null,
                            ),
                        )
                        formue.vurderingsperioder.first()
                            .shouldBeEqualToIgnoringFields(
                                expectedFormue,
                                Vurderingsperiode.Formue::id,
                                Vurderingsperiode.Formue::grunnlag,
                            )
                        formue.vurderingsperioder.first().id shouldNotBe expectedFormue.id
                        formue.vurderingsperioder.first().grunnlag.id shouldNotBe expectedFormue.grunnlag.id
                    }
                }
                first.fradrag shouldBe a.beregning.getFradrag()
                first.originaltVedtak shouldBe a
            }

            it.last().let { last ->
                last.opprettet shouldBe b.opprettet
                last.periode shouldBe Periode.create(1.mai(2021), 31.desember(2021))
                last.grunnlagsdata.let { grunnlagsdata ->
                    grunnlagsdata.bosituasjon.first().shouldBeTypeOf<Grunnlag.Bosituasjon.Fullstendig.Enslig>()
                    grunnlagsdata.bosituasjon.first().id shouldNotBe bosituasjon.id
                    grunnlagsdata.bosituasjon.first().opprettet shouldBe bosituasjon.opprettet
                    (grunnlagsdata.bosituasjon.first() as Grunnlag.Bosituasjon.Fullstendig.Enslig).begrunnelse shouldBe bosituasjon.begrunnelse
                    grunnlagsdata.fradragsgrunnlag shouldBe emptyList()
                }

                last.vilkårsvurderinger.let { vilkårsvurderinger ->
                    vilkårsvurderinger.uføre.shouldBeTypeOf<Vilkår.Uførhet.Vurdert>()
                    (vilkårsvurderinger.uføre as Vilkår.Uførhet.Vurdert).let { uføre ->
                        val expectedUføre = lagUføreVurderingsperiode(
                            id = uføreIdB,
                            vurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                            uføregrunnlagPeriode = Periode.create(1.mai(2021), 31.desember(2021)),
                        )
                        uføre.vurderingsperioder.first().shouldBeEqualToIgnoringFields(
                            expectedUføre,
                            Vurderingsperiode.Uføre::id,
                            Vurderingsperiode.Uføre::grunnlag,
                        )
                        uføre.vurderingsperioder.first().id shouldNotBe expectedUføre.id
                        uføre.vurderingsperioder.first().grunnlag!!.id shouldNotBe expectedUføre.grunnlag!!.id
                    }
                    vilkårsvurderinger.formue.shouldBeTypeOf<Vilkår.Formue.Vurdert>()
                    (vilkårsvurderinger.formue as Vilkår.Formue.Vurdert).let { formue ->
                        val expectedFormue = lagFormueVurderingsperiode(
                            id = formueIdB,
                            vurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                            formuegrunnlagPeriode = Periode.create(1.mai(2021), 31.desember(2021)),
                            behandlingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                            bosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
                                id = bosituasjon.id,
                                opprettet = fixedTidspunkt,
                                periode = Periode.create(1.mai(2021), 31.desember(2021)),
                                begrunnelse = null,
                            ),
                        )
                        formue.vurderingsperioder.first()
                            .shouldBeEqualToIgnoringFields(
                                expectedFormue,
                                Vurderingsperiode.Formue::id,
                                Vurderingsperiode.Formue::grunnlag,
                            )
                        formue.vurderingsperioder.first().id shouldNotBe expectedFormue.id
                        formue.vurderingsperioder.first().grunnlag.id shouldNotBe expectedFormue.grunnlag.id
                    }
                }
                last.fradrag shouldBe b.beregning.getFradrag()
                last.originaltVedtak shouldBe b
            }
        }
    }

    /**
     *  |-––––-|      a
     *  |------|      u1
     *       |------| b
     *       |------| u2
     *  |----|------| resultat
     *  |-u1-|--u2--|
     */
    @Test
    fun `begrenser perioden på grunnlagene til samme perioden som vedtaket`() {
        val p1 = Periode.create(1.januar(2021), 31.desember(2021))
        val u1 = lagUføregrunnlag(
            rekkefølge = 1,
            fraDato = p1.fraOgMed,
            tilDato = p1.tilOgMed,
        )
        val v1 = Vurderingsperiode.Uføre.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = u1,
            periode = p1,
            begrunnelse = "hei",
        )
        val a = lagVedtak(
            rekkefølge = 1,
            fraDato = p1.fraOgMed,
            tilDato = p1.tilOgMed,
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = p1,
                        begrunnelse = null,
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(nonEmptyListOf(v1)),
                formue = formueVilkår(p1),
            ),
        )

        val p2 = Periode.create(1.mai(2021), 31.desember(2021))
        val u2 = lagUføregrunnlag(
            rekkefølge = 2,
            fraDato = p2.fraOgMed,
            tilDato = p2.tilOgMed,
        )
        val uføreVilkår2 = Vurderingsperiode.Uføre.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            resultat = Resultat.Avslag,
            grunnlag = u2,
            periode = p2,
            begrunnelse = "hei",
        )
        val b = lagVedtak(
            rekkefølge = 2,
            fraDato = p2.fraOgMed,
            tilDato = p2.tilOgMed,
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = p2,
                        begrunnelse = null,
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(nonEmptyListOf(uføreVilkår2)),
                formue = formueVilkår(p2),
            ),
        )
        listOf(a, b).lagTidslinje(
            Periode.create(1.januar(2021), 31.desember(2021)),
            fixedClock,
        ).tidslinje.let { tidslinje ->
            tidslinje[0].let { vedtakPåTidslinje ->
                vedtakPåTidslinje.originaltVedtak shouldBe a
                vedtakPåTidslinje.opprettet shouldBe a.opprettet
                vedtakPåTidslinje.periode shouldBe Periode.create(1.januar(2021), 30.april(2021))
                vedtakPåTidslinje.vilkårsvurderinger.uføre.grunnlag[0].let {
                    it.id shouldNotBe u1.id
                    it.periode shouldBe Periode.create(1.januar(2021), 30.april(2021))
                    it.uføregrad shouldBe u1.uføregrad
                    it.forventetInntekt shouldBe u1.forventetInntekt
                }
                (vedtakPåTidslinje.vilkårsvurderinger.uføre as Vilkår.Uførhet.Vurdert).let { vilkårcopy ->
                    vilkårcopy.vurderingsperioder[0].let { vurderingsperiodecopy ->
                        vurderingsperiodecopy.id shouldNotBe v1.id
                        vurderingsperiodecopy.begrunnelse shouldBe v1.begrunnelse
                        vurderingsperiodecopy.resultat shouldBe v1.resultat
                        vurderingsperiodecopy.periode shouldBe Periode.create(1.januar(2021), 30.april(2021))
                        vurderingsperiodecopy.grunnlag!!.let {
                            it.id shouldNotBe u1.id
                            it.periode shouldBe Periode.create(1.januar(2021), 30.april(2021))
                            it.uføregrad shouldBe u1.uføregrad
                            it.forventetInntekt shouldBe u1.forventetInntekt
                        }
                    }
                }
            }
            tidslinje[1].let { vedtakPåTidslinje ->
                vedtakPåTidslinje.originaltVedtak shouldBe b
                vedtakPåTidslinje.opprettet shouldBe b.opprettet
                vedtakPåTidslinje.periode shouldBe b.periode
                vedtakPåTidslinje.vilkårsvurderinger.uføre.grunnlag[0].let {
                    it.id shouldNotBe u2.id
                    it.periode shouldBe u2.periode
                    it.uføregrad shouldBe u2.uføregrad
                    it.forventetInntekt shouldBe u2.forventetInntekt
                }
                (vedtakPåTidslinje.vilkårsvurderinger.uføre as Vilkår.Uførhet.Vurdert).let { vilkårcopy ->
                    vilkårcopy.vurderingsperioder[0].let { vurderingsperiodecopy ->
                        vurderingsperiodecopy.id shouldNotBe uføreVilkår2.id
                        vurderingsperiodecopy.begrunnelse shouldBe uføreVilkår2.begrunnelse
                        vurderingsperiodecopy.resultat shouldBe uføreVilkår2.resultat
                        vurderingsperiodecopy.periode shouldBe uføreVilkår2.periode
                        vurderingsperiodecopy.grunnlag!!.let {
                            it.id shouldNotBe u2.id
                            it.periode shouldBe u2.periode
                            it.uføregrad shouldBe u2.uføregrad
                            it.forventetInntekt shouldBe u2.forventetInntekt
                        }
                    }
                }

                vedtakPåTidslinje.vilkårsvurderinger.formue.assertEqualsIgnoreId(
                    Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
                        formueVilkår(Periode.create(1.mai(2021), 31.desember(2021))).vurderingsperioder +
                            formueVilkår(Periode.create(1.januar(2021), 30.april(2021))).vurderingsperioder,
                    ),
                )
            }
        }
    }

    /**
     *  |------| a
     *  |------| u1
     *  |––––––| b
     *           u2 (fjernet)
     *  |------| resultat
     *           (ingen uføregrunnlag)
     */
    @Test
    fun `informasjon som overskrives av nyere vedtak forsvinner fra tidslinjen`() {
        val p1 = Periode.create(1.januar(2021), 31.desember(2021))
        val u1 = lagUføregrunnlag(
            rekkefølge = 1,
            fraDato = p1.fraOgMed,
            tilDato = p1.tilOgMed,
        )
        fun uføreVurderingsperiode() = Vurderingsperiode.Uføre.create(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = u1,
            periode = p1,
            begrunnelse = "hei",
        )
        val a = lagVedtak(
            rekkefølge = 1,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = p1,
                        begrunnelse = null,
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(nonEmptyListOf(uføreVurderingsperiode())),
                formue = formueVilkår(p1),
            ),
        )
        val b = lagVedtak(
            rekkefølge = 2,
            fraDato = 1.januar(2021),
            tilDato = 31.desember(2021),
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                        fnr = FnrGenerator.random(),
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = Periode.create(1.januar(2021), 31.desember(2021)),
                        begrunnelse = "giftet seg"
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(nonEmptyListOf(uføreVurderingsperiode())),
                formue = formueVilkår(p1),
            ),
        )

        val actual =
            listOf(a, b).lagTidslinje(
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                fixedClock,
            ).tidslinje.let {
                it.size shouldBe 1
                it[0]
            }

        actual.shouldBeEqualToIgnoringFields(
            Vedtak.VedtakPåTidslinje(
                opprettet = b.opprettet,
                periode = b.periode,
                grunnlagsdata = Grunnlagsdata.EMPTY, // ignorert
                vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert, // ignorert
                fradrag = b.beregning.getFradrag(),
                originaltVedtak = b,
            ),
            Vedtak.VedtakPåTidslinje::grunnlagsdata, Vedtak.VedtakPåTidslinje::vilkårsvurderinger,
        )
        // TODO jah: Legg til testing av innhold av grunnlagsdata og vilkårsvurderinger med Jacob
        actual.grunnlagsdata.bosituasjon.size shouldBe 1
        actual.grunnlagsdata.bosituasjon.first().shouldBeTypeOf<Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning>()
    }

    private fun lagUføregrunnlag(rekkefølge: Long, fraDato: LocalDate, tilDato: LocalDate) = Grunnlag.Uføregrunnlag(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(fixedClock).plus(rekkefølge, ChronoUnit.DAYS),
        periode = Periode.create(fraDato, tilDato),
        uføregrad = Uføregrad.parse(100),
        forventetInntekt = 0,
    )

    private fun lagVedtak(
        rekkefølge: Long,
        fraDato: LocalDate,
        tilDato: LocalDate,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger,
    ) = Vedtak.fromSøknadsbehandling(
        Søknadsbehandling.Iverksatt.Innvilget(
            id = mock(),
            opprettet = Tidspunkt.now(fixedClock).plus(rekkefølge, ChronoUnit.DAYS),
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(2222),
            søknad = mock(),
            oppgaveId = mock(),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            fnr = FnrGenerator.random(),
            beregning = mock(),
            simulering = mock(),
            saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("Attes T. Ant")),
            fritekstTilBrev = "",
            stønadsperiode = Stønadsperiode.create(
                periode = Periode.create(fraDato, tilDato),
                begrunnelse = "begrunnelsen for perioden",
            ),
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
        ),
        UUID30.randomUUID(),
    )
}
