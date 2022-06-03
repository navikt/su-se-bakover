package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import no.nav.su.se.bakover.client.maskinporten.KunneIkkeHenteToken
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Skattemelding

interface SkatteService {
    fun hentSkattemelding(fnr: Fnr): Either<KunneIkkeHenteSkattemelding, Skattemelding>
}

sealed class KunneIkkeHenteSkattemelding {
    data class KunneIkkeHenteAccessToken(val feil: KunneIkkeHenteToken) : KunneIkkeHenteSkattemelding()
    object SkatteErSlem : KunneIkkeHenteSkattemelding()
}
