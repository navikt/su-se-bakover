package no.nav.su.se.bakover.web.routes.vedtak

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.vedtak.application.VedtakService
import java.time.Clock

private data class Body(
    val fraOgMedMåned: String? = null,
    val inkluderEPS: Boolean = false,
)

internal fun Route.stønadsmottakereRoute(
    vedtakService: VedtakService,
    clock: Clock,
) {
    post("/stønadsmottakere") {
        authorize(Brukerrolle.Drift) {
            call.withBody<Body> { body ->
                call.svar(
                    Resultat.json(
                        HttpStatusCode.OK,
                        vedtakService.hentInnvilgetFnrFraOgMedMåned(
                            body.fraOgMedMåned?.let { Måned.parse(it) } ?: Måned.now(clock),
                            inkluderEps = body.inkluderEPS,
                        ).toJson(),
                    ),
                )
            }
        }
    }
}

private fun List<Fnr>.toJson(): String {
    return serialize(this.map { it.toString() })
}
