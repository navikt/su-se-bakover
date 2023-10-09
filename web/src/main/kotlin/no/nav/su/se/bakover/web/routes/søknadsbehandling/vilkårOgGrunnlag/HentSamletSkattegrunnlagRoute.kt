package no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilSkattegrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SØKNADSBEHANDLING_PATH
import no.nav.su.se.bakover.web.routes.søknadsbehandling.jsonBody

internal fun Route.hentSamletSkattegrunnlagRoute(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
) {
    get("$SØKNADSBEHANDLING_PATH/{behandlingId}/samletSkattegrunnlag") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                søknadsbehandlingService.leggTilEksternSkattegrunnlag(
                    behandlingId,
                    call.suUserContext.saksbehandler,
                ).fold(
                    { call.svar(it.tilResultat()) },
                    { call.svar(HttpStatusCode.OK.jsonBody(it, satsFactory)) },
                )
            }
        }
    }
}

internal fun KunneIkkeLeggeTilSkattegrunnlag.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeLeggeTilSkattegrunnlag.KanIkkeLeggeTilSkattForTilstandUtenAtDenHarBlittHentetFør -> HttpStatusCode.BadRequest.errorJson(
            "Må være i tilstand vilkårsvurdert for å legge til ny skattegrunnlag. ",
            "må_være_vilkårsvurdert",
        )

        KunneIkkeLeggeTilSkattegrunnlag.UgyldigTilstand -> Feilresponser.ugyldigTilstand
    }
}
