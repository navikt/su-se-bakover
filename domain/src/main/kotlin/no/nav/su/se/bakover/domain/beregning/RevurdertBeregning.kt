package no.nav.su.se.bakover.domain.beregning

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy

/**
 * Fra lovteksten https://lovdata.no/dokument/NL/lov/2005-04-29-21:
 * > Dersom ei endring som nemnd i § 10 fører til at stønaden vert sett opp, gjeld dette frå og med den månaden då endringa skjedde.
 * > Dersom ytinga vert sett ned, får dette verknad frå og med månaden etter den månaden då endringa skjedde.
 *
 * > Fastsett stønad skal setjast opp eller ned dersom det skjer endringar i inntektene eller dei andre tilhøva som er lagt til grunn ved fastsetjinga av stønaden, og dette fører med seg ei endring av stønaden med minst 10 prosent.
 *
 * Det kan hende vi vil gi denne navnet: EndretBeregning (dersom den er gjenbrukbar for klage, anke og automatiske/recurring endringer som grunnbeløpsendring.
 */
internal class RevurdertBeregning private constructor(revurdertBeregning: Beregning) : Beregning by revurdertBeregning {

    companion object {
        fun fraSøknadsbehandling(
            vedtattBeregning: Beregning,
            beregningsgrunnlag: Beregningsgrunnlag,
            beregningsstrategi: BeregningStrategy,
        ): Either<KanIkkeVelgeSisteMånedVedNedgangIStønaden, Beregning> {
            val revurdertBeregning = beregningsstrategi.beregn(beregningsgrunnlag)

            return when {
                revurdertBeregning.getMånedsberegninger().first()
                    .getSumYtelse() > vedtattBeregning.getMånedsberegninger().first()
                    .getSumYtelse() -> revurdertBeregning.right()
                revurdertBeregning.getMånedsberegninger().first()
                    .getSumYtelse() < vedtattBeregning.getMånedsberegninger().first()
                    .getSumYtelse() -> beregnMedVirkningFraOgMedMånedenEtter(revurdertBeregning)
                else -> revurdertBeregning.right()
            }
        }
    }
}

/**
Når revurderingsperioden kun har 1 måned og utbetalt beløp er redusert, blir det feil å returnere en beregning
 */
object KanIkkeVelgeSisteMånedVedNedgangIStønaden

private fun beregnMedVirkningFraOgMedMånedenEtter(
    revurdertBeregning: Beregning
): Either<KanIkkeVelgeSisteMånedVedNedgangIStønaden, Beregning> {
    if (revurdertBeregning.getMånedsberegninger().size < 2) {
        return KanIkkeVelgeSisteMånedVedNedgangIStønaden.left()
    }
    val nyPeriode = Periode.create(
        revurdertBeregning.getPeriode().getFraOgMed().plusMonths(1),
        revurdertBeregning.getPeriode().getTilOgMed()
    )
    return BeregningMedFradragBeregnetMånedsvis(
        periode = nyPeriode,
        sats = revurdertBeregning.getSats(),
        fradrag = revurdertBeregning.getFradrag()
            .filterNot {
                fjernFradragSomLiggerUtenforDenNyePerioden(it, nyPeriode)
            }
            .map {
                FradragFactory.ny(
                    type = it.getFradragstype(),
                    månedsbeløp = it.getMånedsbeløp(),
                    periode = Periode.create(
                        fraOgMed = it.getPeriode().getFraOgMed().let { fraOgMed ->
                            if (fraOgMed == revurdertBeregning.getPeriode().getFraOgMed()) {
                                fraOgMed.plusMonths(1)
                            } else {
                                fraOgMed
                            }
                        },
                        tilOgMed = it.getPeriode().getTilOgMed()
                    ),
                    utenlandskInntekt = it.getUtenlandskInntekt(),
                    tilhører = it.getTilhører(),
                )
            },
        fradragStrategy = FradragStrategy.fromName(revurdertBeregning.getFradragStrategyName())
    ).right()
}

private fun fjernFradragSomLiggerUtenforDenNyePerioden(
    fradrag: Fradrag,
    nyPeriode: Periode
): Boolean {
    return nyPeriode.tilstøter(fradrag.getPeriode())
}
