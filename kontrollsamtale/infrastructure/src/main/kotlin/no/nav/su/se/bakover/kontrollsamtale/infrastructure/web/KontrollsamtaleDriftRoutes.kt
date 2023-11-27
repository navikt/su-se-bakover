package no.nav.su.se.bakover.kontrollsamtale.infrastructure.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.kontrollsamtale.domain.UtløptFristForKontrollsamtaleContext
import no.nav.su.se.bakover.kontrollsamtale.domain.UtløptFristForKontrollsamtaleService
import java.time.LocalDate

private data class RequestBody(
    val kontrollsamtalefrist: LocalDate,
)

private data class ResponseBody(
    val ikkeMøtt: List<String>,
    val feilet: List<String>,
)

private fun UtløptFristForKontrollsamtaleContext.toJsonString(): String {
    return ResponseBody(
        ikkeMøtt = ikkeMøtt().map { it.toString() },
        feilet = feilet().map { it.toString() },
    ).let { serialize(it) }
}

internal fun Route.stansUtløpteKontrollsamtalerRoute(
    service: UtløptFristForKontrollsamtaleService,
) {
    post("/kontrollsamtaler/utløpt/stans") {
        authorize(Brukerrolle.Drift) {
            call.withBody<RequestBody> {
                call.svar(
                    Resultat.json(
                        HttpStatusCode.OK,
                        service.håndterUtløpsdato(it.kontrollsamtalefrist).let {
                            (it.toJsonString())
                        },
                    ),
                )
            }
        }
    }
}
