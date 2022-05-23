package no.nav.su.se.bakover.common.token

import org.slf4j.MDC

/**
 * TODO jah: På sikt ønsker vi å bruke denne over rå strings der vi sender / bruker JWT-tokens: https://trello.com/c/25g4PH72/1287-pakk-inn-jwt-tokens-i-jwttoken-type-der-de-brukes
 */
sealed interface JwtToken {
    object SystemToken : JwtToken
    // Kan ikke bruke inline class her på grunn av Mockito.verify()
    data class BrukerToken(val value: String) : JwtToken {
        companion object {
            fun fraMdc(): BrukerToken {
                return BrukerToken(MDC.get("Authorization"))
            }
        }
    }
}
