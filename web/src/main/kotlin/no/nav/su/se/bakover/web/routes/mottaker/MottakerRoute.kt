package no.nav.su.se.bakover.web.routes.mottaker

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.domain.mottaker.LagreMottaker
import no.nav.su.se.bakover.domain.mottaker.MottakerIdentifikator
import no.nav.su.se.bakover.domain.mottaker.MottakerService
import no.nav.su.se.bakover.domain.mottaker.OppdaterMottaker
import no.nav.su.se.bakover.domain.mottaker.ReferanseTypeMottaker
import java.util.UUID

internal const val MOTTAKER_PATH = "/mottaker"

internal fun Route.mottakerRoutes(
    mottakerService: MottakerService,
) {
    route(MOTTAKER_PATH) {
        get("/{sakId}/{referanseType}/{referanseId}") {
            call.withSakId { sakId ->
                val referanseType = call.parameters["referanseType"]
                    ?.let { ReferanseTypeMottaker.valueOf(it.uppercase()) }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Mangler referanseType")

                val referanseId = call.parameters["referanseId"]
                    ?.let(UUID::fromString)
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Mangler referanseId")

                val mottaker = mottakerService.hentMottaker(
                    MottakerIdentifikator(referanseType, referanseId),
                    sakId = sakId,
                )
                val mottakerUtenFeil = mottaker.getOrElse { return@get call.respond(it) }

                if (mottakerUtenFeil == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(mottaker)
                }
            }
        }

        post("/{sakId}/lagremottaker") {
            call.withSakId { sakId ->
                val mottaker = call.receive<LagreMottaker>()
                mottakerService.lagreMottaker(mottaker = mottaker, sakId).getOrElse {
                    return@post call.respond(it)
                }
                call.respond(HttpStatusCode.Created)
            }
        }

        put("/{sakId}/oppdatermottaker") {
            call.withSakId { sakId ->
                val mottaker = call.receive<OppdaterMottaker>()
                mottakerService.oppdaterMottaker(mottaker = mottaker, sakId).getOrElse {
                    return@put call.respond(it)
                }
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/{sakId}/slett") {
            call.withSakId { sakId ->
                val identifikator = call.receive<MottakerIdentifikator>()
                mottakerService.slettMottaker(identifikator, sakId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
