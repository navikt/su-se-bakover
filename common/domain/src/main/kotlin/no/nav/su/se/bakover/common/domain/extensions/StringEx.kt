package no.nav.su.se.bakover.common.domain.extensions

/** Fjerner all whitespace i strengen basert på reglene til String.isWhitespace */
fun String.trimWhitespace(): String {
    return this.filterNot { it.isWhitespace() }
}
