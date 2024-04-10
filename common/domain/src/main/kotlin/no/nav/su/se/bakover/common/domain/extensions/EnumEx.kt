package no.nav.su.se.bakover.common.domain.extensions

inline fun <reified T : Enum<T>> enumContains(s: String) = enumValues<T>().any { it.name == s }
