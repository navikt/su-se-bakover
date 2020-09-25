package no.nav.su.se.bakover.web.features

import arrow.core.Either
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.http.HttpHeaders
import io.ktor.request.header
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.person.MicrosoftGraphResponse

class SuUserFeature(configuration: Configuration) {
    val clients = configuration.clients

    class Configuration {
        lateinit var clients: Clients
    }

    private fun intercept(context: PipelineContext<Unit, ApplicationCall>) {
        val suUserContext = SuUserContext.from(context.call)

        if (suUserContext.user != null) {
            return
        }

        val authHeader = context.call.request.header(HttpHeaders.Authorization) ?: return

        val u = clients.microsoftGraphApiClient.hent(authHeader)
        suUserContext.user = u
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, SuUserFeature> {
        override val key = AttributeKey<SuUserFeature>("SuUserFeature")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): SuUserFeature {
            val config = Configuration().apply(configure)

            val feature = SuUserFeature(config)

            pipeline.intercept(ApplicationCallPipeline.Features) {
                feature.intercept(this)
            }

            return feature
        }
    }
}

class SuUserContext(val call: ApplicationCall) {
    var user: Either<String, MicrosoftGraphResponse>? = null

    companion object {
        private val AttributeKey = AttributeKey<SuUserContext>("SuUserContext")

        internal fun from(call: ApplicationCall) =
            call.attributes.computeIfAbsent(AttributeKey) { SuUserContext(call) }
    }

    fun getNAVIdent(): String? = user?.let { u ->
        u
            .map { it.onPremisesSamAccountName }
            .orNull()
    }
}

val ApplicationCall.suUserContext: SuUserContext
    get() = SuUserContext.from(this)
