package no.nav.su.se.bakover.person

import io.ktor.application.call
import io.ktor.config.ApplicationConfig
import io.ktor.http.HttpHeaders
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.azure.AzureClient
import no.nav.su.se.bakover.getProperty

const val personPath = "/person"
const val identLabel = "ident"

@KtorExperimentalAPI
fun Route.personRoutes(config: ApplicationConfig, azureClient: AzureClient, personClient: SuPersonClient) {
    get(personPath) {
        val suPersonToken = azureClient.onBehalfOFToken(call.request.header(HttpHeaders.Authorization)!!, config.getProperty("integrations.suPerson.clientId"))
        call.respond(personClient.person(ident = call.parameters[identLabel]!!, suPersonToken = suPersonToken))
    }
}