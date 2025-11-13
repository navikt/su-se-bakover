package no.nav.su.se.bakover.web.routes.fritekst

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.fritekst.Fritekst
import no.nav.su.se.bakover.domain.fritekst.FritekstService
import no.nav.su.se.bakover.domain.fritekst.FritekstType
import java.util.UUID

internal const val FRITEKST_PATH = "fritekst"

data class Body(val referanseId: String, val type: String)

internal fun Route.fritekstRoutes(
    fritekstService: FritekstService,
) {
    post(FRITEKST_PATH) {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBody<Body> {
                val resultat = fritekstService.hentFritekst(
                    referanseId = UUID.fromString(it.referanseId),
                    type = FritekstType.valueOf(it.type),
                ).map {
                    Resultat.json(HttpStatusCode.OK, serialize(it))
                }.getOrElse {
                    HttpStatusCode.NotFound.errorJson("Fant ikke fritekst", "Fant_ikke_fritekst")
                }
                call.svar(resultat)
            }
        }
    }

    post("$FRITEKST_PATH/lagre") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBody<Fritekst> {
                val resultat = fritekstService.lagreFritekst(it).map {
                    Resultat.json(HttpStatusCode.OK, serialize(it))
                }.getOrElse {
                    HttpStatusCode.NotFound.errorJson("Fant ikke fritekst", "Fant_ikke_fritekst")
                }
                call.svar(resultat)
            }
        }
    }
}
