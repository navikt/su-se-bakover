package no.nav.su.se.bakover.web.routes.revurdering.avslutt

import arrow.core.flatMap
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeAvslutteRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeLageAvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.KunneIkkeLageBrevutkastForAvsluttingAvRevurdering
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeLageAvsluttetGjenopptaAvYtelse
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.web.routes.dokument.tilResultat
import no.nav.su.se.bakover.web.routes.revurdering.REVURDERING_PATH
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.Brev.brevvalgIkkeTillatt
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.Brev.manglerBrevvalg
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkePersonEllerSaksbehandlerNavn
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.toJson
import vilkår.formue.domain.FormuegrenserFactory

internal fun Route.avsluttRevurderingRoute(
    revurderingService: RevurderingService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    post("$REVURDERING_PATH/{revurderingId}/avslutt") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBody<AvsluttRevurderingRequestJson> { body ->
                call.withRevurderingId { revurderingId ->
                    body.toBrevvalg().flatMap { brevvalg ->
                        revurderingService.avsluttRevurdering(
                            revurderingId = revurderingId,
                            begrunnelse = body.begrunnelse,
                            brevvalg = brevvalg,
                            saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                        ).mapLeft { it.tilResultat() }
                    }.fold(
                        ifLeft = { call.svar(it) },
                        ifRight = {
                            call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                            call.sikkerlogg("Avsluttet behandling av revurdering med revurderingId $revurderingId")
                            call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(formuegrenserFactory))))
                        },
                    )
                }
            }
        }
    }

    data class BrevutkastForAvslutting(
        val fritekst: String = "",
    )
    post("$REVURDERING_PATH/{revurderingId}/brevutkastForAvslutting") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withBody<BrevutkastForAvslutting> { body ->
                    revurderingService.lagBrevutkastForAvslutting(revurderingId, body.fritekst, call.suUserContext.saksbehandler).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Laget brevutkast for revurdering med id $revurderingId")
                            call.audit(it.first, AuditLogEvent.Action.ACCESS, revurderingId)
                            call.respondBytes(it.second.getContent(), ContentType.Application.Pdf)
                        },
                    )
                }
            }
        }
    }
}

private fun KunneIkkeAvslutteRevurdering.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeAvslutteRevurdering.FantIkkeRevurdering -> fantIkkeRevurdering
        is KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse -> this.feil.tilResultat()
        is KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetRevurdering -> this.feil.tilResultat()
        is KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetStansAvYtelse -> this.feil.tilResultat()
        KunneIkkeAvslutteRevurdering.KunneIkkeLageDokument -> Feilresponser.Brev.kunneIkkeLageBrevutkast
        KunneIkkeAvslutteRevurdering.FantIkkePersonEllerSaksbehandlerNavn -> fantIkkePersonEllerSaksbehandlerNavn
        KunneIkkeAvslutteRevurdering.BrevvalgIkkeTillatt -> brevvalgIkkeTillatt
    }
}

private fun KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.FantIkkeRevurdering -> fantIkkeRevurdering
        is KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.KunneIkkeLageDokument -> this.underliggende.tilResultat()
        is KunneIkkeLageBrevutkastForAvsluttingAvRevurdering.KunneIkkeAvslutteRevurdering -> this.underliggende.tilResultat()
    }
}

internal val revurderingErAlleredeAvsluttet = HttpStatusCode.BadRequest.errorJson(
    "Revurderingen er allerede avsluttet",
    "revurderingen_er_allerede_avsluttet",
)

internal val revurderingenErIverksatt = HttpStatusCode.BadRequest.errorJson(
    "Revurderingen er iverksatt",
    "revurderingen_er_iverksatt",
)

internal fun KunneIkkeLageAvsluttetRevurdering.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeLageAvsluttetRevurdering.RevurderingErAlleredeAvsluttet -> revurderingErAlleredeAvsluttet
        KunneIkkeLageAvsluttetRevurdering.RevurderingenErIverksatt -> revurderingenErIverksatt
        KunneIkkeLageAvsluttetRevurdering.RevurderingenErTilAttestering -> HttpStatusCode.BadRequest.errorJson(
            "Revurderingen er til attestering",
            "revurdering_er_til_attestering",
        )

        KunneIkkeLageAvsluttetRevurdering.BrevvalgUtenForhåndsvarsel -> brevvalgIkkeTillatt // TODO jah: endre i frontend og
        KunneIkkeLageAvsluttetRevurdering.ManglerBrevvalgVedForhåndsvarsling -> manglerBrevvalg // TODO jah: endre i frontend og
    }
}

internal fun KunneIkkeLageAvsluttetGjenopptaAvYtelse.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeLageAvsluttetGjenopptaAvYtelse.RevurderingErAlleredeAvsluttet -> revurderingErAlleredeAvsluttet
        KunneIkkeLageAvsluttetGjenopptaAvYtelse.RevurderingenErIverksatt -> revurderingenErIverksatt
    }
}

internal fun StansAvYtelseRevurdering.KunneIkkeLageAvsluttetStansAvYtelse.tilResultat(): Resultat {
    return when (this) {
        StansAvYtelseRevurdering.KunneIkkeLageAvsluttetStansAvYtelse.RevurderingErAlleredeAvsluttet -> revurderingErAlleredeAvsluttet
        StansAvYtelseRevurdering.KunneIkkeLageAvsluttetStansAvYtelse.RevurderingenErIverksatt -> revurderingenErIverksatt
    }
}
