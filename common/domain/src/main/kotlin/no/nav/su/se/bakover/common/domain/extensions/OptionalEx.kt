package no.nav.su.se.bakover.common.domain.extensions

import java.util.Optional

fun <T> Optional<T>.orNull(): T? = if (this.isPresent) this.get() else null
