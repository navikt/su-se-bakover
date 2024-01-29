package no.nav.su.se.bakover.domain.revurdering.opphør

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import beregning.domain.Beregning
import beregning.domain.Merknad
import beregning.domain.Månedsberegning
import beregning.domain.harAlleMånederMerknadForAvslag
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.kronologisk
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.vilkår.VilkårsvurderingerRevurdering
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Clock

sealed class IdentifiserRevurderingsopphørSomIkkeStøttes {

    protected abstract fun OpphørVedRevurdering.Ja.opphørsdatoErTidligesteDatoIRevurdering(): Boolean

    data class UtenBeregning(
        private val vilkårsvurderinger: VilkårsvurderingerRevurdering,
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
        private val vilkårsvurderinger: VilkårsvurderingerRevurdering,
        private val gjeldendeMånedsberegninger: List<Månedsberegning>,
        private val nyBeregning: Beregning,
        private val clock: Clock,
    ) : IdentifiserRevurderingsopphørSomIkkeStøttes() {
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

        private fun fullstendigOpphør(): Boolean = nyBeregning.harAlleMånederMerknadForAvslag()

        private fun harAndreBeløpsendringerEnnMånederUnderMinstegrense(): Boolean {
            return harBeløpsendringer(
                nyBeregning.getMånedsberegninger()
                    .filterNot {
                        it.getMerknader().contains(Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats)
                    },
            )
        }

        private fun harAndreBeløpsendringerEnnMånederMedBeløp0(): Boolean {
            return harBeløpsendringer(
                nyBeregning.getMånedsberegninger()
                    .filterNot {
                        !it.getMerknader()
                            .contains(Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats) && it.getSumYtelse() == 0
                    },
            )
        }

        private fun harBeløpsendringer(nyeMånedsberegninger: List<Månedsberegning>): Boolean {
            return gjeldendeMånedsberegninger.associate { it.periode to it.getSumYtelse() }
                .let { tidligereMånederOgBeløp ->
                    nyeMånedsberegninger.any { tidligereMånederOgBeløp[it.periode] != it.getSumYtelse() }
                }
        }

        private fun harBeløpsendringerEkskludertForventetInntekt(): Boolean {
            val nyeFradrag = nyBeregning.getMånedsberegninger().kronologisk().flatMap { månedsberegning ->
                månedsberegning.getFradrag().filterNot { it.fradragstype == Fradragstype.ForventetInntekt }
            }
            val tidligereFradrag = gjeldendeMånedsberegninger.kronologisk().flatMap { månedsberegning ->
                månedsberegning.getFradrag().filterNot { it.fradragstype == Fradragstype.ForventetInntekt }
            }.mapNotNull {
                it.copy(CopyArgs.Snitt(revurderingsperiode))
            }
            return tidligereFradrag != nyeFradrag
        }

        override fun OpphørVedRevurdering.Ja.opphørsdatoErTidligesteDatoIRevurdering(): Boolean {
            return this.opphørsdato == nyBeregning.getMånedsberegninger()
                .minByOrNull { it.periode.fraOgMed }!!.periode.fraOgMed
        }
    }
}

sealed class RevurderingsutfallSomIkkeStøttes {
    data object OpphørOgAndreEndringerIKombinasjon : RevurderingsutfallSomIkkeStøttes()
    data object OpphørErIkkeFraFørsteMåned : RevurderingsutfallSomIkkeStøttes()
    data object DelvisOpphør : RevurderingsutfallSomIkkeStøttes()
    data object OpphørAvFlereVilkår : RevurderingsutfallSomIkkeStøttes()
}
