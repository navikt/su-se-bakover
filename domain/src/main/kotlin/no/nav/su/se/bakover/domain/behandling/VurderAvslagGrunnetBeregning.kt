package no.nav.su.se.bakover.domain.behandling

import arrow.core.getOrHandle
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Merknad
import no.nav.su.se.bakover.domain.beregning.finnFørsteMånedMedMerknadForAvslag

object VurderAvslagGrunnetBeregning {

    fun vurderAvslagGrunnetBeregning(
        beregning: Beregning?,
    ): AvslagGrunnetBeregning = if (beregning == null) AvslagGrunnetBeregning.Nei else {
        beregning.finnFørsteMånedMedMerknadForAvslag()
            .getOrHandle { return AvslagGrunnetBeregning.Nei }
            .let { (_, merknad) -> AvslagGrunnetBeregning.Ja(grunn = merknad.tilAvslagsgrunn()) }
    }
}

sealed class AvslagGrunnetBeregning {
    data class Ja(val grunn: Grunn) : AvslagGrunnetBeregning()
    object Nei : AvslagGrunnetBeregning()

    enum class Grunn {
        FOR_HØY_INNTEKT,
        SU_UNDER_MINSTEGRENSE
    }
}

fun Merknad.Beregning.tilAvslagsgrunn(): AvslagGrunnetBeregning.Grunn {
    return when (this) {
        is Merknad.Beregning.Avslag.BeløpErNull -> {
            AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT
        }
        is Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats -> {
            AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE
        }
        is Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats -> {
            throw IllegalStateException("Ukjent merknad for avslag: ${this::class}")
        }
        is Merknad.Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats -> {
            throw IllegalStateException("Ukjent merknad for avslag: ${this::class}")
        }
    }
}
