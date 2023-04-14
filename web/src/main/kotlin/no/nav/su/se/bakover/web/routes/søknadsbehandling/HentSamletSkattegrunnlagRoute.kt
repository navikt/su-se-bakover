package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.toggle.domain.ToggleClient
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.skatt.tilErrorJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SkattegrunnlagForSøknadsbehandlingJson.Companion.toJson

internal fun Route.hentSamletSkattegrunnlagRoute(
    søknadsbehandlingService: SøknadsbehandlingService,
    toggleClient: ToggleClient,
) {
    get("$behandlingPath/{behandlingId}/samletSkattegrunnlag") {
        if (!toggleClient.isEnabled("supstonad.skattemelding")) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                søknadsbehandlingService.nySkattegrunnlag(
                    behandlingId,
                    call.suUserContext.saksbehandler,
                ).let {
                    call.svar(Resultat.json(HttpStatusCode.OK, it.toJson()))
                }
            }
        }
    }

    get("$behandlingPath/{behandlingId}/samletSkattegrunnlag/eksisterende") {
        if (!toggleClient.isEnabled("supstonad.skattemelding")) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                søknadsbehandlingService.hentSkattegrunnlag(behandlingId).fold(
                    { call.svar(it.tilErrorJson().tilResultat(HttpStatusCode.NotFound)) },
                    {
                        call.svar(Resultat.json(HttpStatusCode.OK, it.toJson()))
                    },
                )
            }
        }
    }
}
