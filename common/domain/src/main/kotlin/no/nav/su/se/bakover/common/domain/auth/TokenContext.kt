package no.nav.su.se.bakover.common.domain.auth

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.Hook
import io.ktor.server.application.RouteScopedPlugin
import io.ktor.server.application.call
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelinePhase
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

data class TokenContext(val token: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TokenContext>
}

object Kontekst : ThreadLocal<TokenContext>()

fun authTokenContextPlugin(log: Logger): RouteScopedPlugin<Unit> {
    return createRouteScopedPlugin("AuthTokenContextPlugin") {
        on(TokenHook(ApplicationCallPipeline.Call)) { call, proceed ->
            val authHeader = call.request.header(HttpHeaders.Authorization)
            if (authHeader == null) {
                val method = call.request.httpMethod.value
                val path = call.request.path()
                val query = call.request.queryString().let { if (it.isNotEmpty()) "?$it" else "" }

                log.warn("Missing Authorization header for request: $method $path$query")

                call.respond(HttpStatusCode.Unauthorized)
                return@on
            }

            val tokenContextElement = Kontekst.asContextElement(TokenContext(authHeader))

            withContext(tokenContextElement) {
                proceed()
            }
        }
    }
}

@Suppress("FunctionName")
internal fun TokenHook(insertBeforePhase: PipelinePhase) =
    object : Hook<suspend (ApplicationCall, suspend () -> Unit) -> Unit> {
        override fun install(
            pipeline: ApplicationCallPipeline,
            handler: suspend (ApplicationCall, suspend () -> Unit) -> Unit,
        ) {
            val tokenPhase = PipelinePhase("${insertBeforePhase.name}Token")
            pipeline.insertPhaseBefore(insertBeforePhase, tokenPhase)

            pipeline.intercept(tokenPhase) {
                handler(call, ::proceed)
            }
        }
    }
