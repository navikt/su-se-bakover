package no.nav.su.se.bakover.common.infrastructure.token

import no.nav.su.se.bakover.common.domain.auth.Kontekst

/**
 * TODO jah: På sikt ønsker vi å bruke denne over rå strings der vi sender / bruker JWT-tokens: https://trello.com/c/25g4PH72/1287-pakk-inn-jwt-tokens-i-jwttoken-type-der-de-brukes
 */
sealed interface JwtToken {
    data object SystemToken : JwtToken

    // Kan ikke bruke inline class her på grunn av Mockito.verify()
    data class BrukerToken(val value: String) : JwtToken {
        companion object {
            fun fraCoroutineContext(): BrukerToken {
                val token = Kontekst.get()?.token
                    ?: throw IllegalStateException("TokenContext not set, sjekk AuthTokenContextPlugin og at install(AuthTokenContextPlugin) er kjørt på dine routes")
                return BrukerToken(token)
            }

            fun fraCoroutineContextOrNull(): BrukerToken? {
                val token = Kontekst.get()?.token
                return token?.let { BrukerToken(it) }
            }
        }

        override fun toString() = value
    }
}
