package no.nav.su.se.bakover.web.routes.sak

import arrow.core.Either
import arrow.core.flatMap
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.lesFnr
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.behandling.jsonBody
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.toUUID
import no.nav.su.se.bakover.web.withSakId

internal const val sakPath = "/saker"

@KtorExperimentalAPI
internal fun Route.sakRoutes(
    behandlingService: BehandlingService,
    sakService: SakService
) {
    get(sakPath) {
        call.lesFnr("fnr").fold(
            ifLeft = { call.svar(BadRequest.message(it)) },
            ifRight = { fnr ->
                sakService.hentSak(fnr)
                    .mapLeft { call.svar(NotFound.message("Fant ikke noen sak for person: $fnr")) }
                    .map {
                        call.audit("Hentet sak for fnr: $fnr")
                        call.svar(Resultat.json(OK, serialize((it.toJson()))))
                    }
            }
        )
    }

    get("$sakPath/{sakId}") {
        call.withSakId { sakId ->
            call.svar(
                sakService.hentSak(sakId).fold(
                    { NotFound.message("Fant ikke sak med id: $sakId") },
                    { Resultat.json(OK, serialize((it.toJson()))) }
                )
            )
        }
    }

    data class OpprettBehandlingBody(val soknadId: String)

    authorize(Brukerrolle.Saksbehandler) {
        post("$sakPath/{sakId}/behandlinger") {
            call.withSakId { sakId ->
                Either.catch { deserialize<OpprettBehandlingBody>(call) }
                    .flatMap { it.soknadId.toUUID() }
                    .fold(
                        ifLeft = { call.svar(BadRequest.message("Ugyldig body")) },
                        ifRight = { søknadId ->
                            behandlingService.opprettSøknadsbehandling(søknadId)
                                .fold(
                                    { call.svar(NotFound.message("Fant ikke søknad med id:$søknadId")) },
                                    {
                                        call.audit("Opprettet behandling på sak: $sakId og søknadId: $søknadId")
                                        call.svar(HttpStatusCode.Created.jsonBody(it))
                                    }
                                )
                        }
                    )
            }
        }
    }
}
