package no.nav.su.se.bakover.common.domain.auth

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.header
import io.ktor.server.response.respond
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext

class TokenContext(
    val token: String,
)

object Kontekst : ThreadLocal<TokenContext>()

val AuthTokenContextPlugin = createRouteScopedPlugin("AuthTokenContextPlugin") {
    onCall { call ->
        val authHeader = call.request.header(HttpHeaders.Authorization)
        if (authHeader == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return@onCall
        }

        val tokenContextElement = Kontekst.asContextElement(TokenContext(authHeader))

        // Intercept the pipeline to run the rest of the call in the new context
        call.application.intercept(ApplicationCallPipeline.Call) {
            withContext(tokenContextElement) {
                proceed()
            }
        }
    }
}
