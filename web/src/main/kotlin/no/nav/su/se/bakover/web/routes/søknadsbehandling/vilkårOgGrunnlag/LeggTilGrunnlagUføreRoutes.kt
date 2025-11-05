package no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag

import arrow.core.flatMap
import arrow.core.merge
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.web.routes.grunnlag.LeggTilUførervurderingerBody
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SØKNADSBEHANDLING_PATH
import no.nav.su.se.bakover.web.routes.søknadsbehandling.toJson
import vilkår.formue.domain.FormuegrenserFactory

internal fun Route.leggTilUføregrunnlagRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    post("$SØKNADSBEHANDLING_PATH/{behandlingId}/uføregrunnlag") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withBody<LeggTilUførervurderingerBody> { body ->
                    call.svar(
                        body.toServiceCommand(SøknadsbehandlingId(behandlingId))
                            .flatMap { leggTilUføregrunnlagRequest ->
                                søknadsbehandlingService.leggTilUførevilkår(
                                    leggTilUføregrunnlagRequest,
                                    saksbehandler = call.suUserContext.saksbehandler,
                                ).mapLeft {
                                    it.tilResultat()
                                }.map {
                                    call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                                    Resultat.json(HttpStatusCode.Created, serialize(it.toJson(formuegrenserFactory)))
                                }
                            }.merge(),
                    )
                }
            }
        }
    }
}
