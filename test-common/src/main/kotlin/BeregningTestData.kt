package no.nav.su.se.bakover.test

import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.utledBeregningsstrategi
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag

/**
 * Defaultverdier:
 * periode: 2021
 * bosituasjon: bosituasjongrunnlagEnslig (høy sats)
 * uføregrunnlag: Forventet inntekt 0
 * fradrag: ingen
 */
fun beregning(
    periode: Periode = periode2021,
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(periode),
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
    return Beregningsgrunnlag.tryCreate(
        beregningsperiode = periode,
        uføregrunnlag = uføregrunnlag,
        fradragFraSaksbehandler = fradragsgrunnlag.map { it.fradrag },
    ).let {
        bosituasjon.utledBeregningsstrategi().beregn(
            it.getOrHandle {
                throw IllegalArgumentException("Kunne ikke lage testberegning. Underliggende grunn: $it")
            },
        )
    }
}
