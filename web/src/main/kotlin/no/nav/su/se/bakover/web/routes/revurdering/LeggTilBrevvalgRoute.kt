package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeLeggeTilVedtaksbrevvalg
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.brev.LeggTilBrevvalgRequest
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import vilkår.formue.domain.FormuegrenserFactory

internal fun Route.leggTilBrevvalgRevurderingRoute(
    revurderingService: RevurderingService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    data class Body(
        val valg: LeggTilBrevvalgRequest.Valg,
        val fritekst: String?,
        val begrunnelse: String?,
    )

    post("$REVURDERING_PATH/{revurderingId}/brevvalg") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withBody<Body> { body ->
                    call.svar(
                        revurderingService.leggTilBrevvalg(
                            LeggTilBrevvalgRequest(
                                revurderingId = RevurderingId(revurderingId),
                                valg = body.valg,
                                fritekst = body.fritekst,
                                begrunnelse = body.begrunnelse,
                                saksbehandler = call.suUserContext.saksbehandler,
                            ),
                        ).fold(
                            ifLeft = { it.tilResultat() },
                            ifRight = {
                                call.sikkerlogg("Oppdaterte brevvalg for revurdering:$revurderingId")
                                call.audit(it.fnr, AuditLogEvent.Action.UPDATE, revurderingId)
                                Resultat.json(HttpStatusCode.Created, serialize(it.toJson(formuegrenserFactory)))
                            },
                        ),
                    )
                }
            }
        }
    }
}

internal fun KunneIkkeLeggeTilVedtaksbrevvalg.tilResultat(): Resultat {
    return when (val f = this) {
        is KunneIkkeLeggeTilVedtaksbrevvalg.UgyldigTilstand -> {
            Feilresponser.ugyldigTilstand(f.tilstand, f.tilstand)
        }
    }
}
