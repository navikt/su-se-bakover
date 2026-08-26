package no.nav.su.se.bakover.web.routes.drift

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.client.historisk.CountRequest
import no.nav.su.se.bakover.client.historisk.UttrekkRequest
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.service.historisk.KunneIkkeSletteImport
import no.nav.su.se.bakover.service.historisk.SupstonadHistoriskService
import org.slf4j.LoggerFactory
import java.util.UUID

internal fun Route.supstonadHistoriskRoutes(
    supstonadHistoriskService: SupstonadHistoriskService,
) {
    val log = LoggerFactory.getLogger("SupstonadHistoriskRoutes")

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

    post("$DRIFT_PATH/supstonadhistorisk/hentuttrekk") {
        authorize(Brukerrolle.Drift) {
            call.withBody<UttrekkRequest> { body ->
                log.info("SupstonadHistoriskRoutes: hentUttrekk kalt for tabell '{}'", body.tabellnavn)
                if (body.antallRader <= 0) {
                    return@withBody call.svar(
                        HttpStatusCode.BadRequest.errorJson(
                            message = "antallRader må være større enn 0",
                            code = "supstonad_historisk_ugyldig_antall_rader",
                        ),
                    )
                }
                supstonadHistoriskService.hentUttrekk(
                    tabellnavn = body.tabellnavn,
                    antallRader = body.antallRader,
                    iterator = body.iterator,
                ).fold(
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

    route("$DRIFT_PATH/supstonadhistorisk/import") {
        get {
            authorize(Brukerrolle.Drift) {
                call.svar(Resultat.json(HttpStatusCode.OK, serialize(supstonadHistoriskService.hentAlleImporter())))
            }
        }

        post {
            authorize(Brukerrolle.Drift) {
                log.info("SupstonadHistoriskRoutes: importerAlleTabeller")
                CoroutineScope(Dispatchers.IO).launch {
                    supstonadHistoriskService.importerAlleTabeller()
                }
                call.svar(Resultat.accepted())
            }
        }

        delete("{importId}") {
            authorize(Brukerrolle.Drift) {
                val importId = call.parameters["importId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@authorize call.svar(
                        HttpStatusCode.BadRequest.errorJson("Ugyldig importId", "ugyldig_import_id"),
                    )
                supstonadHistoriskService.slettImport(importId).fold(
                    ifLeft = { feil ->
                        call.svar(
                            when (feil) {
                                KunneIkkeSletteImport.IkkeFunnet ->
                                    HttpStatusCode.NotFound.errorJson(
                                        "Fant ikke import $importId",
                                        "import_ikke_funnet",
                                    )

                                KunneIkkeSletteImport.ImportPågår ->
                                    HttpStatusCode.Conflict.errorJson(
                                        "Import $importId pågår og kan ikke slettes",
                                        "import_pågår",
                                    )
                            },
                        )
                    },
                    ifRight = { call.svar(Resultat.json(HttpStatusCode.OK, """{"importId":"$importId"}""")) },
                )
            }
        }
    }
}
