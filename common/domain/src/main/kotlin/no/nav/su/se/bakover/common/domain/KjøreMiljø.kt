package no.nav.su.se.bakover.common.domain

enum class KjøreMiljø {
    PROD,
    DEV,
    LOCAL,
    UNITTEST,
    ;

    fun erDevEllerLokalt() = this == DEV || this == LOCAL
}
