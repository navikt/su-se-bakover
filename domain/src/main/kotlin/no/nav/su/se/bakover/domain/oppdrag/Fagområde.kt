package no.nav.su.se.bakover.domain.oppdrag

enum class Fagområde {
    SUALDER,
    SUUFORE,
    ;

    companion object {
        fun valuesAsStrings(): List<String> {
            return values().map { it.name }
        }
    }
}
