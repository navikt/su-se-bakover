package no.nav.su.se.bakover.test

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import beregning.domain.Beregning
import beregning.domain.BeregningFactory
import beregning.domain.Beregningsgrunnlag
import beregning.domain.Beregningsperiode
import beregning.domain.utledBeregningsstrategi
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlagForventetInntekt
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlagForventetInntekt0
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.uføre.domain.Uføregrunnlag

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
                arbeidsinntekt = (satsFactoryTestPåDato().høyUføre(januar(2021)).satsForMånedAsDouble - 100),
            ),
            fradragsgrunnlagArbeidsinntekt(
                periode = Periode.create(1.mai(2021), 31.desember(2021)),
                arbeidsinntekt = (satsFactoryTestPåDato().høyUføre(mai(2021)).satsForMånedAsDouble - 100),
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
    bosituasjon: Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(periode = periode),
    uføregrunnlag: NonEmptyList<Uføregrunnlag> = nonEmptyListOf(uføregrunnlagForventetInntekt0(periode = periode)),
    /**
     * Bruk uføregrunnlag for forventet inntekt
     * Selvom fradragFraSaksbehandler krever List<Fradrag> for øyeblikket vil den bli refaktorert til List<FradragGrunnlag> i fremtiden.
     */
    fradragsgrunnlag: List<Fradragsgrunnlag> = emptyList(),
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
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode,
                    bosituasjon.utledBeregningsstrategi(
                        satsFactoryTestPåDato(),
                        // TODO("endre oppsett av testdata slik at dette kan hentes fra aktuell behandling")
                        Sakstype.UFØRE,
                    ),
                ),
            ),
        )
    }
}
