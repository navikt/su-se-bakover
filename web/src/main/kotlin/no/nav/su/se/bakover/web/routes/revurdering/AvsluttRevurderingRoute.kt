package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeAvslutteRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeLageAvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIKkeLageBrevutkastForAvsluttingAvRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkePersonEllerSaksbehandlerNavn
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId

internal fun Route.AvsluttRevurderingRoute(
    revurderingService: RevurderingService,
) {
    authorize(Brukerrolle.Saksbehandler) {

        data class AvsluttRevurderingBody(
            val begrunnelse: String,
            val fritekst: String?,
        )

        post("$revurderingPath/{revurderingId}/avslutt") {
            call.withBody<AvsluttRevurderingBody> { body ->
                call.withRevurderingId { revurderingId ->
                    revurderingService.avsluttRevurdering(
                        revurderingId = revurderingId,
                        begrunnelse = body.begrunnelse,
                        fritekst = body.fritekst,
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Avsluttet behandling av revurdering med revurderingId $revurderingId")
                            call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson())))
                        },
                    )
                }
            }
        }

        data class BrevutkastForAvslutting(
            val fritekst: String?,
        )

        post("$revurderingPath/{revurderingId}/brevutkastForAvslutting") {
            call.withRevurderingId { revurderingId ->
                call.withBody<BrevutkastForAvslutting> { body ->
                    revurderingService.brevutkastForAvslutting(revurderingId, body.fritekst).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Laget brevutkast for revurdering med id $revurderingId")
                            call.audit(it.first, AuditLogEvent.Action.ACCESS, revurderingId)
                            call.respondBytes(it.second, ContentType.Application.Pdf)
                        },
                    )
                }
            }
        }
    }
}

private fun KunneIkkeAvslutteRevurdering.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeAvslutteRevurdering.FantIkkeRevurdering -> Revurderingsfeilresponser.fantIkkeRevurdering
        is KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse -> this.feil.tilResultat()
        is KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetRevurdering -> this.feil.tilResultat()
        is KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetStansAvYtelse -> this.feil.tilResultat()
        KunneIkkeAvslutteRevurdering.KunneIkkeLageDokument -> Feilresponser.Brev.kunneIkkeLageBrevutkast
        KunneIkkeAvslutteRevurdering.FantIkkePersonEllerSaksbehandlerNavn -> fantIkkePersonEllerSaksbehandlerNavn
    }
}

private fun KunneIKkeLageBrevutkastForAvsluttingAvRevurdering.tilResultat(): Resultat {
    return when (this) {
        KunneIKkeLageBrevutkastForAvsluttingAvRevurdering.FantIkkeRevurdering -> Revurderingsfeilresponser.fantIkkeRevurdering
        KunneIKkeLageBrevutkastForAvsluttingAvRevurdering.KunneIkkeLageBrevutkast -> Feilresponser.Brev.kunneIkkeLageBrevutkast
        KunneIKkeLageBrevutkastForAvsluttingAvRevurdering.RevurderingenErIkkeForhåndsvarslet -> HttpStatusCode.BadRequest.errorJson(
            "Revurderingen er ikke forhåndsvarslet for å vise brev",
            "revurdering_er_ikke_forhåndsvarslet_for_å_vise_brev",
        )
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
        KunneIkkeLageAvsluttetRevurdering.RevurderingenErTilAttestering -> revurderingErAlleredeAvsluttet
        KunneIkkeLageAvsluttetRevurdering.FritekstErFylltUtUtenForhåndsvarsel -> HttpStatusCode.BadRequest.errorJson(
            "Fritekst er fyllt ut på en revurdering som ikke er forhåndsvarslet",
            "fritekst_er_fyllt_ut_uten_forhåndsvarsel"
        )
    }
}

internal fun GjenopptaYtelseRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse.tilResultat(): Resultat {
    return when (this) {
        GjenopptaYtelseRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse.RevurderingErAlleredeAvsluttet -> revurderingErAlleredeAvsluttet
        GjenopptaYtelseRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse.RevurderingenErIverksatt -> revurderingenErIverksatt
    }
}

internal fun StansAvYtelseRevurdering.KunneIkkeLageAvsluttetStansAvYtelse.tilResultat(): Resultat {
    return when (this) {
        StansAvYtelseRevurdering.KunneIkkeLageAvsluttetStansAvYtelse.RevurderingErAlleredeAvsluttet -> revurderingErAlleredeAvsluttet
        StansAvYtelseRevurdering.KunneIkkeLageAvsluttetStansAvYtelse.RevurderingenErIverksatt -> revurderingenErIverksatt
    }
}
