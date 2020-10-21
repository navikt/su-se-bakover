package no.nav.su.se.bakover.service

import no.nav.su.se.bakover.domain.Fnr

// Random string resembling a FNR - totally not a valid FNR
object FnrGenerator {
    private val numbers: CharRange = '0'..'9'
    private val LENGTH = 11
    fun random() = Fnr(
        (1..LENGTH)
            .map { numbers.random() }
            .joinToString("")
    )
}
