package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.flatMap
import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
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
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.web.routes.grunnlag.LeggTilBosituasjonJsonRequest
import no.nav.su.se.bakover.web.routes.grunnlag.tilResultat

internal fun Route.leggTilGrunnlagBosituasjonRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
) {
    post("$behandlingPath/{behandlingId}/grunnlag/bosituasjon") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBehandlingId { behandlingId ->
                    call.withBody<LeggTilBosituasjonJsonRequest> { json ->
                        call.svar(
                            json.toService(behandlingId)
                                .mapLeft { it }
                                .flatMap {
                                    søknadsbehandlingService.leggTilBosituasjongrunnlag(
                                        it,
                                        call.suUserContext.saksbehandler,
                                    )
                                        .mapLeft { it.tilResultat() }
                                        .map { søknadsbehandling ->
                                            call.audit(
                                                søknadsbehandling.fnr,
                                                AuditLogEvent.Action.UPDATE,
                                                søknadsbehandling.id,
                                            )
                                            call.sikkerlogg("Lagret bosituasjon for søknadsbehandling $behandlingId på $sakId")
                                            Resultat.json(
                                                HttpStatusCode.OK,
                                                serialize(søknadsbehandling.toJson(satsFactory)),
                                            )
                                        }
                                }.getOrElse { it },
                        )
                    }
                }
            }
        }
    }
}
