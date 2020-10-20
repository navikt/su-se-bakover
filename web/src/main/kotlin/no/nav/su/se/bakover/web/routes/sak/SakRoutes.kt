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
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.foldBoth
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.lesFnr
import no.nav.su.se.bakover.web.lesUUID
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.behandling.jsonBody
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.toUUID

internal const val sakPath = "/saker"

@KtorExperimentalAPI
internal fun Route.sakRoutes(
    behandlingService: BehandlingService,
    sakService: SakService
) {
    get(sakPath) {
        call.lesFnr("fnr")
            .mapLeft { BadRequest.message(it) }
            .flatMap { fnr ->
                call.suUserContext.assertBrukerHarTilgangTilPerson(fnr)

                sakService.hentSak(fnr)
                    .mapLeft { NotFound.message("Fant ikke noen sak for person: $fnr") }
                    .map {
                        call.audit("Hentet sak for fnr: $fnr")
                        Resultat.json(OK, serialize((it.toJson())))
                    }
            }
            .foldBoth { call.svar(it) }
    }

    get("$sakPath/{sakId}") {
        getSak(call)
            .map { sak ->
                Resultat.json(OK, serialize(sak.toJson()))
            }
            .foldBoth { call.svar(it) }
    }

    data class OpprettBehandlingBody(val soknadId: String)

    authorize(Brukerrolle.Saksbehandler) {
        post("$sakPath/{sakId}/behandlinger") {
            getSak(call)
                .flatMap { sak ->
                    Either.catch { deserialize<OpprettBehandlingBody>(call) }
                        .flatMap { it.soknadId.toUUID() }
                        .mapLeft { BadRequest.message("Ugyldig body") }
                        .map { søknadId ->
                            call.audit("Oppretter behandling på sak: ${sak.id} og søknadId: $søknadId")
                            behandlingService.opprettSøknadsbehandling(sak.id, søknadId)
                                .fold(
                                    { NotFound.message("Fant ikke søknad med id:$søknadId") },
                                    { HttpStatusCode.Created.jsonBody(it) }
                                )
                        }
                }
                .foldBoth { call.svar(it) }
        }
    }
}

internal fun getSak(call: ApplicationCall) =
    call.lesUUID("sakId")
        .mapLeft { BadRequest.message(it) }
        .flatMap { sakId ->
            call.suUserContext.assertBrukerHarTilgangTilPerson(sakId)
                .mapLeft { NotFound.message("Fant ikke sak med id $sakId") }
        }
        .map { it.second }
