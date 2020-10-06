package no.nav.su.se.bakover.web.routes.sak

import arrow.core.Either
import arrow.core.flatMap
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SlettBehandlingBody
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.lesFnr
import no.nav.su.se.bakover.web.lesUUID
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.behandling.jsonBody
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.toUUID
import org.slf4j.LoggerFactory

internal const val sakPath = "/saker"

internal fun Route.sakRoutes(
    behandlingService: BehandlingService,
    sakService: SakService,
    søknadService: SøknadService
) {
    val log = LoggerFactory.getLogger(this::class.java)

    get(sakPath) {
        call.lesFnr("fnr").fold(
            ifLeft = { call.svar(BadRequest.message(it)) },
            ifRight = { fnr ->
                sakService.hentSak(fnr)
                    .mapLeft { call.svar(NotFound.message("Fant ikke noen sak for person: $fnr")) }
                    .map {
                        call.audit("Hentet sak for fnr: $fnr")
                        call.svar(OK.jsonBody(it))
                    }
            }
        )
    }

    get("$sakPath/{sakId}") {
        call.withSak(sakService) { call.svar(OK.jsonBody(it)) }
    }

    data class OpprettBehandlingBody(val soknadId: String)

    post("$sakPath/{sakId}/behandlinger") {
        call.withSak(sakService) { sak ->
            Either.catch { deserialize<OpprettBehandlingBody>(call) }
                .flatMap { it.soknadId.toUUID() }
                .fold(
                    ifLeft = { call.svar(BadRequest.message("Ugyldig body")) },
                    ifRight = { søknadId ->
                        call.audit("Oppretter behandling på sak: ${sak.id} og søknadId: $søknadId")
                        behandlingService.opprettSøknadsbehandling(sak.id, søknadId)
                            .fold(
                                { call.svar(NotFound.message("Fant ikke søknad med id:$søknadId")) },
                                { call.svar(HttpStatusCode.Created.jsonBody(it)) }
                            )
                    }
                )
        }
    }

    post("$sakPath/{sakId}/{søknadId}/avsluttSaksbehandling") {
        call.withSak(sakService) {
            Either.catch { deserialize<SlettBehandlingBody>(call) }.fold(
                ifLeft = {
                    log.info("Ugyldig behandling-body: ", it)
                    call.svar(BadRequest.message("Ugyldig body"))
                },
                ifRight = {
                    if (it.valid()) {
                        søknadService.slettBehandlingForSøknad(it.søknadId, it.avsluttetBegrunnelse)
                        behandlingService.slettBehandlingForBehandling(it.søknadId, it.avsluttetBegrunnelse)
                        call.svar(OK.message("Nice"))
                    } else {
                        call.svar(BadRequest.message("Ugyldige begrunnelse for sletting: $it"))
                    }
                }
            )
        }
    }
}

suspend fun ApplicationCall.withSak(sakService: SakService, ifRight: suspend (Sak) -> Unit) {
    this.lesUUID("sakId").fold(
        ifLeft = {
            this.svar(BadRequest.message(it))
        },
        ifRight = { sakId ->
            sakService.hentSak(sakId)
                .mapLeft { this.svar(NotFound.message("Fant ikke sak med sakId: $sakId")) }
                .map {
                    this.audit("Hentet sak med id: $sakId")
                    ifRight(it)
                }
        }
    )
}
