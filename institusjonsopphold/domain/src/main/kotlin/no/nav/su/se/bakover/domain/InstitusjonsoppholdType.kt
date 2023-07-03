package no.nav.su.se.bakover.domain

/**
 * https://github.com/navikt/institusjon/blob/main/apps/institusjon-opphold-hendelser/src/main/java/no/nav/opphold/hendelser/producer/domain/Type.java
 * TODO: Finn mer ut av hva disse enum typene betyr
 */
enum class InstitusjonsoppholdType {
    INNMELDING,
    OPPDATERING,
    UTMELDING,
    ANNULERING,
}
