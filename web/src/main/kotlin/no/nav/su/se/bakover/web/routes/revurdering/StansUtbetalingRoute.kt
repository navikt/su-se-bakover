package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteStansYtelse
import no.nav.su.se.bakover.service.revurdering.KunneIkkeStanseYtelse
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.revurdering.StansYtelseRequest
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
import java.time.LocalDate

internal fun Route.stansUtbetaling(
    revurderingService: RevurderingService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/utbetalinger/stans") {
            call.withSakId { sakId ->
                call.withBody<StansUtbetalingBody> { body ->
                    val navIdent = call.suUserContext.navIdent

                    revurderingService.stansAvYtelse(
                        StansYtelseRequest(
                            sakId = sakId,
                            fraOgMed = body.fraOgMed,
                            saksbehandler = NavIdentBruker.Saksbehandler(navIdent),
                            revurderingsårsak = Revurderingsårsak.create(
                                årsak = body.årsak,
                                begrunnelse = body.begrunnelse,
                            ),
                        ),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Pågbegynner stans av ytelse for $sakId")
                            call.audit(it.fnr, AuditLogEvent.Action.CREATE, it.id)
                            call.svar(Resultat.json(HttpStatusCode.Created, serialize(it.toJson())))
                        },
                    )
                }
            }
        }
    }

    authorize(Brukerrolle.Attestant) {
        post("$revurderingPath/{revurderingId}/utbetalinger/stans/iverksett") {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    revurderingService.iverksettStansAvYtelse(
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

internal class StansUtbetalingBody(
    val fraOgMed: LocalDate,
    val årsak: String,
    val begrunnelse: String,
)

private fun KunneIkkeStanseYtelse.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeStanseYtelse.FantIkkeSak -> Revurderingsfeilresponser.fantIkkeSak
        KunneIkkeStanseYtelse.KontrollAvSimuleringFeilet -> HttpStatusCode.InternalServerError.errorJson(
            message = "feil ved kontroll av simulering",
            code = "feil_ved_kontroll_av_simulering",
        )
        KunneIkkeStanseYtelse.SendingAvUtbetalingTilOppdragFeilet -> HttpStatusCode.InternalServerError.errorJson(
            message = "sending av utbetaling til oppdrag feilet",
            code = "sending_til_oppdrag_feilet",
        )
        KunneIkkeStanseYtelse.SimuleringAvStansFeilet -> HttpStatusCode.InternalServerError.errorJson(
            message = "feil ved simulering av stans",
            code = "feil_ved_simulering_av_stans",
        )
        KunneIkkeStanseYtelse.KunneIkkeOppretteRevurdering -> HttpStatusCode.InternalServerError.errorJson(
            message = "kunne ikke opprette revurdering",
            code = "kunne_ikke_opprette_revurdering",
        )
    }
}

private fun KunneIkkeIverksetteStansYtelse.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeIverksetteStansYtelse.FantIkkeRevurdering -> Revurderingsfeilresponser.fantIkkeRevurdering
        is KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale -> this.utbetalingFeilet.tilResultat()
        is KunneIkkeIverksetteStansYtelse.UgyldigTilstand -> HttpStatusCode.BadRequest.errorJson(
            "Kan ikke iverksette stans av utbetalinger for revurdering av type: ${this.faktiskTilstand}, eneste gyldige tilstand er ${this.målTilstand}",
            "kunne_ikke_iverksette_stans_ugyldig_tilstand",
        )
    }
}
