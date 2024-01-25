package no.nav.su.se.bakover.domain.vilkår

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.august
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlagForventetInntekt0
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlagForventetInntekt12000
import org.junit.jupiter.api.Test
import vilkår.domain.Vurdering
import vilkår.domain.slåSammenLikePerioder
import vilkår.uføre.domain.UføreVilkår
import vilkår.uføre.domain.Uføregrad
import vilkår.uføre.domain.Uføregrunnlag
import vilkår.uføre.domain.VurderingsperiodeUføre
import java.util.UUID

internal class UførhetTest {
    @Test
    fun `validerer at vurderingsperioder ikke overlapper`() {
        UføreVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeUføre.create(
                    vurdering = Vurdering.Innvilget,
                    opprettet = fixedTidspunkt,
                    grunnlag = null,
                    periode = år(2021),
                ),
                VurderingsperiodeUføre.create(
                    vurdering = Vurdering.Avslag,
                    opprettet = fixedTidspunkt,
                    grunnlag = null,
                    periode = år(2021),
                ),
            ),
        ) shouldBe UføreVilkår.Vurdert.UgyldigUførevilkår.OverlappendeVurderingsperioder.left()
    }

    @Test
    fun `slår sammen tilstøtende og like vurderingsperioder`() {
        val v1 = lagUføreVurderingsperiode(periode = januar(2021))
        val v2 = lagUføreVurderingsperiode(periode = februar(2021))
        val v3 =
            lagUføreVurderingsperiode(vurdering = Vurdering.Avslag, periode = mars(2021))

        val actual = nonEmptyListOf(v1, v2, v3).slåSammenLikePerioder()
        actual.size shouldBe 2
        actual.first() shouldBe VurderingsperiodeUføre.create(
            id = actual.first().id,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Innvilget,
            grunnlag = null,
            periode = Periode.create(1.januar(2021), 28.februar(2021)),
        )
        actual.last() shouldBe VurderingsperiodeUføre.create(
            id = actual.last().id,
            opprettet = fixedTidspunkt,
            vurdering = Vurdering.Avslag,
            grunnlag = null,
            periode = mars(2021),
        )
    }

    @Test
    fun `2 uføre-perioder som tilstøter og er lik`() {
        val v1 = lagUføreVurderingsperiode(periode = januar(2021))
        val v2 = lagUføreVurderingsperiode(periode = februar(2021))

        v1.tilstøterOgErLik(v2) shouldBe true
    }

    @Test
    fun `2 uføre-perioder som ikke tilstøter, men er lik`() {
        val v1 = lagUføreVurderingsperiode(periode = januar(2021))
        val v2 = lagUføreVurderingsperiode(periode = mars(2021))

        v1.tilstøterOgErLik(v2) shouldBe false
    }

    @Test
    fun `2 uføre-perioder som tilstøter, men grunnlag er ulik`() {
        val v1 = lagUføreVurderingsperiode(periode = januar(2021))
        val v2 = lagUføreVurderingsperiode(
            periode = februar(2021),
            grunnlag = Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = februar(2021),
                uføregrad = Uføregrad.parse(1),
                forventetInntekt = 0,
            ),
        )

        v1.tilstøterOgErLik(v2) shouldBe false
    }

    @Test
    fun `oppdatering av stønadsperiode for uførevilkår med flere vurderinger - simpel`() {
        val januar = lagUføreVurderingsperiode(
            periode = januar(2021),
            grunnlag = uføregrunnlagForventetInntekt0(periode = januar(2021)),
        )
        val februar = lagUføreVurderingsperiode(
            periode = februar(2021),
            grunnlag = uføregrunnlagForventetInntekt12000(periode = februar(2021)),
        )

        UføreVilkår.Vurdert.fromVurderingsperioder(
            nonEmptyListOf(januar, februar),
        ).getOrFail().let { vilkår ->
            vilkår.oppdaterStønadsperiode(
                stønadsperiode = Stønadsperiode.create(periode = januar(2021)),
            ).let {
                it.vurderingsperioder shouldHaveSize 1
                it.vurderingsperioder.first().erLik(januar) shouldBe true
            }
        }
    }

    @Test
    fun `oppdatering av stønadsperiode for uførevilkår med flere vurderinger - krymper start og slutt`() {
        val janMars = lagUføreVurderingsperiode(
            periode = Periode.create(1.januar(2021), 31.mars(2021)),
            grunnlag = uføregrunnlagForventetInntekt0(periode = Periode.create(1.januar(2021), 31.mars(2021))),
        )
        val aprAug = lagUføreVurderingsperiode(
            periode = Periode.create(1.april(2021), 31.august(2021)),
            grunnlag = uføregrunnlagForventetInntekt12000(periode = Periode.create(1.april(2021), 31.august(2021))),
        )
        UføreVilkår.Vurdert.fromVurderingsperioder(
            nonEmptyListOf(janMars, aprAug),
        ).getOrFail().let { vilkår ->
            vilkår.oppdaterStønadsperiode(
                stønadsperiode = Stønadsperiode.create(periode = Periode.create(1.mars(2021), 31.mai(2021))),
            ).let {
                it.vurderingsperioder shouldHaveSize 2
                it.vurderingsperioder.first().erLik(
                    janMars.oppdaterStønadsperiode(
                        Stønadsperiode.create(
                            mars(2021),
                        ),
                    ),
                ) shouldBe true
                it.vurderingsperioder.last().erLik(
                    aprAug.oppdaterStønadsperiode(
                        Stønadsperiode.create(
                            Periode.create(
                                1.april(2021),
                                31.mai(2021),
                            ),
                        ),
                    ),
                ) shouldBe true
            }
        }
    }

    @Test
    fun `oppdatering av stønadsperiode for uførevilkår med flere vurderinger - utvider start, midt og slutt`() {
        val februar = lagUføreVurderingsperiode(
            periode = februar(2021),
            grunnlag = uføregrunnlagForventetInntekt0(periode = februar(2021)),
        )
        val april = lagUføreVurderingsperiode(
            periode = april(2021),
            grunnlag = uføregrunnlagForventetInntekt12000(periode = april(2021)),
        )
        UføreVilkår.Vurdert.fromVurderingsperioder(
            nonEmptyListOf(februar, april),
        ).getOrFail().let { vilkår ->
            vilkår.oppdaterStønadsperiode(
                stønadsperiode = Stønadsperiode.create(periode = Periode.create(1.januar(2021), 31.mai(2021))),
            ).let {
                it.vurderingsperioder shouldHaveSize 2
                it.vurderingsperioder.first().erLik(
                    februar.oppdaterStønadsperiode(
                        Stønadsperiode.create(
                            Periode.create(
                                1.januar(2021),
                                31.mars(2021),
                            ),
                        ),
                    ),
                ) shouldBe true
                it.vurderingsperioder.last().erLik(
                    april.oppdaterStønadsperiode(
                        Stønadsperiode.create(
                            Periode.create(
                                1.april(2021),
                                31.mai(2021),
                            ),
                        ),
                    ),
                ) shouldBe true
            }
        }
    }

    @Test
    fun `oppdatering av stønadsperiode for uførevilkår med flere vurderinger - ingen overlapp mellom gammel og ny og ny er tidligere`() {
        val februar = lagUføreVurderingsperiode(
            periode = februar(2022),
            grunnlag = uføregrunnlagForventetInntekt0(periode = februar(2022)),
        )
        val mars = lagUføreVurderingsperiode(
            periode = mars(2022),
            grunnlag = uføregrunnlagForventetInntekt12000(periode = mars(2022)),
        )
        UføreVilkår.Vurdert.fromVurderingsperioder(
            nonEmptyListOf(februar, mars),
        ).getOrFail().let { vilkår ->
            vilkår.oppdaterStønadsperiode(
                stønadsperiode = Stønadsperiode.create(periode = februar(2021)),
            ).let {
                it.vurderingsperioder shouldHaveSize 1
                it.vurderingsperioder.first().erLik(
                    februar.oppdaterStønadsperiode(
                        Stønadsperiode.create(
                            februar(2021),
                        ),
                    ),
                ) shouldBe true
            }
        }
    }

    @Test
    fun `oppdatering av stønadsperiode for uførevilkår med flere vurderinger - ingen overlapp mellom gammel og ny og ny er senere`() {
        val februar = lagUføreVurderingsperiode(
            periode = februar(2022),
            grunnlag = uføregrunnlagForventetInntekt0(periode = februar(2022)),
        )
        val mars = lagUføreVurderingsperiode(
            periode = mars(2022),
            grunnlag = uføregrunnlagForventetInntekt12000(periode = mars(2022)),
        )
        UføreVilkår.Vurdert.fromVurderingsperioder(
            nonEmptyListOf(februar, mars),
        ).getOrFail().let { vilkår ->
            vilkår.oppdaterStønadsperiode(
                stønadsperiode = Stønadsperiode.create(periode = februar(2023)),
            ).let {
                it.vurderingsperioder shouldHaveSize 1
                it.vurderingsperioder.first().erLik(
                    mars.oppdaterStønadsperiode(
                        Stønadsperiode.create(
                            mars(2023),
                        ),
                    ),
                ) shouldBe true
            }
        }
    }

    private fun lagUføreVurderingsperiode(
        periode: Periode,
        tidspunkt: Tidspunkt = fixedTidspunkt,
        vurdering: Vurdering = Vurdering.Innvilget,
        grunnlag: Uføregrunnlag? = null,
    ): VurderingsperiodeUføre {
        return VurderingsperiodeUføre.create(
            opprettet = tidspunkt,
            vurdering = vurdering,
            grunnlag = grunnlag,
            periode = periode,
        )
    }
}
