package no.nav.su.se.bakover.web.external

import arrow.core.Either
import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.vedtak.InnvilgetForMåned
import no.nav.su.se.bakover.vedtak.application.VedtakService
import java.time.Clock
import java.time.YearMonth
import java.time.format.DateTimeFormatter

internal const val FRIKORT_PATH = "/frikort"
private val formatter = DateTimeFormatter.ofPattern("yyyy-MM")

internal fun Route.frikortVedtakRoutes(
    vedtakService: VedtakService,
    clock: Clock,
) {
    fun hentDato(dato: String): Either<Resultat, Måned> {
        return Either.catch { Måned.fra(YearMonth.parse(dato, formatter)) }
            .mapLeft {
                HttpStatusCode.BadRequest.errorJson(
                    "Ugyldig dato - dato må være på format yyyy-MM",
                    "ugyldig_datoformat",
                )
            }
    }

    // Responsen kan inneholde skjermede fødselsnumre. Dette er avklart med dagens konsument og må avklares med potensielt fremtidige konsumenter.
    get("$FRIKORT_PATH/{aktivDato?}") {
        val forMåned = call.parameters["aktivDato"] // yyyy-MM  2021-02
            ?.let {
                hentDato(it).getOrElse {
                    call.svar(it)
                    return@get
                }
            }
            ?: Måned.now(clock)
        call.svar(
            Resultat.json(
                HttpStatusCode.OK,
                vedtakService.hentInnvilgetFnrForMåned(forMåned).toJson(),
            ),
        )
    }
    get("$FRIKORT_PATH/alle") {
        val saker = vedtakService.hentAlleSakerMedInnvilgetVedtak()
        call.svar(
            Resultat.json(
                HttpStatusCode.OK,
                serialize(saker),
            ),
        )
    }
}

private data class JsonResponse(
    val dato: String,
    val fnr: List<String>,
)

private fun InnvilgetForMåned.toJson(): String {
    return JsonResponse(
        dato = måned.toString(),
        fnr = fnr.map { it.toString() },
    ).let {
        serialize(it)
    }
}
