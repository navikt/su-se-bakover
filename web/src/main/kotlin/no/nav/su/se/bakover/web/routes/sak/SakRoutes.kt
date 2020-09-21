package no.nav.su.se.bakover.web.routes.sak

import arrow.core.Either
import arrow.core.flatMap
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.behandlinger.stopp.StoppbehandlingService
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.lesFnr
import no.nav.su.se.bakover.web.lesUUID
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.behandling.jsonBody
import no.nav.su.se.bakover.web.routes.behandllinger.stopp.toResultat
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.toUUID

internal const val sakPath = "/saker"

internal fun Route.sakRoutes(
    stoppbehandlingService: StoppbehandlingService,
    sakRepo: ObjectRepo
) {
    get(sakPath) {
        call.lesFnr("fnr").fold(
            ifLeft = { call.svar(BadRequest.message(it)) },
            ifRight = { fnr ->
                when (val sak = sakRepo.hentSak(fnr)) {
                    null -> call.svar(NotFound.message("Fant ikke noen sak for person: $fnr"))
                    else -> {
                        call.audit("Hentet sak for fnr: $fnr")
                        call.svar(OK.jsonBody(sak))
                    }
                }
            }
        )
    }

    get("$sakPath/{sakId}") {
        call.withSak(sakRepo) { call.svar(OK.jsonBody(it)) }
    }

    data class OpprettBehandlingBody(val soknadId: String)

    post("$sakPath/{sakId}/behandlinger") {
        call.withSak(sakRepo) { sak ->
            Either.catch { deserialize<OpprettBehandlingBody>(call) }
                .flatMap { it.soknadId.toUUID() }
                .fold(
                    ifLeft = { call.svar(BadRequest.message("Ugyldig body")) },
                    ifRight = { søknadId ->
                        call.audit("Oppretter behandling på sak: ${sak.id} og søknadId: $søknadId")
                        val behandling = sak.opprettSøknadsbehandling(søknadId)
                        call.svar(HttpStatusCode.Created.jsonBody(behandling))
                    }
                )
        }
    }

    post("$sakPath/{sakId}/stopp-utbetalinger") {
        call.withSak(sakRepo) { sak ->
            call.svar(
                sak.stoppUtbetalinger(
                    stoppbehandlingService = stoppbehandlingService,
                    saksbehandler = Saksbehandler(id = "saksbehandler"),
                    stoppÅrsak = "Årsaken til stoppen er ..."
                ).fold(
                    {
                        InternalServerError.message("Kunne ikke opprette stoppbehandling for sak id ${sak.id}")
                    },
                    {
                        it.toResultat(OK)
                    }
                )
            )
        }
    }
}

suspend fun ApplicationCall.withSak(repo: ObjectRepo, ifRight: suspend (Sak) -> Unit) {
    this.lesUUID("sakId").fold(
        ifLeft = {
            this.svar(BadRequest.message(it))
        },
        ifRight = { sakId ->
            repo.hentSak(sakId)?.let { sak ->
                this.audit("Hentet sak med id: $sakId")
                ifRight(sak)
            } ?: this.svar(NotFound.message("Fant ikke sak med sakId: $sakId"))
        }
    )
}
