package no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag

import arrow.core.merge
import arrow.core.raise.either
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag.KunneIkkeLeggeTilSkattegrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag.SøknadsbehandlingSkatt
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SØKNADSBEHANDLING_PATH
import no.nav.su.se.bakover.web.routes.søknadsbehandling.jsonBody
import org.slf4j.LoggerFactory
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Year
import java.util.UUID

data class OppdaterSkattegrunnlagBody(
    val fra: String,
    val til: String,
) {
    fun toCommand(behandlingId: UUID, saksbehandler: NavIdentBruker.Saksbehandler) = SøknadsbehandlingSkatt(
        behandlingId = SøknadsbehandlingId(behandlingId),
        saksbehandler = saksbehandler,
        yearRange = YearRange(start = Year.parse(fra), endInclusive = Year.parse(til)),
    )
}

internal fun Route.oppdaterSkattegrunnlagRoute(
    søknadsbehandlingService: SøknadsbehandlingService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    post("$SØKNADSBEHANDLING_PATH/{behandlingId}/samletSkattegrunnlag") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withValidertBody<OppdaterSkattegrunnlagBody>(log) { body ->
                    either {
                        val søknadsbehandling = søknadsbehandlingService.oppdaterSkattegrunnlag(
                            body.toCommand(behandlingId, call.suUserContext.saksbehandler),
                        ).mapLeft { kunneIkkeLeggeTilSkattegrunnlag -> kunneIkkeLeggeTilSkattegrunnlag.tilResultat() }.bind()

                        HttpStatusCode.OK.jsonBody(søknadsbehandling, formuegrenserFactory)
                    }.merge().let { resultat -> call.svar(resultat) }
                }
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
