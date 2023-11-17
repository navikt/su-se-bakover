package no.nav.su.se.bakover.common.infrastructure.mdc

import arrow.core.Either
import org.slf4j.MDC

fun putInMdcIfMissing(key: String, value: String) {
    if (getOrNullFromMdc(key) != value) {
        MDC.put(key, value)
    }
}

fun hasInMdc(key: String): Boolean = getOrNullFromMdc(key) != null

fun getOrNullFromMdc(key: String): String? = Either.catch {
    MDC.get(key)
}.getOrNull()
