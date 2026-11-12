package no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag

import arrow.core.merge
import arrow.core.raise.either
import behandling.søknadsbehandling.presentation.bosituasjon.LeggTilBosituasjonForSøknadsbehandlingJsonRequest
import behandling.søknadsbehandling.presentation.bosituasjon.tilResultat
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SØKNADSBEHANDLING_PATH
import no.nav.su.se.bakover.web.routes.søknadsbehandling.toJson
import org.slf4j.LoggerFactory
import vilkår.formue.domain.FormuegrenserFactory

internal fun Route.leggTilGrunnlagBosituasjonRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    post("$SØKNADSBEHANDLING_PATH/{behandlingId}/grunnlag/bosituasjon") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBehandlingId { behandlingId ->
                    call.withValidertBody<LeggTilBosituasjonForSøknadsbehandlingJsonRequest>(log) { json ->
                        either {
                            val command = json.toService(SøknadsbehandlingId(behandlingId)).bind()
                            val søknadsbehandling = søknadsbehandlingService.leggTilBosituasjongrunnlag(
                                command,
                                call.suUserContext.saksbehandler,
                            ).mapLeft { kunneIkkeLeggeTilBosituasjongrunnlag -> kunneIkkeLeggeTilBosituasjongrunnlag.tilResultat() }.bind()

                            call.audit(
                                søknadsbehandling.fnr,
                                AuditLogEvent.Action.UPDATE,
                                søknadsbehandling.id.value,
                            )
                            call.sikkerlogg("Lagret bosituasjon for søknadsbehandling $behandlingId på $sakId")
                            Resultat.json(
                                HttpStatusCode.OK,
                                serialize(søknadsbehandling.toJson(formuegrenserFactory)),
                            )
                        }.merge().let { resultat -> call.svar(resultat) }
                    }
                }
            }
        }
    }
}
