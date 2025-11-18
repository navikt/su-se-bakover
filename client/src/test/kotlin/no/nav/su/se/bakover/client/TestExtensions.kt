package no.nav.su.se.bakover.client

import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.su.se.bakover.common.domain.auth.Kontekst
import no.nav.su.se.bakover.common.domain.auth.TokenContext

internal inline fun <reified T : Any> argThat(noinline predicate: (T) -> Unit): T {
    return org.mockito.kotlin.argThat {
        predicate(this)
        true
    }
}

fun runBlockingWithAuth(
    token: String = "Bearer token",
    block: suspend () -> Unit,
) {
    runBlocking {
        val tokenContextElement = Kontekst.asContextElement(TokenContext(token))
        withContext(tokenContextElement) {
            block()
        }
    }
}
