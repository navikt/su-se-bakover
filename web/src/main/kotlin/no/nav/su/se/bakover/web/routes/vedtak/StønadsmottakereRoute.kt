package no.nav.su.se.bakover.web.routes.vedtak

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.vedtak.InnvilgetForMåned
import no.nav.su.se.bakover.service.vedtak.VedtakService
import java.time.Clock

internal fun Route.stønadsmottakereRoute(
    vedtakService: VedtakService,
    clock: Clock,
) {
    get("/stønadsmottakere") {
        authorize(Brukerrolle.Drift) {
            call.svar(
                Resultat.json(
                    HttpStatusCode.OK,
                    vedtakService.hentInnvilgetFnrForMåned(Måned.now(clock)).toJson(),
                ),
            )
        }
    }
}

private data class JsonResponse(
    val dato: String,
    val fnr: List<String>,
)

private fun InnvilgetForMåned.toJson(): String {
    return JsonResponse(
        // TODO jah: Burde endre dato-feltet til måned for backend/frontend, men dette er bare et driftsendepunkt for verifikasjon.
        dato = måned.toString(),
        fnr = fnr.map { it.toString() },
    ).let {
        serialize(it)
    }
}
