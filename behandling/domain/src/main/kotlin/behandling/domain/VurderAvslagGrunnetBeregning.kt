package behandling.domain

import arrow.core.getOrElse
import beregning.domain.Beregning
import beregning.domain.Merknad
import beregning.domain.finnFørsteMånedMedMerknadForAvslag
import vilkår.common.domain.Avslagsgrunn

data object VurderAvslagGrunnetBeregning {

    fun vurderAvslagGrunnetBeregning(
        beregning: Beregning?,
    ): AvslagGrunnetBeregning = if (beregning == null) {
        AvslagGrunnetBeregning.Nei
    } else {
        beregning.finnFørsteMånedMedMerknadForAvslag()
            .getOrElse { return AvslagGrunnetBeregning.Nei }
            .let { (_, merknad) -> AvslagGrunnetBeregning.Ja(grunn = merknad.tilAvslagsgrunn()) }
    }
}

sealed interface AvslagGrunnetBeregning {
    data class Ja(val grunn: Grunn) : AvslagGrunnetBeregning
    data object Nei : AvslagGrunnetBeregning

    enum class Grunn {
        FOR_HØY_INNTEKT,
        SU_UNDER_MINSTEGRENSE,
        ;

        fun toAvslagsgrunn(): Avslagsgrunn = when (this) {
            FOR_HØY_INNTEKT -> Avslagsgrunn.FOR_HØY_INNTEKT
            SU_UNDER_MINSTEGRENSE -> Avslagsgrunn.SU_UNDER_MINSTEGRENSE
        }
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
    }
}
