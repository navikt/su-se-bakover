package no.nav.su.se.bakover.web.routes.statistikk

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkVisningsvalg
import no.nav.su.se.bakover.domain.statistikk.Statistikkoppløsning
import no.nav.su.se.bakover.service.statistikk.SakstatistikkSvar
import no.nav.su.se.bakover.service.statistikk.StatistikkVisningService
import no.nav.su.se.bakover.service.statistikk.StønadstatistikkSvar
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

private const val STATISTIKK_PATH = "/statistikk"
private const val MAKS_ANTALL_MÅNEDER = 12L

internal fun Route.statistikkVisningRoutes(service: StatistikkVisningService) {
    get("$STATISTIKK_PATH/sak") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Drift) {
            val nøkkel = call.sakstatistikkvisningsvalg()
                ?: return@authorize call.svar(ugyldigeParametre())

            when (val svar = service.hentSakstatistikk(nøkkel)) {
                is SakstatistikkSvar.Ferdig -> call.respondText(
                    text = serialize(svar.oppsummering),
                    contentType = io.ktor.http.ContentType.Application.Json,
                    status = HttpStatusCode.OK,
                )
                is SakstatistikkSvar.Genererer -> {
                    call.respondText(
                        text = serialize(GenerererStatistikkJson(svar.aggregatIder, "GENERERER")),
                        contentType = io.ktor.http.ContentType.Application.Json,
                        status = HttpStatusCode.Accepted,
                    )
                    call.application.launch(Dispatchers.IO) {
                        service.genererSakstatistikk(svar.aggregatIder)
                    }
                }
            }
        }
    }

    get("$STATISTIKK_PATH/stønad") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Drift) {
            val fraOgMed = call.request.parameter("fraOgMed")?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
            val tilOgMed = call.request.parameter("tilOgMed")?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
            if (
                fraOgMed == null ||
                tilOgMed == null ||
                fraOgMed > tilOgMed ||
                java.time.temporal.ChronoUnit.MONTHS.between(fraOgMed, tilOgMed) >= MAKS_ANTALL_MÅNEDER
            ) {
                return@authorize call.svar(ugyldigeParametre())
            }
            when (val svar = service.hentStønadstatistikk(fraOgMed, tilOgMed)) {
                is StønadstatistikkSvar.Ferdig -> call.respondText(
                    text = serialize(svar.oppsummering),
                    contentType = io.ktor.http.ContentType.Application.Json,
                    status = HttpStatusCode.OK,
                )
                is StønadstatistikkSvar.Genererer -> {
                    call.respondText(
                        text = serialize(GenerererStønadstatistikkJson(svar.aggregatIder, "GENERERER")),
                        contentType = io.ktor.http.ContentType.Application.Json,
                        status = HttpStatusCode.Accepted,
                    )
                    call.application.launch(Dispatchers.IO) {
                        service.genererStønadstatistikk(svar.aggregatIder)
                    }
                }
            }
        }
    }
}

private fun ApplicationCall.sakstatistikkvisningsvalg(): SakStatistikkVisningsvalg? {
    val fraOgMed = request.parameter("fraOgMed")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return null
    val tilOgMed = request.parameter("tilOgMed")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return null
    val oppløsning = when ((request.parameter("oppløsning") ?: request.parameter("opplosning"))?.lowercase()) {
        "uke" -> Statistikkoppløsning.UKE
        "måned", "maned" -> Statistikkoppløsning.MÅNED
        "år", "ar" -> Statistikkoppløsning.ÅR
        else -> return null
    }
    if (
        fraOgMed > tilOgMed ||
        tilOgMed >= fraOgMed.plusYears(1)
    ) {
        return null
    }
    return SakStatistikkVisningsvalg(fraOgMed, tilOgMed, oppløsning)
}

private fun ApplicationRequest.parameter(navn: String): String? = queryParameters[navn]

private fun ugyldigeParametre() = HttpStatusCode.BadRequest.errorJson(
    message = "Ugyldig eller manglende statistikkperiode.",
    code = "ugyldig_statistikkperiode",
)

private data class GenerererStatistikkJson(
    val aggregatIder: List<UUID>,
    val status: String,
)

private data class GenerererStønadstatistikkJson(
    val aggregatIder: List<UUID>,
    val status: String,
)
