package no.nav.su.se.bakover.web.routes.skatt

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.infrastructure.web.withFnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.toggle.domain.ToggleClient
import no.nav.su.se.bakover.domain.skatt.SkatteoppslagFeil
import no.nav.su.se.bakover.service.skatt.KunneIkkeHenteSkattemelding
import no.nav.su.se.bakover.service.skatt.SkatteService
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.person.tilResultat

internal const val skattPath = "/skatt"

internal fun Route.skattRoutes(skatteService: SkatteService, toggleService: ToggleClient) {
    get("$skattPath/person/{fnr}") {
        if (!toggleService.isEnabled("supstonad.skattemelding")) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.withFnr { fnr ->
            skatteService.hentSamletSkattegrunnlag(fnr).fold(
                {
                    call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
                    call.svar(it.tilResultat())
                },
                {
                    call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
                    call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJSON())))
                },
            )
        }
    }

    get("$skattPath/soknadsbehandling/{behandlingId}") {
        if (!toggleService.isEnabled("supstonad.skattemelding")) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                skatteService.hentSamletSkattegrunnlagForBehandling(behandlingId).let { pair ->
                    pair.second.fold(
                        ifLeft = {
                            // Dette er ikke et åpent søk, men baserer seg heller på behandlingId og er en del av behandlingsflyten. Trenger ikke auditlogge.
                            call.svar(it.tilResultat())
                        },
                        ifRight = {
                            call.audit(pair.first, AuditLogEvent.Action.ACCESS, behandlingId)
                            call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJSON())))
                        },
                    )
                }
            }
        }
    }
}

internal fun KunneIkkeHenteSkattemelding.tilResultat() = when (this) {
    is KunneIkkeHenteSkattemelding.KallFeilet -> {
        when (val f = this.feil) {
            is SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr -> HttpStatusCode.NotFound.errorJson(
                "Ingen summert skattegrunnlag funnet på oppgitt fødselsnummer og inntektsår ${f.år}",
                "inget_skattegrunnlag_for_gitt_fnr_og_år",
            )

            is SkatteoppslagFeil.ManglerRettigheter -> HttpStatusCode.NotFound.errorJson(
                "Autentiserings- eller autoriseringsfeil mot Sigrun/Skatteetaten. Mangler bruker noen rettigheter?",
                "mangler_rettigheter_mot_skatt",
            )

            is SkatteoppslagFeil.Nettverksfeil -> HttpStatusCode.ServiceUnavailable.errorJson(
                "Får ikke kontakt med Sigrun/Skatteetaten. Prøv igjen senere.",
                "nettverksfeil_skatt",
            )

            is SkatteoppslagFeil.UkjentFeil -> HttpStatusCode.InternalServerError.errorJson(
                "Uforventet feil oppstod ved kall til Sigrun/Skatteetaten.",
                "uforventet_feil_mot_skatt",
            )

            is SkatteoppslagFeil.PersonFeil -> (this.feil as SkatteoppslagFeil.PersonFeil).feil.tilResultat()
        }
    }
}
