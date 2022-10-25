package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.Fnr

/**
 * 11 tilfeldige sifre mellom 0 og 9.
 * Gir sjeldent gyldige fødselsnumre :)
 */
fun Fnr.Companion.generer(): Fnr {
    val numbers: CharRange = '0'..'9'
    return Fnr(
        (1..11)
            .map { numbers.random() }
            .joinToString(""),
    )
}
