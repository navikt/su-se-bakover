package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.utenSosialstønad
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.Clock
import java.time.LocalDate

sealed class VurderOpphørVedRevurdering {

    data class Vilkårsvurderinger(
        private val vilkårsvurderinger: no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger,
    ) : VurderOpphørVedRevurdering() {
        val resultat = when (
            val opphør = setOf(
                VurderOmVilkårGirOpphørVedRevurdering(vilkårsvurderinger).resultat,
            ).filterIsInstance<OpphørVedRevurdering.Ja>()
                .firstOrNull()
        ) {
            is OpphørVedRevurdering.Ja -> opphør
            else -> OpphørVedRevurdering.Nei
        }
    }

    data class VilkårsvurderingerOgBeregning(
        private val vilkårsvurderinger: no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger,
        private val beregning: Beregning,
        private val clock: Clock = Clock.systemUTC(),
    ) : VurderOpphørVedRevurdering() {
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
            Resultat.Uavklart -> throw IllegalStateException("Et vurdert vilkår i revurdering kan ikke være uavklart. Siden vilkårene brukes på tvers av søknadsbehandling og revurdering, må den støtte at et vurdert vilkår kan være uavklart i søknadsbehandlingsøyemed.")
        }
    }
}

class VurderOmBeregningGirOpphørVedRevurdering(beregning: Beregning, private val clock: Clock = Clock.systemUTC()) {
    val beregning: Beregning

    init {
        this.beregning = beregning.utenSosialstønad()
    }

    val resultat = beregningGirOpphør()

    private fun beregningGirOpphør(): OpphørVedRevurdering {
        if (fullstendigOpphør(beregning)) {
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

        førsteFremtidigeMånedUnderMinstebeløp(beregning)?.let {
            return OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE), it.periode.fraOgMed)
        }
        førsteFremtidigeMånedBeløpLik0(beregning)?.let {
            return OpphørVedRevurdering.Ja(listOf(Opphørsgrunn.FOR_HØY_INNTEKT), it.periode.fraOgMed)
        }
        return OpphørVedRevurdering.Nei
    }

    private fun fullstendigOpphør(b: Beregning): Boolean =
        b.alleMånederErUnderMinstebeløp() || b.alleMånederHarBeløpLik0()

    private fun førsteFremtidigeMånedUnderMinstebeløp(beregning: Beregning): Månedsberegning? {
        val førsteDagInneværendeMåned = LocalDate.now(clock).startOfMonth()
        return beregning.getMånedsberegninger().sortedBy { it.periode.fraOgMed }
            .firstOrNull { it.periode.starterSamtidigEllerSenere(førsteDagInneværendeMåned) && it.erSumYtelseUnderMinstebeløp() }
    }

    private fun førsteFremtidigeMånedBeløpLik0(beregning: Beregning): Månedsberegning? {
        val førsteDagInneværendeMåned = LocalDate.now(clock).startOfMonth()
        return beregning.getMånedsberegninger().sortedBy { it.periode.fraOgMed }
            .firstOrNull { it.periode.starterSamtidigEllerSenere(førsteDagInneværendeMåned) && !it.erSumYtelseUnderMinstebeløp() && it.getSumYtelse() == 0 }
    }

    private fun Periode.starterSamtidigEllerSenere(other: LocalDate) =
        this.fraOgMed.isEqual(other) || this.fraOgMed.isAfter(other)
}
