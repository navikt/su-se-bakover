package no.nav.su.se.bakover.common.domain.extensions

inline fun Boolean.and(predicate: () -> Boolean): Boolean {
    return this && predicate()
}
