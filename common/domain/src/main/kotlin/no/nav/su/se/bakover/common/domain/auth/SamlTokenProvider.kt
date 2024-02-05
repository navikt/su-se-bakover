package no.nav.su.se.bakover.common.domain.auth

import arrow.core.Either

interface SamlTokenProvider {
    fun samlToken(): Either<KunneIkkeHenteSamlToken, SamlToken>
}
data object KunneIkkeHenteSamlToken
