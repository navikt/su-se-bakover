package no.nav.su.se.bakover.client.kodeverk

import arrow.core.Either

interface Kodeverk {
    fun hentPoststed(postnummer: String): Either<CouldNotGetKode, String?>

    fun hentKommunenavn(kommunenummer: String): Either<CouldNotGetKode, String?>

    data object CouldNotGetKode
}
