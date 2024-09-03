package no.nav.su.se.bakover.domain.revurdering.opphør

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import behandling.revurdering.domain.Opphørsgrunn
import behandling.revurdering.domain.VilkårsvurderingerRevurdering
import beregning.domain.Beregning
import beregning.domain.Månedsberegning
import beregning.domain.harAlleMånederMerknadForAvslag
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Clock

sealed interface IdentifiserRevurderingsopphørSomIkkeStøttes {

    data class UtenBeregning(
        private val vilkårsvurderinger: VilkårsvurderingerRevurdering,
        private val periode: Periode,
    ) : IdentifiserRevurderingsopphørSomIkkeStøttes {
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

        private fun OpphørVedRevurdering.Ja.opphørsdatoErTidligesteDatoIRevurdering(): Boolean {
            return this.opphørsdato == periode.fraOgMed
        }
    }

    data class MedBeregning(
        private val revurderingsperiode: Periode,
        private val vilkårsvurderinger: VilkårsvurderingerRevurdering,
        private val gjeldendeMånedsberegninger: List<Månedsberegning>,
        private val nyBeregning: Beregning,
        private val clock: Clock,
    ) : IdentifiserRevurderingsopphørSomIkkeStøttes {
        val resultat: Either<Set<RevurderingsutfallSomIkkeStøttes>, Unit> =
            VurderOpphørVedRevurdering.VilkårsvurderingerOgBeregning(
                vilkårsvurderinger,
                nyBeregning,
                clock,
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
                        if (opphørVedRevurdering.erOpphørPgaInntekt()) {
                            if (!fullstendigOpphør()) utfall.add(RevurderingsutfallSomIkkeStøttes.DelvisOpphør)
                        }
                        if (setOf(Opphørsgrunn.UFØRHET).containsAll(opphørVedRevurdering.opphørsgrunner) && harBeløpsendringerEkskludertForventetInntekt()) {
                            utfall.add(RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon)
                        }
                        if (utfall.isEmpty()) Unit.right() else utfall.left()
                    }

                    OpphørVedRevurdering.Nei -> Unit.right()
                }
            }

        private fun fullstendigOpphør(): Boolean = nyBeregning.harAlleMånederMerknadForAvslag()

        private fun harBeløpsendringerEkskludertForventetInntekt(): Boolean {
            val nyeFradrag = nyBeregning.getMånedsberegninger().flatMap { månedsberegning ->
                månedsberegning.getFradrag().filterNot { it.fradragstype == Fradragstype.ForventetInntekt }
            }
            val tidligereFradrag = gjeldendeMånedsberegninger.flatMap { månedsberegning ->
                månedsberegning.getFradrag().filterNot { it.fradragstype == Fradragstype.ForventetInntekt }
            }.mapNotNull {
                it.copy(CopyArgs.Snitt(revurderingsperiode))
            }
            return tidligereFradrag != nyeFradrag
        }

        private fun OpphørVedRevurdering.Ja.opphørsdatoErTidligesteDatoIRevurdering(): Boolean {
            return this.opphørsdato == nyBeregning.getMånedsberegninger()
                .minByOrNull { it.periode.fraOgMed }!!.periode.fraOgMed
        }
    }
}

sealed interface RevurderingsutfallSomIkkeStøttes {
    data object OpphørOgAndreEndringerIKombinasjon : RevurderingsutfallSomIkkeStøttes
    data object OpphørErIkkeFraFørsteMåned : RevurderingsutfallSomIkkeStøttes
    data object DelvisOpphør : RevurderingsutfallSomIkkeStøttes
    data object OpphørAvFlereVilkår : RevurderingsutfallSomIkkeStøttes
}
