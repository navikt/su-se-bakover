package no.nav.su.se.bakover.nais

import io.ktor.application.Application
import io.ktor.config.MapApplicationConfig
import io.ktor.util.KtorExperimentalAPI

@KtorExperimentalAPI
fun Application.testEnv() = (environment.config as MapApplicationConfig).apply {
    put("allowCorsOrigin", "http://localhost")
    put("integrations.suPerson.url", "http://localhost")
    put("integrations.suInntekt.url", "http://localhost")
    put("azure.tenant", "tenant")
    put("azure.requiredGroup", "ingroup")
    put("azure.clientId", "3")
}