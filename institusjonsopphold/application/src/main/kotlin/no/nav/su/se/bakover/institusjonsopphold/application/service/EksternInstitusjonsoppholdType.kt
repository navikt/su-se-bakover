package no.nav.su.se.bakover.institusjonsopphold.application.service

import no.nav.su.se.bakover.domain.InstitusjonsoppholdType

/**
 * https://github.com/navikt/institusjon/blob/main/apps/institusjon-opphold-hendelser/src/main/java/no/nav/opphold/hendelser/producer/domain/Type.java
 */
enum class EksternInstitusjonsoppholdType {
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
}
