package no.nav.su.se.bakover.domain.behandling.avslag

import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning

/**
 * https://confluence.adeo.no/pages/viewpage.action?pageId=414002852
 */
enum class Avslagsgrunn {
    UFØRHET,
    FLYKTNING,
    OPPHOLDSTILLATELSE,
    PERSONLIG_OPPMØTE,
    FORMUE,
    BOR_OG_OPPHOLDER_SEG_I_NORGE,
    FOR_HØY_INNTEKT,
    SU_UNDER_MINSTEGRENSE,
    UTENLANDSOPPHOLD_OVER_90_DAGER,
    INNLAGT_PÅ_INSTITUSJON;

    companion object {
        fun List<Avslagsgrunn>.getDistinkteParagrafer(): List<Int> =
            this.map { it.getParagrafer() }.flatten().distinct().sorted()

        fun AvslagGrunnetBeregning.Grunn.toAvslagsgrunn(): Avslagsgrunn {
            return when (this) {
                AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT -> FOR_HØY_INNTEKT
                AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE -> SU_UNDER_MINSTEGRENSE
            }
        }
    }

    // TODO: bør lage en paragraf-type/enum
    fun getParagrafer() = when (this) {
        UFØRHET -> listOf(1, 2)
        FLYKTNING -> listOf(1, 2)
        OPPHOLDSTILLATELSE -> listOf(1, 2)
        PERSONLIG_OPPMØTE -> listOf(17)
        FORMUE -> listOf(8)
        BOR_OG_OPPHOLDER_SEG_I_NORGE -> listOf(1, 2, 3, 4)
        FOR_HØY_INNTEKT -> listOf(5, 6, 7)
        SU_UNDER_MINSTEGRENSE -> listOf(5, 6, 9)
        UTENLANDSOPPHOLD_OVER_90_DAGER -> listOf(1, 2, 4)
        INNLAGT_PÅ_INSTITUSJON -> listOf(12)
    }
}

/**
 * Vi legger på flere grunner etterhvert som det går ann å revurdere dem
 * https://confluence.adeo.no/pages/viewpage.action?pageId=414002852
 */
enum class Opphørsgrunn {
    UFØRHET,
    FOR_HØY_INNTEKT,
    SU_UNDER_MINSTEGRENSE,
    FORMUE;

    companion object {
        fun AvslagGrunnetBeregning.Grunn?.toOpphørsgrunn(): Opphørsgrunn? {
            return when (this) {
                AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT -> FOR_HØY_INNTEKT
                AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE -> SU_UNDER_MINSTEGRENSE
                null -> null
            }
        }

        fun List<Opphørsgrunn>.getDistinkteParagrafer(): List<Int> =
            this.map { it.getParagrafer() }.flatten().distinct().sorted()
    }

    // TODO: bør lage en paragraf-type/enum
    fun getParagrafer() = when (this) {
        UFØRHET -> listOf(1, 2)
        FOR_HØY_INNTEKT -> listOf(5, 6, 7)
        SU_UNDER_MINSTEGRENSE -> listOf(5, 6, 9)
        FORMUE -> listOf(8)
    }
}
