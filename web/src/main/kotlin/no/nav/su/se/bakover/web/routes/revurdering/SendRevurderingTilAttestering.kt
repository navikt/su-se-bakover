package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.brevvalgMangler
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeAktørId
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.gReguleringKanIkkeFøreTilOpphør
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.kunneIkkeOppretteOppgave
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigTilstand
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.attestering.KunneIkkeSendeRevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.attestering.SendTilAttesteringRequest
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import vilkår.formue.domain.FormuegrenserFactory

internal fun Route.sendRevurderingTilAttestering(
    revurderingService: RevurderingService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    post("$REVURDERING_PATH/{revurderingId}/tilAttestering") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                revurderingService.sendTilAttestering(
                    SendTilAttesteringRequest(
                        revurderingId = RevurderingId(revurderingId),
                        saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                    ),
                ).fold(
                    ifLeft = { call.svar(it.tilResultat()) },
                    ifRight = {
                        call.sikkerlogg("Sendt revurdering til attestering med id $revurderingId")
                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                        call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(formuegrenserFactory))))
                    },
                )
            }
        }
    }
}

internal fun KunneIkkeSendeRevurderingTilAttestering.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeSendeRevurderingTilAttestering.FantIkkeRevurdering -> fantIkkeRevurdering
        is KunneIkkeSendeRevurderingTilAttestering.UgyldigTilstand -> ugyldigTilstand(this.fra, this.til)
        is KunneIkkeSendeRevurderingTilAttestering.FantIkkeAktørId -> fantIkkeAktørId
        is KunneIkkeSendeRevurderingTilAttestering.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
        is KunneIkkeSendeRevurderingTilAttestering.KanIkkeRegulereGrunnbeløpTilOpphør -> gReguleringKanIkkeFøreTilOpphør
        KunneIkkeSendeRevurderingTilAttestering.FeilutbetalingStøttesIkke -> HttpStatusCode.InternalServerError.errorJson(
            "Feilutbetalinger støttes ikke",
            "feilutbetalinger_støttes_ikke",
        )
        is KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke -> BadRequest.errorJson(feilmeldinger.map { it.toJson() })
        is KunneIkkeSendeRevurderingTilAttestering.FeilInnvilget -> {
            when (this.feil) {
                SimulertRevurdering.KunneIkkeSendeInnvilgetRevurderingTilAttestering.BrevvalgMangler -> brevvalgMangler
            }
        }
        is KunneIkkeSendeRevurderingTilAttestering.FeilOpphørt -> {
            when (this.feil) {
                SimulertRevurdering.Opphørt.KanIkkeSendeOpphørtRevurderingTilAttestering.BrevvalgMangler -> brevvalgMangler
                SimulertRevurdering.Opphørt.KanIkkeSendeOpphørtRevurderingTilAttestering.KanIkkeSendeEnOpphørtGReguleringTilAttestering -> gReguleringKanIkkeFøreTilOpphør
            }
        }
    }
}
