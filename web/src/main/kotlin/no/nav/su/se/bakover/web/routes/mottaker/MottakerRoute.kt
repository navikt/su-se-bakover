package no.nav.su.se.bakover.web.routes.mottaker

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import no.nav.su.se.bakover.domain.mottaker.Mottaker
import no.nav.su.se.bakover.domain.mottaker.MottakerService
import java.util.UUID

internal const val MOTTAKER_PATH = "/mottaker"

internal fun Route.mottakerRoutes(
    mottakerService: MottakerService,
) {
    route(MOTTAKER_PATH) {
        // eller kanskje behandlingsid vi får bruken
        get("/{dokumentId}") {
            val dokumentId = call.parameters["dokumentId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Mangler dokumentId")

            val mottaker = mottakerService.hentMottaker(UUID.fromString(dokumentId))

            if (mottaker == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(mottaker)
            }
        }

        post("/{dokumentId}") {
            val dokumentId = call.parameters["dokumentId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Mangler dokumentId")

            val mottaker = call.receive<Mottaker>()

            mottakerService.lagreMottaker(
                mottaker = mottaker,
                dokumentId = UUID.fromString(dokumentId),
            )

            call.respond(HttpStatusCode.Created)
        }

        put("/{dokumentId}") {
            val dokumentId = call.parameters["dokumentId"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Mangler dokumentId")

            val mottaker = call.receive<Mottaker>()

            mottakerService.oppdaterMottaker(
                mottaker = mottaker,
                dokumentId = UUID.fromString(dokumentId),
            )

            call.respond(HttpStatusCode.OK)
        }

        delete("/{dokumentId}/{mottakerId}") {
            val dokumentId = call.parameters["dokumentId"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Mangler dokumentId")

            val mottakerId = call.parameters["mottakerId"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Mangler mottakerId")

            mottakerService.slettMottaker(
                mottakerId = UUID.fromString(mottakerId),
                dokumentId = UUID.fromString(dokumentId),
            )

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
