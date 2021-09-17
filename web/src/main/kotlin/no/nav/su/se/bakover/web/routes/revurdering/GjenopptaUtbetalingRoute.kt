package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.patch
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.GjenopptaYtelseRequest
import no.nav.su.se.bakover.service.revurdering.KunneIkkeGjenopptaYtelse
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteGjenopptakAvYtelse
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.Feilresponser.tilResultat
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import no.nav.su.se.bakover.web.withSakId

internal fun Route.gjenopptaUtbetaling(
    revurderingService: RevurderingService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/gjenoppta") {
            call.withSakId { sakId ->
                call.withBody<GjenopptaUtbetalingBody> { body ->
                    val navIdent = call.suUserContext.navIdent

                    val request = GjenopptaYtelseRequest.Opprett(
                        sakId = sakId,
                        saksbehandler = NavIdentBruker.Saksbehandler(navIdent),
                        revurderingsårsak = Revurderingsårsak.create(
                            årsak = body.årsak,
                            begrunnelse = body.begrunnelse,
                        ),
                    )

                    revurderingService.gjenopptaYtelse(request).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Opprettet revurdering for satans av ytelse for $sakId")
                            call.audit(it.fnr, AuditLogEvent.Action.CREATE, it.id)
                            call.svar(Resultat.json(HttpStatusCode.Created, serialize(it.toJson())))
                        },
                    )
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        patch("$revurderingPath/gjenoppta/{revurderingId}") {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withBody<GjenopptaUtbetalingBody> { body ->
                        val navIdent = call.suUserContext.navIdent

                        val request = GjenopptaYtelseRequest.Oppdater(
                            sakId = sakId,
                            saksbehandler = NavIdentBruker.Saksbehandler(navIdent),
                            revurderingsårsak = Revurderingsårsak.create(
                                årsak = body.årsak,
                                begrunnelse = body.begrunnelse,
                            ),
                            revurderingId = revurderingId,
                        )

                        revurderingService.gjenopptaYtelse(request).fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = {
                                call.sikkerlogg("Oppdaterer revurdering for stans av ytelse for $sakId")
                                call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                                call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson())))
                            },
                        )
                    }
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
        post("$revurderingPath/gjenoppta/{revurderingId}/iverksett") {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    revurderingService.iverksettGjenopptakAvYtelse(
                        revurderingId = revurderingId,
                        attestant = NavIdentBruker.Attestant(call.suUserContext.navIdent),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Iverksatt stans av utbetalinger for $sakId")
                            call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                            call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson())))
                        },
                    )
                }
            }
        }
    }
}

internal class GjenopptaUtbetalingBody(
    val årsak: String,
    val begrunnelse: String,
)

private fun KunneIkkeGjenopptaYtelse.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeGjenopptaYtelse.FantIkkeSak -> Revurderingsfeilresponser.fantIkkeSak
        KunneIkkeGjenopptaYtelse.KontrollAvSimuleringFeilet -> HttpStatusCode.InternalServerError.errorJson(
            message = "feil ved kontroll av simulering",
            code = "feil_ved_kontroll_av_simulering",
        )
        KunneIkkeGjenopptaYtelse.SendingAvUtbetalingTilOppdragFeilet -> HttpStatusCode.InternalServerError.errorJson(
            message = "sending av utbetaling til oppdrag feilet",
            code = "sending_til_oppdrag_feilet",
        )
        KunneIkkeGjenopptaYtelse.SimuleringAvGjenopptakFeilet -> HttpStatusCode.InternalServerError.errorJson(
            message = "feil ved simulering av gjenopptak",
            code = "feil_ved_simulering_av_gjenopptak",
        )
        KunneIkkeGjenopptaYtelse.KunneIkkeOppretteRevurdering -> HttpStatusCode.InternalServerError.errorJson(
            message = "kunne ikke opprette revurdering",
            code = "kunne_ikke_opprette_revurdering",
        )
        KunneIkkeGjenopptaYtelse.FantIkkeRevurdering -> Revurderingsfeilresponser.fantIkkeRevurdering
        is KunneIkkeGjenopptaYtelse.UgyldigTypeForOppdatering -> HttpStatusCode.BadRequest.errorJson(
            message = "kunne ikke oppdatere revurdering for gjenopptak, eksisterende revurdering er av feil type: ${this.type}",
            code = "ugyldig_type_for_oppdatering_av_gjenopptak",
        )
        KunneIkkeGjenopptaYtelse.SisteVedtakErIkkeStans -> HttpStatusCode.BadRequest.errorJson(
            "kan ikke opprette revurdering for gjenopptak av ytelse, siste vedtak er ikke en gjenopptak",
            "siste_vedtak_ikke_gjenopptak",
        )
        KunneIkkeGjenopptaYtelse.FantIngenVedtak -> HttpStatusCode.BadRequest.errorJson(
            "kan ikke opprette revurdering for gjenopptak av utbetaling uten tidligere vedtak",
            "ingen_tidligere_vedtak"
        )
    }
}

private fun KunneIkkeIverksetteGjenopptakAvYtelse.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeIverksetteGjenopptakAvYtelse.FantIkkeRevurdering -> Revurderingsfeilresponser.fantIkkeRevurdering
        is KunneIkkeIverksetteGjenopptakAvYtelse.KunneIkkeUtbetale -> this.utbetalingFeilet.tilResultat()
        is KunneIkkeIverksetteGjenopptakAvYtelse.UgyldigTilstand -> HttpStatusCode.BadRequest.errorJson(
            "Kan ikke iverksette gjenopptak av utbetalinger for revurdering av type: ${this.faktiskTilstand}, eneste gyldige tilstand er ${this.målTilstand}",
            "kunne_ikke_iverksette_gjenopptak_ugyldig_tilstand",
        )
    }
}
