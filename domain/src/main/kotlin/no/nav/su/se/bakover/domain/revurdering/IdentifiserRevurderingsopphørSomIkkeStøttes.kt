package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger

sealed class IdentifiserRevurderingsopphørSomIkkeStøttes {

    protected abstract fun OpphørVedRevurdering.Ja.opphørsdatoErTidligesteDatoIRevurdering(): Boolean

    data class UtenBeregning(
        private val vilkårsvurderinger: Vilkårsvurderinger,
        private val periode: Periode,
    ) : IdentifiserRevurderingsopphørSomIkkeStøttes() {
        val resultat: Either<Set<RevurderingsutfallSomIkkeStøttes>, Unit> =
            VurderOpphørVedRevurdering.Vilkårsvurderinger(vilkårsvurderinger).resultat.let { opphørVedRevurdering ->
                val utfall = mutableSetOf<RevurderingsutfallSomIkkeStøttes>()
                when (opphørVedRevurdering) {
                    is OpphørVedRevurdering.Ja -> {
                        if (opphørVedRevurdering.opphørsgrunner.count() > 1) {
                            utfall.add(RevurderingsutfallSomIkkeStøttes.OpphørAvFlereVilkår)
                        }
                        if (!opphørVedRevurdering.opphørsdatoErTidligesteDatoIRevurdering()) {
                            utfall.add(RevurderingsutfallSomIkkeStøttes.OpphørErIkkeFraFørsteMåned)
                        }
                        if (utfall.isEmpty()) Unit.right() else utfall.left()
                    }
                    OpphørVedRevurdering.Nei -> Unit.right()
                }
            }

        override fun OpphørVedRevurdering.Ja.opphørsdatoErTidligesteDatoIRevurdering(): Boolean {
            return this.opphørsdato == periode.fraOgMed
        }
    }

    data class MedBeregning(
        private val revurderingsperiode: Periode,
        private val vilkårsvurderinger: Vilkårsvurderinger,
        private val tidligereBeregning: Beregning,
        private val nyBeregning: Beregning,
    ) : IdentifiserRevurderingsopphørSomIkkeStøttes() {
        val resultat: Either<Set<RevurderingsutfallSomIkkeStøttes>, Unit> =
            VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
                vilkårsvurderinger,
                nyBeregning,
            ).resultat.let { opphørVedRevurdering ->
                val utfall = mutableSetOf<RevurderingsutfallSomIkkeStøttes>()
                when (opphørVedRevurdering) {
                    is OpphørVedRevurdering.Ja -> {
                        if (opphørVedRevurdering.opphørsgrunner.count() > 1) {
                            utfall.add(RevurderingsutfallSomIkkeStøttes.OpphørAvFlereVilkår)
                        }
                        if (!opphørVedRevurdering.opphørsdatoErTidligesteDatoIRevurdering()) {
                            utfall.add(RevurderingsutfallSomIkkeStøttes.OpphørErIkkeFraFørsteMåned)
                        }
                        if (setOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE).containsAll(opphørVedRevurdering.opphørsgrunner)) {
                            if (harAndreBeløpsendringerEnnMånederUnderMinstegrense()) {
                                utfall.add(RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon)
                            }
                            if (!fullstendigOpphør()) {
                                utfall.add(RevurderingsutfallSomIkkeStøttes.DelvisOpphør)
                            }
                        }
                        if (setOf(Opphørsgrunn.FOR_HØY_INNTEKT).containsAll(opphørVedRevurdering.opphørsgrunner)) {
                            if (harAndreBeløpsendringerEnnMånederMedBeløp0()) {
                                utfall.add(RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon)
                            }
                            if (!fullstendigOpphør()) {
                                utfall.add(RevurderingsutfallSomIkkeStøttes.DelvisOpphør)
                            }
                        }
                        if (setOf(Opphørsgrunn.UFØRHET).containsAll(opphørVedRevurdering.opphørsgrunner) && harBeløpsendringerEkskludertForventetInntekt()) {
                            utfall.add(RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon)
                        }
                        if (utfall.isEmpty()) Unit.right() else utfall.left()
                    }
                    OpphørVedRevurdering.Nei -> Unit.right()
                }
            }

        private fun fullstendigOpphør(): Boolean =
            nyBeregning.alleMånederErUnderMinstebeløp() || nyBeregning.alleMånederHarBeløpLik0()

        private fun harAndreBeløpsendringerEnnMånederUnderMinstegrense(): Boolean {
            return harBeløpsendringer(nyBeregning.getMånedsberegninger().filterNot { it.erSumYtelseUnderMinstebeløp() })
        }

        private fun harAndreBeløpsendringerEnnMånederMedBeløp0(): Boolean {
            return harBeløpsendringer(
                nyBeregning.getMånedsberegninger()
                    .filterNot { !it.erSumYtelseUnderMinstebeløp() && it.getSumYtelse() == 0 },
            )
        }

        private fun harBeløpsendringer(nyeMånedsberegninger: List<Månedsberegning>): Boolean {
            return tidligereBeregning.getMånedsberegninger().associate { it.periode to it.getSumYtelse() }
                .let { tidligereMånederOgBeløp ->
                    nyeMånedsberegninger.any { tidligereMånederOgBeløp[it.periode] != it.getSumYtelse() }
                }
        }

        private fun harBeløpsendringerEkskludertForventetInntekt(): Boolean {
            val nyeFradrag = nyBeregning.getFradrag().filterNot { it.fradragstype == Fradragstype.ForventetInntekt }
            val tidligereFradrag = tidligereBeregning.getFradrag()
                .filterNot { it.fradragstype == Fradragstype.ForventetInntekt }
                .mapNotNull { it.copy(CopyArgs.Snitt(revurderingsperiode)) }
            return tidligereFradrag != nyeFradrag
        }

        override fun OpphørVedRevurdering.Ja.opphørsdatoErTidligesteDatoIRevurdering(): Boolean {
            return this.opphørsdato == nyBeregning.getMånedsberegninger()
                .minByOrNull { it.periode.fraOgMed }!!.periode.fraOgMed
        }
    }
}

sealed class RevurderingsutfallSomIkkeStøttes {
    object OpphørOgAndreEndringerIKombinasjon : RevurderingsutfallSomIkkeStøttes()
    object OpphørErIkkeFraFørsteMåned : RevurderingsutfallSomIkkeStøttes()
    object DelvisOpphør : RevurderingsutfallSomIkkeStøttes()
    object OpphørAvFlereVilkår : RevurderingsutfallSomIkkeStøttes()
}
