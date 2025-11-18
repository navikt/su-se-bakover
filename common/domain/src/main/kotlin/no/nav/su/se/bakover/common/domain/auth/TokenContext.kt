package no.nav.su.se.bakover.common.domain.auth

class TokenContext(
    val token: String,
)

object Kontekst : ThreadLocal<TokenContext>()
