package no.nav.su.se.bakover.test

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.utledBeregningsstrategi
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag

/**
 * forventet inntekt 1 000 000
 */
fun beregningAvslagForHøyInntekt(
    periode: Periode = år(2021),
): Beregning {
    return beregning(
        periode = periode,
        uføregrunnlag = nonEmptyListOf(
            uføregrunnlagForventetInntekt(periode = periode, forventetInntekt = 1_000_000),
        ),
    )
}

fun beregningAvslagUnderMinstebeløp(
    periode: Periode = år(2021),
): Beregning {
    return beregning(
        periode = periode,
        uføregrunnlag = nonEmptyListOf(
            uføregrunnlagForventetInntekt(periode = periode, forventetInntekt = 0),
        ),
        fradragsgrunnlag = nonEmptyListOf(
            fradragsgrunnlagArbeidsinntekt(
                periode = Periode.create(1.januar(2021), 30.april(2021)),
                arbeidsinntekt = (satsFactoryTest.høy(januar(2021)).satsForMånedAsDouble - 100),
            ),
            fradragsgrunnlagArbeidsinntekt(
                periode = Periode.create(1.mai(2021), 31.desember(2021)),
                arbeidsinntekt = (satsFactoryTest.høy(mai(2021)).satsForMånedAsDouble - 100),
            ),
        ),
    )
}

/**
 * Defaultverdier:
 * periode: 2021
 * bosituasjon: bosituasjongrunnlagEnslig (høy sats)
 * uføregrunnlag: Forventet inntekt 0
 * fradrag: ingen
 */
fun beregning(
    periode: Periode = år(2021),
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(periode = periode),
    uføregrunnlag: NonEmptyList<Grunnlag.Uføregrunnlag> = nonEmptyListOf(uføregrunnlagForventetInntekt0(periode = periode)),
    /**
     * Bruk uføregrunnlag for forventet inntekt
     * Selvom fradragFraSaksbehandler krever List<Fradrag> for øyeblikket vil den bli refaktorert til List<FradragGrunnlag> i fremtiden.
     */
    fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag> = emptyList(),
): Beregning {
    if (fradragsgrunnlag.any { it.fradrag.fradragstype == Fradragstype.ForventetInntekt }) {
        throw IllegalArgumentException("Foreventet inntekt etter uføre populeres via uføregrunnlag")
    }
    Beregningsgrunnlag.create(
        beregningsperiode = periode,
        uføregrunnlag = uføregrunnlag,
        fradragFraSaksbehandler = fradragsgrunnlag,
    ).let {
        return BeregningFactory(clock = fixedClock).ny(
            fradrag = it.fradrag,
            begrunnelse = null,
            beregningsperioder = listOf(Beregningsperiode(periode, bosituasjon.utledBeregningsstrategi(satsFactoryTest))),
        )
    }
}
