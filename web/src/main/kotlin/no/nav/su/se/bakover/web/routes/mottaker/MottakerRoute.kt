package no.nav.su.se.bakover.web.routes.mottaker

import arrow.core.getOrElse
import dokument.domain.Brevtype
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
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
    fun String.tilReferanseTypeMottaker(): ReferanseTypeMottaker? =
        runCatching { ReferanseTypeMottaker.valueOf(this.uppercase()) }.getOrNull()
    fun String.tilBrevtypeForMottaker(): Brevtype? =
        Brevtype.fraString(this)
            ?.takeIf { it == Brevtype.VEDTAK || it == Brevtype.FORHANDSVARSEL || it == Brevtype.OVERSENDELSE_KA }
    route(MOTTAKER_PATH) {
        get("/{sakId}/{referanseType}/{referanseId}") {
            authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
                call.withSakId { sakId ->
                    val referanseType = call.parameters["referanseType"]
                        ?.tilReferanseTypeMottaker()
                        ?: return@withSakId call.respond(HttpStatusCode.BadRequest, "Ugyldig eller manglende referanseType")

                    val referanseId = call.parameters["referanseId"]
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@withSakId call.respond(HttpStatusCode.BadRequest, "Ugyldig eller manglende referanseId")

                    val brevtype = call.parameters["brevtype"]?.tilBrevtypeForMottaker()
                        ?: return@withSakId call.respond(HttpStatusCode.BadRequest, "Ugyldig eller manglende brevtype")

                    val mottaker = mottakerService.hentMottaker(
                        MottakerIdentifikator(referanseType, referanseId, brevtype),
                        sakId = sakId,
                    )
                    val mottakerUtenFeil = mottaker.getOrElse { return@withSakId call.respond(HttpStatusCode.BadRequest, it) }

                    if (mottakerUtenFeil == null) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        call.respond(mottakerUtenFeil)
                    }
                }
            }
        }

        post("/{sakId}/lagre") {
            authorize(Brukerrolle.Saksbehandler) {
                call.withSakId { sakId ->
                    call.withBody<LagreMottaker> { mottaker ->
                        val mottakerLagret = mottakerService.lagreMottaker(mottaker = mottaker, sakId).getOrElse {
                            return@withBody call.respond(HttpStatusCode.BadRequest, it)
                        }
                        call.respond(HttpStatusCode.Created, serialize(mottakerLagret))
                    }
                }
            }
        }

        put("/{sakId}/oppdater") {
            authorize(Brukerrolle.Saksbehandler) {
                call.withSakId { sakId ->
                    call.withBody<OppdaterMottaker> { mottaker ->
                        mottakerService.oppdaterMottaker(mottaker = mottaker, sakId).getOrElse {
                            return@withBody call.respond(HttpStatusCode.BadRequest, it)
                        }
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }

        post("/{sakId}/slett") {
            authorize(Brukerrolle.Saksbehandler) {
                call.withSakId { sakId ->
                    call.withBody<MottakerIdentifikator> { identifikator ->
                        mottakerService.slettMottaker(identifikator, sakId).getOrElse {
                            return@withBody call.respond(HttpStatusCode.BadRequest, it)
                        }
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }
}
