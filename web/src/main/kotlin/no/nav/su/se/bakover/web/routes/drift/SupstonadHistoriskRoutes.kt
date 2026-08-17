package no.nav.su.se.bakover.web.routes.drift

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.su.se.bakover.client.historisk.CountRequest
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.service.historisk.SupstonadHistoriskService
import org.slf4j.LoggerFactory
import java.util.UUID

internal fun Route.supstonadHistoriskRoutes(
    supstonadHistoriskService: SupstonadHistoriskService,
) {
    val log = LoggerFactory.getLogger("SupstonadHistoriskRoutes")

    get("$DRIFT_PATH/supstonadhistorisk/import") {
        authorize(Brukerrolle.Drift) {
            call.svar(Resultat.json(HttpStatusCode.OK, serialize(supstonadHistoriskService.hentAlleImporter())))
        }
    }

    post("$DRIFT_PATH/supstonadhistorisk/tellrader") {
        authorize(Brukerrolle.Drift) {
            call.withBody<CountRequest> { body ->
                log.info("SupstonadHistoriskRoutes: tellRader kalt for tabell '{}'", body.tabellnavn)
                supstonadHistoriskService.tellRader(body.tabellnavn).fold(
                    ifLeft = { feil ->
                        call.svar(
                            HttpStatusCode.fromValue(feil.httpStatus).errorJson(
                                message = feil.message,
                                code = "supstonad_historisk_feil",
                            ),
                        )
                    },
                    ifRight = { svar ->
                        call.svar(Resultat.json(HttpStatusCode.OK, serialize(svar)))
                    },
                )
            }
        }
    }

    delete("$DRIFT_PATH/supstonadhistorisk/import/{importId}") {
        authorize(Brukerrolle.Drift) {
            val importId = call.parameters["importId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@authorize call.svar(
                    HttpStatusCode.BadRequest.errorJson("Ugyldig importId", "ugyldig_import_id"),
                )
            runCatching {
                supstonadHistoriskService.slettImport(importId)
            }.fold(
                onSuccess = { call.svar(Resultat.json(HttpStatusCode.OK, """{"importId":"$importId"}""")) },
                onFailure = { e ->
                    log.error("Feil ved sletting av historisk import {}", importId, e)
                    call.svar(
                        HttpStatusCode.BadRequest.errorJson(
                            e.message ?: "Kunne ikke slette import",
                            "kunne_ikke_slette_import",
                        ),
                    )
                },
            )
        }
    }
}
