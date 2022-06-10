package no.nav.su.se.bakover.web.routes.skatt

import arrow.core.Either
import arrow.core.flatMap
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.client.skatteetaten.SkatteoppslagFeil
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.service.skatt.KunneIkkeHenteSkattemelding
import no.nav.su.se.bakover.service.skatt.SkatteService
import no.nav.su.se.bakover.service.toggles.ToggleService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.parameter
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.svar

internal const val skattPath = "/skatt"

internal fun Route.skattRoutes(skatteService: SkatteService, toggleService: ToggleService) {
    get("$skattPath/{fnr}") {
        if (!toggleService.isEnabled(ToggleService.supstonadSkattemelding)) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        authorize(Brukerrolle.Saksbehandler) {
            call.parameter("fnr")
                .flatMap {
                    Either.catch { Fnr(it) }
                        .mapLeft { Feilresponser.ugyldigFødselsnummer }
                }
                .map { fnr ->
                    skatteService.hentSamletSkattegrunnlag(fnr)
                        .fold(
                            ifLeft = {
                                call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
                                val feilmelding = when (it) {
                                    is KunneIkkeHenteSkattemelding.KallFeilet -> {
                                        when (val feil = it.feil) {
                                            is SkatteoppslagFeil.KunneIkkeHenteSkattedata -> (
                                                if (feil.statusCode == 404) HttpStatusCode.fromValue(
                                                    feil.statusCode,
                                                ) else HttpStatusCode.InternalServerError
                                                )
                                                .errorJson(
                                                    "Feil i henting av skattedata for gitt person",
                                                    "UKJENT_SKATTEFEIL",
                                                )
                                            is SkatteoppslagFeil.Nettverksfeil -> HttpStatusCode.InternalServerError.errorJson(
                                                "Feil i kommunikasjon mot skatteetaten",
                                                "FEIL_I_NETTVERK",
                                            )
                                        }
                                    }
                                    is KunneIkkeHenteSkattemelding.KunneIkkeHenteAccessToken -> HttpStatusCode.InternalServerError.errorJson(
                                        "Feil i kommunikasjon mot skatteetaten",
                                        "FEIL_I_HENTING_AV_ACCESS_TOKEN",
                                    )
                                }
                                call.svar(feilmelding)
                            },
                            ifRight = {
                                call.audit(fnr, AuditLogEvent.Action.ACCESS, null)
                                call.svar(Resultat.json(HttpStatusCode.OK, serialize(it)))
                            },
                        )
                }
        }
    }
}
