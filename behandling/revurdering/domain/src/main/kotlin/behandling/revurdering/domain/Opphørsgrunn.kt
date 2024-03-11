package behandling.revurdering.domain

import vilkår.common.domain.Avslagsgrunn

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
    INNLAGT_PÅ_INSTITUSJON,
    ;

    // TODO: bør lage en paragraf-type/enum - Se Avslagsgrunn.kt
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

fun Avslagsgrunn.tilOpphørsgrunn(): Opphørsgrunn {
    return when (this) {
        Avslagsgrunn.UFØRHET -> Opphørsgrunn.UFØRHET
        Avslagsgrunn.FLYKTNING -> Opphørsgrunn.FLYKTNING
        Avslagsgrunn.OPPHOLDSTILLATELSE -> Opphørsgrunn.OPPHOLDSTILLATELSE
        Avslagsgrunn.PERSONLIG_OPPMØTE -> Opphørsgrunn.PERSONLIG_OPPMØTE
        Avslagsgrunn.FORMUE -> Opphørsgrunn.FORMUE
        Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE -> Opphørsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE
        Avslagsgrunn.FOR_HØY_INNTEKT -> Opphørsgrunn.FOR_HØY_INNTEKT
        Avslagsgrunn.SU_UNDER_MINSTEGRENSE -> Opphørsgrunn.SU_UNDER_MINSTEGRENSE
        Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER -> Opphørsgrunn.UTENLANDSOPPHOLD
        Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON -> Opphørsgrunn.INNLAGT_PÅ_INSTITUSJON
        Avslagsgrunn.MANGLENDE_DOKUMENTASJON -> Opphørsgrunn.MANGLENDE_DOKUMENTASJON
        Avslagsgrunn.SØKNAD_MANGLER_DOKUMENTASJON -> TODO()
        Avslagsgrunn.FAMILIEGJENFORENING -> TODO("legg inn opphørsgrunn når det skal revurderes")
        Avslagsgrunn.MANGLER_VEDTAK_ALDERSPENSJON_FOLKETRYGDEN -> TODO("Gjør det mulig å revurdere vilkåret + brev + etc")
        Avslagsgrunn.MANGLER_VEDTAK_ANDRE_NORSKE_PENSJONSORDNINGER -> TODO("Gjør det mulig å revurdere vilkåret + brev + etc")
        Avslagsgrunn.MANGLER_VEDTAK_UTENLANDSKE_PENSJONSORDNINGER -> TODO("Gjør det mulig å revurdere vilkåret + brev + etc")
    }
}

fun List<Opphørsgrunn>.getDistinkteParagrafer(): List<Int> =
    this.map { it.getParagrafer() }.flatten().distinct().sorted()

fun List<Avslagsgrunn>.tilOpphørsgrunn(): List<Opphørsgrunn> = this.map { it.tilOpphørsgrunn() }
