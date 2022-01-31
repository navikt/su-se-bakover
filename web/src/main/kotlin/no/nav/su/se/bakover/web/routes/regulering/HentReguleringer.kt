package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.Either
import arrow.core.getOrHandle
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.service.regulering.ReguleringService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.external.formatter
import no.nav.su.se.bakover.web.svar
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

internal fun Route.hentListeAvVedtakSomKanReguleres(
    reguleringService: ReguleringService,
    clock: Clock,
) {
    fun formaterDato(dato: String): Either<Resultat, LocalDate> {
        return Either.catch { YearMonth.parse(dato, formatter).atDay(1) }
            .mapLeft {
                HttpStatusCode.BadRequest.errorJson(
                    "Ugyldig dato - dato må være på format YYYY-MM",
                    "ugyldig_datoformat",
                )
            }
    }
    // post kjørgreguleringautomatisk
    // post kjørdennemanuelt

    // authorize(Brukerrolle.Saksbehandler) {
    get("$reguleringPath") {
        val dato = call.request.queryParameters["dato"].let {
            if (it == null) {
                LocalDate.of(LocalDate.now(clock).year, 5, 1)
            } else {
                formaterDato(it).getOrHandle {
                    call.svar(it)
                    return@get
                }
            }
        }
        val aktiveBehandlinger =
            reguleringService.hentAlleSakerSomKanReguleres(dato).getOrHandle { call.respond("feil") }
        call.svar(Resultat.json(HttpStatusCode.Created, serialize(aktiveBehandlinger)))
    }
    // }
}
