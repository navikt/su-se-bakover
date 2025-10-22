package no.nav.su.se.bakover.statistikk.behandling.klage

import behandling.klage.domain.Hjemmel
import behandling.klage.domain.Klagehjemler

internal fun Klagehjemler.toResultatBegrunnelse(): String {
    return this.sorted().joinToString(",") { it.toJsonformat() }
}

private fun Hjemmel.toJsonformat(): String {
    return when (this) {
        Hjemmel.SU_PARAGRAF_3 -> "SU_PARAGRAF_3"
        Hjemmel.SU_PARAGRAF_4 -> "SU_PARAGRAF_4"
        Hjemmel.SU_PARAGRAF_5 -> "SU_PARAGRAF_5"
        Hjemmel.SU_PARAGRAF_6 -> "SU_PARAGRAF_6"
        Hjemmel.SU_PARAGRAF_7 -> "SU_PARAGRAF_7"
        Hjemmel.SU_PARAGRAF_8 -> "SU_PARAGRAF_8"
        Hjemmel.SU_PARAGRAF_9 -> "SU_PARAGRAF_9"
        Hjemmel.SU_PARAGRAF_10 -> "SU_PARAGRAF_10"
        Hjemmel.SU_PARAGRAF_11 -> "SU_PARAGRAF_11"
        Hjemmel.SU_PARAGRAF_12 -> "SU_PARAGRAF_12"
        Hjemmel.SU_PARAGRAF_13 -> "SU_PARAGRAF_13"
        Hjemmel.SU_PARAGRAF_17 -> "SU_PARAGRAF_17"
        Hjemmel.SU_PARAGRAF_18 -> "SU_PARAGRAF_18"
        Hjemmel.SU_PARAGRAF_21 -> "SU_PARAGRAF_21"
        Hjemmel.SU_PARAGRAF_22 -> "SU_PARAGRAF_22"
        Hjemmel.FVL_PARAGRAF_12 -> "FVL_PARAGRAF_12"
        Hjemmel.FVL_PARAGRAF_28 -> "FVL_PARAGRAF_28"
        Hjemmel.FVL_PARAGRAF_29 -> "FVL_PARAGRAF_29"
        Hjemmel.FVL_PARAGRAF_31 -> "FVL_PARAGRAF_31"
        Hjemmel.FVL_PARAGRAF_32 -> "FVL_PARAGRAF_32"
    }
}
