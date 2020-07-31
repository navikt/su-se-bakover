package no.nav.su.se.bakover.web.routes.behandling

import arrow.core.Either
import arrow.core.flatMap
import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.lesUUID
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.toUUID
import java.time.LocalDate

internal const val behandlingPath = "$sakPath/{sakId}/behandlinger"

@KtorExperimentalAPI
internal fun Route.behandlingRoutes(
    repo: ObjectRepo
) {
    data class OpprettBehandlingBody(val soknadId: String)

    post(behandlingPath) {
        call.lesUUID("sakId").fold(
            ifLeft = { call.svar(BadRequest.message(it)) },
            ifRight = { sakId ->
                Either.catch { deserialize<OpprettBehandlingBody>(call) }
                    .flatMap { it.soknadId.toUUID() }
                    .fold(
                        ifLeft = { call.svar(BadRequest.message("Ugyldig body")) },
                        ifRight = { søknadId ->
                            when (val sak = repo.hentSak(sakId)) {
                                null -> call.svar(NotFound.message("Fant ikke sak med id:$sakId"))
                                else -> {
                                    call.audit("Oppretter behandling på sak: $sakId og søknadId: $søknadId")
                                    val behandling = sak.opprettSøknadsbehandling(søknadId)
                                    call.svar(Created.jsonBody(behandling))
                                }
                            }
                        }
                    )
            }
        )
    }

    get("$behandlingPath/{behandlingId}") {
        call.lesUUID("behandlingId").fold(
            ifLeft = { call.svar(BadRequest.message(it)) },
            ifRight = { id ->
                call.audit("Henter behandling med id: $id")
                when (val behandling = repo.hentBehandling(id)) {
                    null -> call.svar(NotFound.message("Fant ikke behandling med id:$id"))
                    else -> call.svar(OK.jsonBody(behandling))
                }
            }
        )
    }

    data class OpprettBeregningBody(
        val fom: LocalDate,
        val tom: LocalDate,
        val sats: String
    ) {
        fun valid() = fom.dayOfMonth == 1 &&
            tom.dayOfMonth == tom.lengthOfMonth() &&
            (sats == Sats.HØY.name || sats == Sats.LAV.name)
    }
    post("$behandlingPath/{behandlingId}/beregn") {
        call.lesUUID("behandlingId").fold(
            ifLeft = { call.svar(BadRequest.message(it)) },
            ifRight = { behandlingId ->
                Either.catch { deserialize<OpprettBeregningBody>(call) }.fold(
                    ifLeft = { call.svar(BadRequest.message("Ugyldig body")) },
                    ifRight = { body ->
                        if (body.valid()) {
                            when (val behandling = repo.hentBehandling(behandlingId)) {
                                null -> call.svar(NotFound.message("Fant ikke behandling med id:$behandlingId"))
                                else -> call.svar(
                                    Created.jsonBody(
                                        behandling.opprettBeregning(
                                            fom = body.fom,
                                            tom = body.tom,
                                            sats = Sats.valueOf(body.sats)
                                        )
                                    )
                                )
                            }
                        } else {
                            call.svar(BadRequest.message("Ugyldige input-parametere for: $body"))
                        }
                    }
                )
            }
        )
    }
}
