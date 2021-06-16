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
    private val vilkårsvurderinger: Vilkårsvurderinger,
    private val beregning: Beregning,
    private val clock: Clock = Clock.systemUTC(),
) {
    val resultat = when (
        val opphør = setOf(
            VurderOmVilkårGirOpphørVedRevurdering(vilkårsvurderinger).resultat,
            VurderOmBeregningGirOpphørVedRevurdering(beregning, clock).resultat,
        ).filterIsInstance<OpphørVedRevurdering.Ja>()
            .firstOrNull()
    ) {
        is OpphørVedRevurdering.Ja -> opphør
        else -> OpphørVedRevurdering.Nei
    }
}

sealed class OpphørVedRevurdering {
    data class Ja(val opphørsgrunner: List<Opphørsgrunn>, val opphørsdato: LocalDate) : OpphørVedRevurdering()
    object Nei : OpphørVedRevurdering()
}

data class VurderOmVilkårGirOpphørVedRevurdering(
    private val vilkårsvurderinger: Vilkårsvurderinger,
) {
    val resultat = vilkårGirOpphør()

    private fun vilkårGirOpphør(): OpphørVedRevurdering {
        return when (vilkårsvurderinger.resultat) {
            Resultat.Avslag -> OpphørVedRevurdering.Ja(
                vilkårsvurderinger.utledOpphørsgrunner(),
                vilkårsvurderinger.tidligsteDatoForAvslag()!!,
            )
            Resultat.Innvilget -> OpphørVedRevurdering.Nei
            Resultat.Uavklart -> throw IllegalStateException("Alle vilkår må vurderes før opphør kan vurderes.")
        }
    }
}

data class VurderOmBeregningGirOpphørVedRevurdering(
    private val beregning: Beregning,
    private val clock: Clock = Clock.systemUTC(),
) {
    val resultat = beregningGirOpphør()

    private fun beregningGirOpphør(): OpphørVedRevurdering {
        if (fullstendigOpphør()) {
            when {
                beregning.alleMånederErUnderMinstebeløp() -> return OpphørVedRevurdering.Ja(
                    listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE),
                    beregning.getMånedsberegninger().first().periode.fraOgMed,
                )
                beregning.alleMånederHarBeløpLik0() -> return OpphørVedRevurdering.Ja(
                    listOf(Opphørsgrunn.FOR_HØY_INNTEKT),
                    beregning.getMånedsberegninger().first().periode.fraOgMed,
                )
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

    private fun fullstendigOpphør(): Boolean = beregning.alleMånederErUnderMinstebeløp() || beregning.alleMånederHarBeløpLik0()

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
