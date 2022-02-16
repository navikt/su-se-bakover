package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.Either
import arrow.core.getOrHandle
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.bruker.Brukerrolle
import no.nav.su.se.bakover.service.regulering.ReguleringService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.external.formatter
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.svar
import java.time.LocalDate
import java.time.YearMonth

internal fun Route.hentListeAvVedtakSomKanReguleres(
    reguleringService: ReguleringService,
) {
    fun String.formaterDato(): Either<Resultat, LocalDate> {
        return Either.catch { YearMonth.parse(this, formatter).atDay(1) }
            .mapLeft {
                HttpStatusCode.BadRequest.errorJson(
                    "Ugyldig dato - dato må være på format YYYY-MM",
                    "ugyldig_datoformat",
                )
            }
    }

    authorize(Brukerrolle.Drift) {
        get("$reguleringPath") {
            val dato = call.request.queryParameters["dato"].let {
                if (it.isNullOrEmpty()) null else it.formaterDato().getOrHandle {
                    call.svar(it)
                    return@get
                }
            }
            call.svar(Resultat.json(HttpStatusCode.OK, serialize(reguleringService.hentAlleSakerSomKanReguleres(dato))))
        }
    }
}
