package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.Clock
import java.time.LocalDate

data class VurderOpphørVedRevurdering(
    val vilkårsvurderinger: Vilkårsvurderinger,
    val beregning: Beregning,
    val clock: Clock = Clock.systemUTC(),
) {
    val resultat = when {
        vilkårGirOpphør() is OpphørVedRevurdering.Ja -> vilkårGirOpphør()
        beregningGirOpphør() is OpphørVedRevurdering.Ja -> beregningGirOpphør()
        else -> OpphørVedRevurdering.Nei
    }

    private fun vilkårGirOpphør(): OpphørVedRevurdering {
        return if (vilkårsvurderinger.resultat == Resultat.Avslag) {
            OpphørVedRevurdering.Ja(vilkårsvurderinger.utledOpphørsgrunner(), vilkårsvurderinger.tidligsteDatoFrorAvslag()!!)
        } else {
            OpphørVedRevurdering.Nei
        }
    }

    private fun beregningGirOpphør(): OpphørVedRevurdering {
        fun fullstendigOpphør(): Boolean {
            return beregning.getMånedsberegninger().all { it.erSumYtelseUnderMinstebeløp() } || beregning.getMånedsberegninger().all { it.getSumYtelse() == 0 }
        }

        if (fullstendigOpphør()) {
            when {
                beregning.getMånedsberegninger().all { it.erSumYtelseUnderMinstebeløp() } -> return OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE), beregning.getMånedsberegninger().first().periode.fraOgMed)
                beregning.getMånedsberegninger().all { !it.erSumYtelseUnderMinstebeløp() && it.getSumYtelse() == 0 } -> return OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.FOR_HØY_INNTEKT), beregning.getMånedsberegninger().first().periode.fraOgMed)
            }
        }

        førsteFremtidigeMånedUnderMinstebeløp()?.let {
            return OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE), it.periode.fraOgMed)
        }
        førsteFremtidigeMånedBeløpLik0()?.let {
            return OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.FOR_HØY_INNTEKT), it.periode.fraOgMed)
        }
        return OpphørVedRevurdering.Nei
    }

    private fun førsteFremtidigeMånedUnderMinstebeløp(): Månedsberegning? {
        val førsteDagInneværendeMåned = LocalDate.now(clock).startOfMonth()
        return beregning.getMånedsberegninger().sortedBy { it.periode.fraOgMed }
            .firstOrNull() { it.periode.starterSamtidigEllerSenere(førsteDagInneværendeMåned) && it.erSumYtelseUnderMinstebeløp() }
    }

    private fun førsteFremtidigeMånedBeløpLik0(): Månedsberegning? {
        val førsteDagInneværendeMåned = LocalDate.now(clock).startOfMonth()
        return beregning.getMånedsberegninger().sortedBy { it.periode.fraOgMed }
            .firstOrNull() { it.periode.starterSamtidigEllerSenere(førsteDagInneværendeMåned) && !it.erSumYtelseUnderMinstebeløp() && it.getSumYtelse() == 0 }
    }

    private fun Periode.starterSamtidigEllerSenere(other: LocalDate) =
        this.fraOgMed.isEqual(other) || this.fraOgMed.isAfter(other)
}

sealed class OpphørVedRevurdering {
    data class Ja(val grunn: List<Opphørsgrunn>, val opphørsdato: LocalDate) : OpphørVedRevurdering()
    object Nei : OpphørVedRevurdering()
}
