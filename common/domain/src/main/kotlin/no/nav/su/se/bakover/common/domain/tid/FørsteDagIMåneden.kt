package no.nav.su.se.bakover.common.domain.tid

import java.time.LocalDate

@JvmInline
value class FørsteDagIMåneden(val dato: LocalDate) {
    init {
        require(dato.erFørsteDagIMåned()) {
            "$dato må være den 1. i måneden for å mappes til en fraogmed-dato."
        }
    }
}

fun LocalDate.somFørsteDagIMåneden(): FørsteDagIMåneden = FørsteDagIMåneden(this)

fun LocalDate.tilFørsteDagIMåneden(): FørsteDagIMåneden = startOfMonth().somFørsteDagIMåneden()

fun LocalDate.tilFørsteDagINesteMåned(): FørsteDagIMåneden = plusMonths(1).tilFørsteDagIMåneden()
