package no.nav.su.se.bakover.web.routes.vedtak

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.web.features.authorize
import java.time.Clock
import java.time.LocalDate

internal fun Route.stønadsmottakereRoute(
    vedtakService: VedtakService,
    clock: Clock,
) {
    get("/stønadsmottakere") {
        authorize(Brukerrolle.Drift) {
            val inneværendeMåned = LocalDate.now(clock)
            call.svar(
                Resultat.json(
                    HttpStatusCode.OK,
                    vedtakService.hentAktiveFnr(inneværendeMåned).toJson(inneværendeMåned),
                ),
            )
        }
    }
}

private data class JsonResponse(
    val dato: String,
    val fnr: List<String>,
)

private fun List<Fnr>.toJson(dato: LocalDate): String {
    return JsonResponse(
        dato = dato.toString(),
        fnr = this.map { it.toString() },
    ).let {
        serialize(it)
    }
}
