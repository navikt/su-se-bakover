package no.nav.su.se.bakover.institusjonsopphold.database

import no.nav.su.se.bakover.domain.InstitusjonsoppholdType

enum class InstitusjonsoppholdTypeDb {
    INNMELDING,
    OPPDATERING,
    UTMELDING,
    ANNULERING,
    ;

    fun toDomain(): InstitusjonsoppholdType = when (this) {
        INNMELDING -> InstitusjonsoppholdType.INNMELDING
        OPPDATERING -> InstitusjonsoppholdType.OPPDATERING
        UTMELDING -> InstitusjonsoppholdType.UTMELDING
        ANNULERING -> InstitusjonsoppholdType.ANNULERING
    }

    companion object {
        fun InstitusjonsoppholdType.toJson(): InstitusjonsoppholdTypeDb = when (this) {
            InstitusjonsoppholdType.INNMELDING -> INNMELDING
            InstitusjonsoppholdType.OPPDATERING -> OPPDATERING
            InstitusjonsoppholdType.UTMELDING -> UTMELDING
            InstitusjonsoppholdType.ANNULERING -> ANNULERING
        }
    }
}
