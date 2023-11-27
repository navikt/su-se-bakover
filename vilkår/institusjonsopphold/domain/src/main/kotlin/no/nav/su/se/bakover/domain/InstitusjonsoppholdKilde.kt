package no.nav.su.se.bakover.domain

/**
 * https://github.com/navikt/institusjon/blob/main/apps/institusjon-opphold-hendelser/src/main/java/no/nav/opphold/hendelser/producer/domain/Kilde.java
 */
enum class InstitusjonsoppholdKilde(val verdi: String) {
    Applikasjonsbruker("APPBRK"),
    Institusjon("INST"),
    Kriminalomsorgsdirektoratet("KDI"),
    Infotrygd("IT"),
}
