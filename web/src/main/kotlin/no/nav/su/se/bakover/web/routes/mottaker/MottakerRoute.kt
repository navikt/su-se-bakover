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
import no.nav.su.se.bakover.domain.mottaker.Mottaker
import no.nav.su.se.bakover.domain.mottaker.MottakerIdentifikator
import no.nav.su.se.bakover.domain.mottaker.MottakerService
import no.nav.su.se.bakover.domain.mottaker.ReferanseType
import java.util.UUID

internal const val MOTTAKER_PATH = "/mottaker"

internal fun Route.mottakerRoutes(
    mottakerService: MottakerService,
) {
    // TODO: mulig alle disse burde ha sakid som prefiks for å sjekke tilgang etc?
    route(MOTTAKER_PATH) {
        get("/{referanseType}/{referanseId}") {
            val referanseType = call.parameters["referanseType"]
                ?.let { ReferanseType.valueOf(it.uppercase()) }
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Mangler referanseType")

            val referanseId = call.parameters["referanseId"]
                ?.let(UUID::fromString)
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Mangler referanseId")

            val mottaker = mottakerService.hentMottaker(
                MottakerIdentifikator(referanseType, referanseId),
            )

            if (mottaker == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(mottaker)
            }
        }

        post("/lagremottaker") {
            val mottaker = call.receive<Mottaker>()
            mottakerService.lagreMottaker(mottaker = mottaker).getOrElse {
                return@post call.respond(it)
            }
            call.respond(HttpStatusCode.Created)
        }

        put("/oppdatermottaker") {
            val mottaker = call.receive<Mottaker>()
            mottakerService.oppdaterMottaker(mottaker = mottaker).getOrElse {
                return@put call.respond(it)
            }
            call.respond(HttpStatusCode.OK)
        }

        post("/slett") {
            val identifikator = call.receive<MottakerIdentifikator>()
            mottakerService.slettMottaker(identifikator)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
