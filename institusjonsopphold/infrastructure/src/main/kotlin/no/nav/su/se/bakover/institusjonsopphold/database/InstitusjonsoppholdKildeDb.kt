package no.nav.su.se.bakover.institusjonsopphold.database

import no.nav.su.se.bakover.domain.InstitusjonsoppholdKilde

enum class InstitusjonsoppholdKildeDb {
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

    companion object {
        fun InstitusjonsoppholdKilde.toJson(): InstitusjonsoppholdKildeDb = when (this) {
            InstitusjonsoppholdKilde.APPBRK -> APPBRK
            InstitusjonsoppholdKilde.INST -> INST
            InstitusjonsoppholdKilde.KDI -> KDI
            InstitusjonsoppholdKilde.IT -> IT
        }
    }
}
