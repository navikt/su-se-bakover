package no.nav.su.se.bakover.database

import vilkår.common.domain.Avslagsgrunn

enum class AvslagsgrunnDbJson {
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
    MANGLER_VEDTAK_ALDERSPENSJON_FOLKETRYGDEN,
    MANGLER_VEDTAK_ANDRE_NORSKE_PENSJONSORDNINGER,
    MANGLER_VEDTAK_UTENLANDSKE_PENSJONSORDNINGER,
    FAMILIEGJENFORENING,
}

fun Avslagsgrunn.toDbJson(): AvslagsgrunnDbJson {
    return when (this) {
        Avslagsgrunn.UFØRHET -> AvslagsgrunnDbJson.UFØRHET
        Avslagsgrunn.FLYKTNING -> AvslagsgrunnDbJson.FLYKTNING
        Avslagsgrunn.OPPHOLDSTILLATELSE -> AvslagsgrunnDbJson.OPPHOLDSTILLATELSE
        Avslagsgrunn.PERSONLIG_OPPMØTE -> AvslagsgrunnDbJson.PERSONLIG_OPPMØTE
        Avslagsgrunn.FORMUE -> AvslagsgrunnDbJson.FORMUE
        Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE -> AvslagsgrunnDbJson.BOR_OG_OPPHOLDER_SEG_I_NORGE
        Avslagsgrunn.FOR_HØY_INNTEKT -> AvslagsgrunnDbJson.FOR_HØY_INNTEKT
        Avslagsgrunn.SU_UNDER_MINSTEGRENSE -> AvslagsgrunnDbJson.SU_UNDER_MINSTEGRENSE
        Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER -> AvslagsgrunnDbJson.UTENLANDSOPPHOLD_OVER_90_DAGER
        Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON -> AvslagsgrunnDbJson.INNLAGT_PÅ_INSTITUSJON
        Avslagsgrunn.MANGLENDE_DOKUMENTASJON -> AvslagsgrunnDbJson.MANGLENDE_DOKUMENTASJON
        Avslagsgrunn.SØKNAD_MANGLER_DOKUMENTASJON -> AvslagsgrunnDbJson.SØKNAD_MANGLER_DOKUMENTASJON
        Avslagsgrunn.MANGLER_VEDTAK_ALDERSPENSJON_FOLKETRYGDEN -> AvslagsgrunnDbJson.MANGLER_VEDTAK_ALDERSPENSJON_FOLKETRYGDEN
        Avslagsgrunn.MANGLER_VEDTAK_ANDRE_NORSKE_PENSJONSORDNINGER -> AvslagsgrunnDbJson.MANGLER_VEDTAK_ANDRE_NORSKE_PENSJONSORDNINGER
        Avslagsgrunn.MANGLER_VEDTAK_UTENLANDSKE_PENSJONSORDNINGER -> AvslagsgrunnDbJson.MANGLER_VEDTAK_UTENLANDSKE_PENSJONSORDNINGER
        Avslagsgrunn.FAMILIEGJENFORENING -> AvslagsgrunnDbJson.FAMILIEGJENFORENING
    }
}

fun List<Avslagsgrunn>.toDbJson(): List<AvslagsgrunnDbJson> {
    return this.map { it.toDbJson() }
}

fun AvslagsgrunnDbJson.toDomain(): Avslagsgrunn {
    return when (this) {
        AvslagsgrunnDbJson.UFØRHET -> Avslagsgrunn.UFØRHET
        AvslagsgrunnDbJson.FLYKTNING -> Avslagsgrunn.FLYKTNING
        AvslagsgrunnDbJson.OPPHOLDSTILLATELSE -> Avslagsgrunn.OPPHOLDSTILLATELSE
        AvslagsgrunnDbJson.PERSONLIG_OPPMØTE -> Avslagsgrunn.PERSONLIG_OPPMØTE
        AvslagsgrunnDbJson.FORMUE -> Avslagsgrunn.FORMUE
        AvslagsgrunnDbJson.BOR_OG_OPPHOLDER_SEG_I_NORGE -> Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE
        AvslagsgrunnDbJson.FOR_HØY_INNTEKT -> Avslagsgrunn.FOR_HØY_INNTEKT
        AvslagsgrunnDbJson.SU_UNDER_MINSTEGRENSE -> Avslagsgrunn.SU_UNDER_MINSTEGRENSE
        AvslagsgrunnDbJson.UTENLANDSOPPHOLD_OVER_90_DAGER -> Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER
        AvslagsgrunnDbJson.INNLAGT_PÅ_INSTITUSJON -> Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON
        AvslagsgrunnDbJson.MANGLENDE_DOKUMENTASJON -> Avslagsgrunn.MANGLENDE_DOKUMENTASJON
        AvslagsgrunnDbJson.SØKNAD_MANGLER_DOKUMENTASJON -> Avslagsgrunn.SØKNAD_MANGLER_DOKUMENTASJON
        AvslagsgrunnDbJson.MANGLER_VEDTAK_ALDERSPENSJON_FOLKETRYGDEN -> Avslagsgrunn.MANGLER_VEDTAK_ALDERSPENSJON_FOLKETRYGDEN
        AvslagsgrunnDbJson.MANGLER_VEDTAK_ANDRE_NORSKE_PENSJONSORDNINGER -> Avslagsgrunn.MANGLER_VEDTAK_ANDRE_NORSKE_PENSJONSORDNINGER
        AvslagsgrunnDbJson.MANGLER_VEDTAK_UTENLANDSKE_PENSJONSORDNINGER -> Avslagsgrunn.MANGLER_VEDTAK_UTENLANDSKE_PENSJONSORDNINGER
        AvslagsgrunnDbJson.FAMILIEGJENFORENING -> Avslagsgrunn.FAMILIEGJENFORENING
    }
}
