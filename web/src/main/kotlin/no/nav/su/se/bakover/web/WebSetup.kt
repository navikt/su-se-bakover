package no.nav.su.se.bakover.web

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callid.generate
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.routing.Route
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.CORRELATION_ID_HEADER
import no.nav.su.se.bakover.common.infrastructure.brukerrolle.AzureGroupMapper
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.authHeader
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.jacksonConverter
import no.nav.su.se.bakover.common.person.UgyldigFnrException
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.web.routes.installMetrics
import no.nav.su.se.bakover.web.routes.naisPaths
import no.nav.su.se.bakover.web.routes.naisRoutes
import no.nav.su.se.bakover.web.services.AccessCheckProxy
import no.nav.su.se.bakover.web.services.Services
import no.nav.su.se.bakover.web.services.Tilgangssjekkfeil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import person.domain.KunneIkkeHentePerson
import tilbakekreving.application.service.Tilbakekrevingskomponenter
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock
import java.time.format.DateTimeParseException

internal fun Application.setupKtor(
    services: Services,
    tilbakekrevingskomponenter: Tilbakekrevingskomponenter,
    clock: Clock,
    applicationConfig: ApplicationConfig,
    accessCheckProxy: AccessCheckProxy,
    formuegrenserFactoryIDag: FormuegrenserFactory,
    databaseRepos: DatabaseRepos,
    clients: Clients,
    extraRoutes: Route.(services: Services) -> Unit,
) {
    setupKtorExceptionHandling()

    val (collectorRegistry, prometheusMeterRegistry) = SuMetrics.setup()
    installMetrics(prometheusMeterRegistry)
    naisRoutes(collectorRegistry)

    configureAuthentication(clients.oauth, applicationConfig, clients.tokenOppslag)
    val azureGroupMapper = AzureGroupMapper(applicationConfig.azure.groups)

    install(ContentNegotiation) {
        register(ContentType.Application.Json, jacksonConverter())
    }

    setupKtorCallId()
    setupKtorCallLogging()

    install(XForwardedHeaders)
    setupKtorRoutes(
        services = services,
        clock = clock,
        applicationConfig = applicationConfig,
        accessCheckProxy = accessCheckProxy,
        extraRoutes = extraRoutes,
        azureGroupMapper = azureGroupMapper,
        formuegrenserFactoryIDag = formuegrenserFactoryIDag,
        databaseRepos = databaseRepos,
        clients = clients,
        tilbakekrevingskomponenter = tilbakekrevingskomponenter,
    )
}

private fun Application.setupKtorCallLogging() {
    install(CallLogging) {
        level = Level.INFO
        filter { call ->
            if (call.request.httpMethod.value == "OPTIONS") return@filter false
            if (call.pathShouldBeExcluded(naisPaths)) return@filter false

            return@filter true
        }
        callIdMdc(CORRELATION_ID_HEADER)

        mdc("Authorization") { it.authHeader() }
        disableDefaultColors()
    }
}

private fun Application.setupKtorCallId() {
    install(CallId) {
        header(XCorrelationId)
        this.generate(length = 17)
        verify { it.isNotEmpty() }
    }
}

private fun Application.setupKtorExceptionHandling(
    log: Logger = LoggerFactory.getLogger("Application.setupKtorExceptionHandling"),
) {
    install(StatusPages) {
        exception<Tilgangssjekkfeil> { call, cause ->
            when (cause.feil) {
                is KunneIkkeHentePerson.IkkeTilgangTilPerson -> {
                    call.sikkerlogg("slo opp person hen ikke har tilgang til")
                    log.warn("[Tilgangssjekk] Ikke tilgang til person.", cause)
                    call.svar(Feilresponser.ikkeTilgangTilPerson)
                }

                is KunneIkkeHentePerson.FantIkkePerson -> {
                    log.warn("[Tilgangssjekk] Fant ikke person", cause)
                    call.svar(Feilresponser.fantIkkePerson)
                }

                is KunneIkkeHentePerson.Ukjent -> {
                    log.warn("[Tilgangssjekk] Feil ved oppslag på person", cause)
                    call.svar(Feilresponser.feilVedOppslagPåPerson)
                }
            }
        }
        exception<UgyldigFnrException> { call, cause ->
            log.warn("Got UgyldigFnrException with message=${cause.message}", cause)
            call.svar(
                BadRequest.errorJson(
                    message = cause.message ?: "Ugyldig fødselsnummer",
                    code = "ugyldig_fødselsnummer",
                ),
            )
        }
        exception<DateTimeParseException> { call, cause ->
            log.info("Got ${DateTimeParseException::class.simpleName} with message ${cause.message}")
            call.svar(
                BadRequest.errorJson(
                    message = "Ugyldig dato - datoer må være på isoformat",
                    code = "ugyldig_dato",
                ),
            )
        }
        exception<Throwable> { call, cause ->
            log.error("Got Throwable with message=${cause.message}", cause)
            call.svar(Feilresponser.ukjentFeil)
        }
    }
}

private fun ApplicationCall.pathShouldBeExcluded(paths: List<String>): Boolean {
    return paths.any {
        this.request.path().startsWith(it)
    }
}
