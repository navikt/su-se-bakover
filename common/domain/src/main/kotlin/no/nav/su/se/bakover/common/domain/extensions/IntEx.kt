package no.nav.su.se.bakover.common.domain.extensions

fun Int.toStringWithDecimals(decimalPlaces: Int): String {
    if (decimalPlaces <= 0) return this.toString()
    return "$this.${"0".repeat(decimalPlaces)}"
}
