package no.nav.su.se.bakover.domain.revurdering.opphør

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import beregning.domain.BeregningForMåned
import io.kotest.assertions.arrow.core.shouldBeRight
import no.nav.su.se.bakover.common.domain.tid.september
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.test.beregning
import no.nav.su.se.bakover.test.bosituasjonEpsUnder67
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.forventetInntekt0FradragForMåned
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlagForventetInntekt
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkårFraGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurderingerRevurderingInnvilget
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.uføre.domain.Uføregrunnlag

/**
 * Oppsto en bug som traff Opphørsgrunner.FOR_HØY_INNTEKT og OpphørOgAndreEndringerIKombinasjon
 * Søknadsvedtak: 2023-07 -> 2024-06
 * Stansvedtak: 2023-12 -> 2024-06
 * Gjenopptaksvedtak: 2023-12 -> 2024-06
 * Reguleringsvedtak: 2024-05 -> 2024-06
 * Revurderingsperiode: 2024-03 -> 2024-06
 */
internal class IdentifiserRevurderingsopphørSomIkkeStøttesKombiopphørTest {

    @Test
    fun `skal støtte kombinasjon av 2pst og 0pst opphør`() {
        val revurderingsperiode = mars(2024)..juni(2024)

        val satsFactoryTestPåDato = satsFactoryTestPåDato(3.september(2024))
        val eksisterendeMånedsberegning: NonEmptyList<BeregningForMåned> = nonEmptyListOf(
            BeregningForMåned(
                måned = mars(2024),
                fradrag = listOf(
                    FradragForMåned(
                        fradragstype = Fradragstype.Uføretrygd,
                        månedsbeløp = 6198.0,
                        måned = mars(2024),
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    forventetInntekt0FradragForMåned(mars(2024)),
                ),
                fullSupplerendeStønadForMåned = satsFactoryTestPåDato.høyUføre(mars(2024)),
                sumYtelse = 18317,
                sumFradrag = 6198.0,
            ),
            BeregningForMåned(
                måned = april(2024),
                fradrag = listOf(
                    FradragForMåned(
                        fradragstype = Fradragstype.Uføretrygd,
                        månedsbeløp = 6198.0,
                        måned = april(2024),
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    forventetInntekt0FradragForMåned(april(2024)),
                ),
                fullSupplerendeStønadForMåned = satsFactoryTestPåDato.høyUføre(april(2024)),
                sumYtelse = 18317,
                sumFradrag = 6198.0,
            ),
            BeregningForMåned(
                måned = mai(2024),
                fradrag = listOf(
                    FradragForMåned(
                        fradragstype = Fradragstype.Uføretrygd,
                        månedsbeløp = 6481.0,
                        måned = mai(2024),
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    forventetInntekt0FradragForMåned(mai(2024)),
                ),
                fullSupplerendeStønadForMåned = satsFactoryTestPåDato.høyUføre(mai(2024)),
                sumYtelse = 19151,
                sumFradrag = 6481.0,
            ),
            BeregningForMåned(
                måned = juni(2024),
                fradrag = listOf(
                    FradragForMåned(
                        fradragstype = Fradragstype.Uføretrygd,
                        månedsbeløp = 6481.0,
                        måned = juni(2024),
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    forventetInntekt0FradragForMåned(juni(2024)),
                ),
                fullSupplerendeStønadForMåned = satsFactoryTestPåDato.høyUføre(juni(2024)),
                sumYtelse = 19151,
                sumFradrag = 6481.0,
            ),
        )
        val nyeUføregrunnlag: NonEmptyList<Uføregrunnlag> = nonEmptyListOf(
            uføregrunnlagForventetInntekt(periode = mars(2024)..juni(2024), forventetInntekt = 0),
        )
        val nyttFradragsgrunnlag: NonEmptyList<Fradragsgrunnlag> = nonEmptyListOf(
            lagFradragsgrunnlag(
                type = Fradragstype.Arbeidsavklaringspenger,
                månedsbeløp = 18240.0,
                periode = mars(2024),
                tilhører = FradragTilhører.EPS,
            ),
            lagFradragsgrunnlag(
                type = Fradragstype.Arbeidsavklaringspenger,
                månedsbeløp = 27360.0,
                periode = april(2024)..juni(2024),
                tilhører = FradragTilhører.EPS,
            ),
            lagFradragsgrunnlag(
                type = Fradragstype.Uføretrygd,
                månedsbeløp = 6198.0,
                periode = mars(2024)..april(2024),
                tilhører = FradragTilhører.BRUKER,
            ),
            lagFradragsgrunnlag(
                type = Fradragstype.Uføretrygd,
                månedsbeløp = 6481.0,
                periode = mai(2024)..juni(2024),
                tilhører = FradragTilhører.BRUKER,
            ),
        )
        val bosituasjon = bosituasjonEpsUnder67(periode = revurderingsperiode)
        val nyBeregning = beregning(
            periode = revurderingsperiode,
            uføregrunnlag = nyeUføregrunnlag,
            fradragsgrunnlag = nyttFradragsgrunnlag,
            bosituasjon = bosituasjon,
            satsFactory = satsFactoryTestPåDato,
        )
        val vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget(
            periode = revurderingsperiode,
            uføre = innvilgetUførevilkårFraGrunnlag(
                grunnlag = nyeUføregrunnlag,
            ),
            bosituasjon = nonEmptyListOf(bosituasjon),
        )
        IdentifiserRevurderingsopphørSomIkkeStøttes.MedBeregning(
            revurderingsperiode = nyBeregning.periode,
            vilkårsvurderinger = vilkårsvurderinger,
            gjeldendeMånedsberegninger = eksisterendeMånedsberegning,
            nyBeregning = nyBeregning,
            clock = fixedClock,
        ).resultat.shouldBeRight()
    }
}
