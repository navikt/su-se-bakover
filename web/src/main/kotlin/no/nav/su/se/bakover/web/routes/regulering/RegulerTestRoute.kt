package no.nav.su.se.bakover.web.routes.regulering

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringRepo

internal fun Route.regulerTestRoute(
    kjøringRepo: ReguleringKjøringRepo,
    runtime: ApplicationConfig.RuntimeEnvironment,
) {
    route("$REGULERING_PATH/test/kjøringer") {
        get {
            if (runtime != ApplicationConfig.RuntimeEnvironment.Test) {
                return@get call.svar(
                    HttpStatusCode.BadRequest.errorJson(
                        message = "Endepunkt er tiltenkt for integrasjonstester",
                        code = "kun_tilgjengelig_for_tester",
                    ),
                )
            }

            val kjøringer = kjøringRepo.hent()

            if (kjøringer.isEmpty()) {
                return@get call.svar(
                    HttpStatusCode.NotFound.errorJson(
                        message = "Fant ingen reguleringskjøringer",
                        code = "fant_ikke_kjøring",
                    ),
                )
            }
            call.svar(
                Resultat.json(
                    httpCode = HttpStatusCode.OK,
                    json = serialize(kjøringer),
                ),
            )
        }
    }
}
