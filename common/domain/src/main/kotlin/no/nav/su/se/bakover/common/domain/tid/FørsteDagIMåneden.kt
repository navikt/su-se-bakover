package no.nav.su.se.bakover.common.domain.tid

import java.time.LocalDate

@JvmInline
value class FørsteDagIMåneden(val dato: LocalDate) {
    init {
        require(dato.erFørsteDagIMåned()) {
            "Dato må være første dag i måneden, men var $dato"
        }
    }
}

fun LocalDate.somFørsteDagIMåneden(): FørsteDagIMåneden = FørsteDagIMåneden(this)

fun LocalDate.tilFørsteDagIMåneden(): FørsteDagIMåneden = startOfMonth().somFørsteDagIMåneden()

fun LocalDate.tilFørsteDagINesteMåned(): FørsteDagIMåneden = plusMonths(1).tilFørsteDagIMåneden()
