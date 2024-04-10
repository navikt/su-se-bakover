package no.nav.su.se.bakover.common.domain.extensions

import arrow.core.Either
import java.util.UUID

fun String.toUUID(): Either<String, UUID> {
    return Either.catch { UUID.fromString(this@toUUID) }
        .mapLeft { "${this@toUUID} er ikke en gyldig UUID" }
}
