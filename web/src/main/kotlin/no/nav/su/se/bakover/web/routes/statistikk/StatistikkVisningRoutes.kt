package no.nav.su.se.bakover.web.routes.statistikk

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkAggregatnøkkel
import no.nav.su.se.bakover.domain.statistikk.Statistikkoppløsning
import no.nav.su.se.bakover.service.statistikk.SakstatistikkSvar
import no.nav.su.se.bakover.service.statistikk.StatistikkVisningService
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private const val STATISTIKK_PATH = "/statistikk"

internal fun Route.statistikkVisningRoutes(service: StatistikkVisningService) {
    val trigger = StatistikkgenereringTrigger(service)

    get("$STATISTIKK_PATH/sak") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Drift) {
            val nøkkel = call.sakstatistikknøkkel()
                ?: return@authorize call.svar(ugyldigeParametre())

            when (val svar = service.hentSakstatistikk(nøkkel)) {
                is SakstatistikkSvar.Ferdig -> call.respondText(
                    text = svar.payload,
                    contentType = io.ktor.http.ContentType.Application.Json,
                    status = HttpStatusCode.OK,
                )

                is SakstatistikkSvar.Genererer -> {
                    trigger.start(svar.aggregatId)
                    call.respondText(
                        text = serialize(GenerererStatistikkJson(svar.aggregatId, "GENERERER")),
                        contentType = io.ktor.http.ContentType.Application.Json,
                        status = HttpStatusCode.Accepted,
                    )
                }
            }
        }
    }

    get("$STATISTIKK_PATH/stønad") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Drift) {
            val fraOgMed = call.request.parameter("fraOgMed")?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
            val tilOgMed = call.request.parameter("tilOgMed")?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
            if (fraOgMed == null || tilOgMed == null || fraOgMed > tilOgMed) {
                return@authorize call.svar(ugyldigeParametre())
            }
            call.respondText(
                text = serialize(service.hentStønadstatistikk(fraOgMed, tilOgMed)),
                contentType = io.ktor.http.ContentType.Application.Json,
                status = HttpStatusCode.OK,
            )
        }
    }
}

private fun ApplicationCall.sakstatistikknøkkel(): SakStatistikkAggregatnøkkel? {
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
    if (fraOgMed > tilOgMed) return null
    return SakStatistikkAggregatnøkkel(fraOgMed, tilOgMed, oppløsning)
}

private fun ApplicationRequest.parameter(navn: String): String? = queryParameters[navn]

private fun ugyldigeParametre() = HttpStatusCode.BadRequest.errorJson(
    message = "Ugyldig eller manglende statistikkperiode.",
    code = "ugyldig_statistikkperiode",
)

private data class GenerererStatistikkJson(
    val aggregatId: UUID,
    val status: String,
)

private class StatistikkgenereringTrigger(
    private val service: StatistikkVisningService,
) {
    private val kjører = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(aggregatId: UUID) {
        if (!kjører.compareAndSet(false, true)) return
        scope.launch {
            try {
                service.genererVentendeSakstatistikk(bareId = aggregatId, maksAntall = 1)
            } finally {
                kjører.set(false)
            }
        }
    }
}
