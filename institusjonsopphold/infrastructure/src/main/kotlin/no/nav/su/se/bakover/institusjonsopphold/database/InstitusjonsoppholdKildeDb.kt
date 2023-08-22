package no.nav.su.se.bakover.institusjonsopphold.database

import no.nav.su.se.bakover.domain.InstitusjonsoppholdKilde

enum class InstitusjonsoppholdKildeDb {
    APPBRK,
    INST,
    KDI,
    IT,
    ;

    fun toDomain(): InstitusjonsoppholdKilde = when (this) {
        APPBRK -> InstitusjonsoppholdKilde.Applikasjonsbruker
        INST -> InstitusjonsoppholdKilde.Institusjon
        KDI -> InstitusjonsoppholdKilde.Kriminalomsorgsdirektoratet
        IT -> InstitusjonsoppholdKilde.Infotrygd
    }

    companion object {
        fun InstitusjonsoppholdKilde.toJson(): InstitusjonsoppholdKildeDb = when (this) {
            InstitusjonsoppholdKilde.Applikasjonsbruker -> APPBRK
            InstitusjonsoppholdKilde.Institusjon -> INST
            InstitusjonsoppholdKilde.Kriminalomsorgsdirektoratet -> KDI
            InstitusjonsoppholdKilde.Infotrygd -> IT
        }
    }
}
