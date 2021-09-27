package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.getOrHandle
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.patch
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteStansYtelse
import no.nav.su.se.bakover.service.revurdering.KunneIkkeStanseYtelse
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.revurdering.StansYtelseRequest
import no.nav.su.se.bakover.service.utbetaling.SimulerStansFeilet
import no.nav.su.se.bakover.service.utbetaling.UtbetalStansFeil
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.Feilresponser.tilResultat
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.tilResultat
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
        post("$revurderingPath/stans") {
            call.withSakId { sakId ->
                call.withBody<StansUtbetalingBody> { body ->
                    val navIdent = call.suUserContext.navIdent

                    val revurderingsårsak = Revurderingsårsak.tryCreate(
                        årsak = body.årsak,
                        begrunnelse = body.begrunnelse,
                    ).getOrHandle { return@withSakId call.svar(it.tilResultat()) }

                    val request = StansYtelseRequest.Opprett(
                        sakId = sakId,
                        fraOgMed = body.fraOgMed,
                        saksbehandler = NavIdentBruker.Saksbehandler(navIdent),
                        revurderingsårsak = revurderingsårsak,
                    )

                    revurderingService.stansAvYtelse(request).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Opprettet revurdering for stans av ytelse for $sakId")
                            call.audit(it.fnr, AuditLogEvent.Action.CREATE, it.id)
                            call.svar(Resultat.json(HttpStatusCode.Created, serialize(it.toJson())))
                        },
                    )
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        patch("$revurderingPath/stans/{revurderingId}") {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withBody<StansUtbetalingBody> { body ->
                        val navIdent = call.suUserContext.navIdent

                        val revurderingsårsak = Revurderingsårsak.tryCreate(
                            årsak = body.årsak,
                            begrunnelse = body.begrunnelse,
                        ).getOrHandle { return@withRevurderingId call.svar(it.tilResultat()) }

                        val request = StansYtelseRequest.Oppdater(
                            sakId = sakId,
                            fraOgMed = body.fraOgMed,
                            saksbehandler = NavIdentBruker.Saksbehandler(navIdent),
                            revurderingsårsak = revurderingsårsak,
                            revurderingId = revurderingId,
                        )

                        revurderingService.stansAvYtelse(request).fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = {
                                call.sikkerlogg("Oppdaterer revurdering for stans av ytelse for sak:$sakId")
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
        post("$revurderingPath/stans/{revurderingId}/iverksett") {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    revurderingService.iverksettStansAvYtelse(
                        revurderingId = revurderingId,
                        attestant = NavIdentBruker.Attestant(call.suUserContext.navIdent),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Iverksatt stans av utbetalinger for sak:$sakId")
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
        KunneIkkeStanseYtelse.FantIkkeRevurdering -> {
            Revurderingsfeilresponser.fantIkkeRevurdering
        }
        KunneIkkeStanseYtelse.KunneIkkeOppretteRevurdering -> {
            HttpStatusCode.InternalServerError.errorJson(
                message = "Kunne ikke opprette revurdering for stans",
                code = "kunne_ikke_opprette_revurdering_for_stans",
            )
        }
        is KunneIkkeStanseYtelse.SimuleringAvStansFeilet -> {
            this.feil.tilResultat()
        }
        is KunneIkkeStanseYtelse.UgyldigTypeForOppdatering -> {
            HttpStatusCode.BadRequest.errorJson(
                message = "Ugyldig tilstand for oppdatering: ${this.type}",
                code = "ugyldig_tilstand_for_oppdatering",
            )
        }
    }
}

private fun KunneIkkeIverksetteStansYtelse.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeIverksetteStansYtelse.FantIkkeRevurdering -> {
            Revurderingsfeilresponser.fantIkkeRevurdering
        }
        is KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale -> {
            when (val kunneIkkeUtbetale = this.feil) {
                is UtbetalStansFeil.KunneIkkeSimulere -> {
                    kunneIkkeUtbetale.feil.tilResultat()
                }
                is UtbetalStansFeil.KunneIkkeUtbetale -> {
                    kunneIkkeUtbetale.feil.tilResultat()
                }
            }
        }
        is KunneIkkeIverksetteStansYtelse.UgyldigTilstand -> HttpStatusCode.BadRequest.errorJson(
            "Kan ikke iverksette stans av utbetalinger for revurdering av type: ${this.faktiskTilstand}, eneste gyldige tilstand er ${this.målTilstand}",
            "kunne_ikke_iverksette_stans_ugyldig_tilstand",
        )
    }
}

private fun SimulerStansFeilet.tilResultat(): Resultat {
    return when (this) {
        is SimulerStansFeilet.KunneIkkeGenerereUtbetaling -> {
            this.feil.tilResultat()
        }
        is SimulerStansFeilet.KunneIkkeSimulere -> {
            this.feil.tilResultat()
        }
    }
}

private fun Utbetalingsstrategi.Stans.Feil.tilResultat(): Resultat {
    return when (this) {
        Utbetalingsstrategi.Stans.Feil.FantIngenUtbetalinger -> {
            HttpStatusCode.InternalServerError.errorJson(
                message = "Utbetalingsstrategi (stans): Fant ingen utbetalinger",
                code = "fant_ingen_utbetalinger",
            )
        }
        Utbetalingsstrategi.Stans.Feil.IngenUtbetalingerEtterStansDato -> {
            HttpStatusCode.InternalServerError.errorJson(
                message = "Utbetalingsstrategi (stans): Fant ingen utbetalinger etter stansdato",
                code = "fant_ingen_utbetalinger_etter_stansdato",
            )
        }
        Utbetalingsstrategi.Stans.Feil.KanIkkeStanseOpphørtePerioder -> {
            HttpStatusCode.InternalServerError.errorJson(
                message = "Utbetalingsstrategi (stans): Kan ikke stanse opphørte utbetalinger",
                code = "kan_ikke_stanse_opphørte_utbetalinger",
            )
        }
        Utbetalingsstrategi.Stans.Feil.SisteUtbetalingErEnStans -> {
            HttpStatusCode.InternalServerError.errorJson(
                message = "Utbetalingsstrategi (stans): Kan ikke stanse utbetalinger som allerede er stanset",
                code = "utbetaling_allerede_stanset",
            )
        }
        Utbetalingsstrategi.Stans.Feil.SisteUtbetalingErOpphør -> {
            HttpStatusCode.InternalServerError.errorJson(
                message = "Utbetalingsstrategi (stans): Kan ikke stanse utbetalinger som allerede er opphørt",
                code = "utbetaling_allerede_opphørt",
            )
        }
        Utbetalingsstrategi.Stans.Feil.StansDatoErIkkeFørsteINesteMåned -> {
            HttpStatusCode.InternalServerError.errorJson(
                message = "Utbetalingsstrategi (stans): Stansdato er ikke første dato i neste måned",
                code = "stansdato_ikke_første_i_neste_måned",
            )
        }
    }
}
