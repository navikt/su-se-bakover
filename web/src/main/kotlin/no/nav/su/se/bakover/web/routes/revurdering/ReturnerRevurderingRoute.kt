package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigTilstand
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.retur.KunneIkkeReturnereRevurdering
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import vilkår.formue.domain.FormuegrenserFactory

internal fun Route.returnerRevurderingRoute(
    revurderingService: RevurderingService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    patch("$REVURDERING_PATH/{revurderingId}/returnerRevurdering") {
        authorize(Brukerrolle.Saksbehandler) {
            val saksbehandler = call.suUserContext.saksbehandler

            call.withRevurderingId { revurderingId ->
                revurderingService
                    .returnerRevurdering(
                        RevurderingService.ReturnerRevurderingRequest(
                            revurderingId = RevurderingId(revurderingId),
                            saksbehandler = saksbehandler,
                        ),
                    ).fold(
                        ifLeft = {
                            val resultat = when (it) {
                                KunneIkkeReturnereRevurdering.FantIkkeRevurdering -> fantIkkeRevurdering
                                KunneIkkeReturnereRevurdering.FantIkkeAktørId -> Feilresponser.fantIkkeAktørId
                                KunneIkkeReturnereRevurdering.KunneIkkeOppretteOppgave -> Feilresponser.kunneIkkeOppretteOppgave
                                is KunneIkkeReturnereRevurdering.FeilSaksbehandler -> ugyldigTilstand
                            }
                            call.svar(resultat)
                        },
                        ifRight = {
                            call.sikkerlogg("Tok revurdering i retur: $revurderingId")
                            call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                            call.svar(
                                Resultat.json(
                                    HttpStatusCode.OK,
                                    serialize(it.toJson(formuegrenserFactory)),
                                ),
                            )
                        },
                    )
            }
        }
    }
}
