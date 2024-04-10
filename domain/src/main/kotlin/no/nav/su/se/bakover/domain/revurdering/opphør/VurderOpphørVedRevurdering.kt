package no.nav.su.se.bakover.domain.revurdering.opphør

import arrow.core.getOrElse
import behandling.revurdering.domain.Opphørsgrunn
import behandling.revurdering.domain.VilkårsvurderingerRevurdering
import behandling.revurdering.domain.tilOpphørsgrunn
import beregning.domain.Beregning
import beregning.domain.Merknad
import beregning.domain.finnFørsteMånedMedMerknadForAvslag
import beregning.domain.finnMånederMedMerknadForAvslag
import beregning.domain.harAlleMånederMerknadForAvslag
import no.nav.su.se.bakover.common.domain.tid.startOfMonth
import vilkår.common.domain.Vurdering
import java.time.Clock
import java.time.LocalDate

sealed interface VurderOpphørVedRevurdering {

    data class Vilkårsvurderinger(
        private val vilkårsvurderinger: VilkårsvurderingerRevurdering,
    ) : VurderOpphørVedRevurdering {
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
        private val vilkårsvurderinger: VilkårsvurderingerRevurdering,
        private val beregning: Beregning,
        private val clock: Clock,
    ) : VurderOpphørVedRevurdering {
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

sealed interface OpphørVedRevurdering {
    data class Ja(val opphørsgrunner: List<Opphørsgrunn>, val opphørsdato: LocalDate) : OpphørVedRevurdering
    data object Nei : OpphørVedRevurdering
}

data class VurderOmVilkårGirOpphørVedRevurdering(
    private val vilkårsvurderinger: VilkårsvurderingerRevurdering,
) {
    val resultat = vilkårGirOpphør()

    private fun vilkårGirOpphør(): OpphørVedRevurdering {
        return when (vilkårsvurderinger.resultat()) {
            Vurdering.Avslag -> OpphørVedRevurdering.Ja(
                opphørsgrunner = vilkårsvurderinger.avslagsgrunner.map { it }.tilOpphørsgrunn(),
                opphørsdato = vilkårsvurderinger.vilkår.filter { it.erAvslag }.minOf { it.hentTidligesteDatoForAvslag()!! },
            )

            Vurdering.Innvilget -> OpphørVedRevurdering.Nei
            Vurdering.Uavklart -> throw IllegalStateException("Et vurdert vilkår i revurdering kan ikke være uavklart. Siden vilkårene brukes på tvers av søknadsbehandling og revurdering, må den støtte at et vurdert vilkår kan være uavklart i søknadsbehandlingsøyemed.")
        }
    }
}

data class VurderOmBeregningGirOpphørVedRevurdering(
    private val beregning: Beregning,
    private val clock: Clock,
) {
    val resultat = beregningGirOpphør()

    private fun beregningGirOpphør(): OpphørVedRevurdering {
        return if (beregning.harAlleMånederMerknadForAvslag()) {
            beregning.finnFørsteMånedMedMerknadForAvslag()
                .getOrElse { throw IllegalStateException("Skal eksistere minste én måned med avslag.") }
                .let { (månedsberegning, merknad) ->
                    OpphørVedRevurdering.Ja(
                        opphørsgrunner = listOf(element = merknad.tilOpphørsgrunn()),
                        opphørsdato = månedsberegning.periode.fraOgMed,
                    )
                }
        } else {
            val førsteDagInneværendeMåned = LocalDate.now(clock).startOfMonth()
            beregning.finnMånederMedMerknadForAvslag()
                .getOrElse { return OpphørVedRevurdering.Nei }
                .firstOrNull { (månedsberegning, _) -> månedsberegning.periode.fraOgMed >= førsteDagInneværendeMåned }
                ?.let { (månedsberegning, merknad) ->
                    OpphørVedRevurdering.Ja(
                        opphørsgrunner = listOf(element = merknad.tilOpphørsgrunn()),
                        opphørsdato = månedsberegning.periode.fraOgMed,
                    )
                } ?: OpphørVedRevurdering.Nei
        }
    }

    private fun Merknad.Beregning.tilOpphørsgrunn(): Opphørsgrunn {
        return when (this) {
            is Merknad.Beregning.Avslag.BeløpErNull -> Opphørsgrunn.FOR_HØY_INNTEKT
            is Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats -> Opphørsgrunn.SU_UNDER_MINSTEGRENSE
            else -> throw IllegalStateException("Merknad av type: ${this::class} skal ikke gi opphør.")
        }
    }
}
