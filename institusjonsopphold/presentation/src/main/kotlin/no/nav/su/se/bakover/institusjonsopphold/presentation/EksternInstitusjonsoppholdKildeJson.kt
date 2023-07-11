package no.nav.su.se.bakover.institusjonsopphold.presentation

import no.nav.su.se.bakover.domain.InstitusjonsoppholdKilde

/**
 * https://github.com/navikt/institusjon/blob/main/apps/institusjon-opphold-hendelser/src/main/java/no/nav/opphold/hendelser/producer/domain/Kilde.java
 */
enum class EksternInstitusjonsoppholdKildeJson {
    APPBRK,
    INST,
    KDI,
    IT,
    ;

    fun toDomain(): InstitusjonsoppholdKilde = when (this) {
        APPBRK -> InstitusjonsoppholdKilde.APPBRK
        INST -> InstitusjonsoppholdKilde.INST
        KDI -> InstitusjonsoppholdKilde.KDI
        IT -> InstitusjonsoppholdKilde.IT
    }
}
