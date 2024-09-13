package no.nav.su.se.bakover.client.kodeverk

import arrow.core.Either
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken

interface Kodeverk {
    fun hentPoststed(postnummer: String, token: JwtToken): Either<CouldNotGetKode, String?>

    fun hentKommunenavn(kommunenummer: String, token: JwtToken): Either<CouldNotGetKode, String?>

    data object CouldNotGetKode
}
