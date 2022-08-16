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
    INNLAGT_PÅ_INSTITUSJON,
    MANGLENDE_DOKUMENTASJON,
    SØKNAD_MANGLER_DOKUMENTASJON,
    PENSJON, // TODO: Fjern denne fra listen, og rydd opp i basen i dev
    MANGLER_VEDTAK_ALDERSPENSJON_FOLKETRYGDEN,
    MANGLER_VEDTAK_ANDRE_NORSKE_PENSJONSORDNINGER,
    MANGLER_VEDTAK_UTENLANDSKE_PENSJONSORDNINGER,
    FAMILIEGJENFORENING;

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
        FLYKTNING -> listOf(3)
        OPPHOLDSTILLATELSE -> listOf(1, 2)
        PERSONLIG_OPPMØTE -> listOf(17)
        FORMUE -> listOf(8)
        BOR_OG_OPPHOLDER_SEG_I_NORGE -> listOf(1, 2, 3, 4)
        FOR_HØY_INNTEKT -> listOf(5, 6, 7)
        SU_UNDER_MINSTEGRENSE -> listOf(5, 6, 9)
        UTENLANDSOPPHOLD_OVER_90_DAGER -> listOf(1, 2, 4)
        INNLAGT_PÅ_INSTITUSJON -> listOf(12)
        MANGLENDE_DOKUMENTASJON -> listOf(18)
        PENSJON -> TODO("SKAL FJERNES")
        SØKNAD_MANGLER_DOKUMENTASJON -> listOf(18)
        MANGLER_VEDTAK_ALDERSPENSJON_FOLKETRYGDEN -> listOf(3)
        MANGLER_VEDTAK_ANDRE_NORSKE_PENSJONSORDNINGER -> listOf(3)
        MANGLER_VEDTAK_UTENLANDSKE_PENSJONSORDNINGER -> listOf(3)
        FAMILIEGJENFORENING -> listOf(3)
    }

    fun tilOpphørsgrunn(): Opphørsgrunn {
        return when (this) {
            UFØRHET -> Opphørsgrunn.UFØRHET
            FLYKTNING -> Opphørsgrunn.FLYKTNING
            OPPHOLDSTILLATELSE -> Opphørsgrunn.OPPHOLDSTILLATELSE
            PERSONLIG_OPPMØTE -> Opphørsgrunn.PERSONLIG_OPPMØTE
            FORMUE -> Opphørsgrunn.FORMUE
            BOR_OG_OPPHOLDER_SEG_I_NORGE -> Opphørsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE
            FOR_HØY_INNTEKT -> Opphørsgrunn.FOR_HØY_INNTEKT
            SU_UNDER_MINSTEGRENSE -> Opphørsgrunn.SU_UNDER_MINSTEGRENSE
            UTENLANDSOPPHOLD_OVER_90_DAGER -> Opphørsgrunn.UTENLANDSOPPHOLD
            INNLAGT_PÅ_INSTITUSJON -> Opphørsgrunn.INNLAGT_PÅ_INSTITUSJON
            MANGLENDE_DOKUMENTASJON -> Opphørsgrunn.MANGLENDE_DOKUMENTASJON
            SØKNAD_MANGLER_DOKUMENTASJON -> TODO()
            FAMILIEGJENFORENING -> TODO("legg inn opphørsgrunn når det skal revurderes")
            PENSJON -> TODO("SKAL FJERNES")
            MANGLER_VEDTAK_ALDERSPENSJON_FOLKETRYGDEN -> TODO("Gjør det mulig å revurdere vilkåret + brev + etc")
            MANGLER_VEDTAK_ANDRE_NORSKE_PENSJONSORDNINGER -> TODO("Gjør det mulig å revurdere vilkåret + brev + etc")
            MANGLER_VEDTAK_UTENLANDSKE_PENSJONSORDNINGER -> TODO("Gjør det mulig å revurdere vilkåret + brev + etc")
        }
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
    FORMUE,
    UTENLANDSOPPHOLD,
    MANGLENDE_DOKUMENTASJON,
    OPPHOLDSTILLATELSE,
    FLYKTNING,
    BOR_OG_OPPHOLDER_SEG_I_NORGE,
    PERSONLIG_OPPMØTE,
    INNLAGT_PÅ_INSTITUSJON;

    companion object {
        fun List<Opphørsgrunn>.getDistinkteParagrafer(): List<Int> =
            this.map { it.getParagrafer() }.flatten().distinct().sorted()
    }

    // TODO: bør lage en paragraf-type/enum
    fun getParagrafer() = when (this) {
        UFØRHET -> listOf(1, 2)
        FOR_HØY_INNTEKT -> listOf(5, 6, 7)
        SU_UNDER_MINSTEGRENSE -> listOf(5, 6, 9)
        FORMUE -> listOf(8)
        UTENLANDSOPPHOLD -> listOf(1, 2, 4)
        MANGLENDE_DOKUMENTASJON -> listOf(18)
        OPPHOLDSTILLATELSE -> listOf(3)
        FLYKTNING -> listOf(3)
        BOR_OG_OPPHOLDER_SEG_I_NORGE -> listOf(1, 2, 3, 4)
        PERSONLIG_OPPMØTE -> listOf(17)
        INNLAGT_PÅ_INSTITUSJON -> listOf(12)
    }
}
