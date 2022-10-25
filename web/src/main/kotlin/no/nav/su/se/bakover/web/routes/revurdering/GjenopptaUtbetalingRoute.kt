package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.getOrHandle
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.audit.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.harAlleredeÅpenBehandling
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.lagringFeilet
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.revurdering.GjenopptaYtelseRequest
import no.nav.su.se.bakover.service.revurdering.KunneIkkeGjenopptaYtelse
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteGjenopptakAvYtelse
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.utbetaling.SimulerGjenopptakFeil
import no.nav.su.se.bakover.service.utbetaling.UtbetalGjenopptakFeil
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeSak
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.tilResultat
import no.nav.su.se.bakover.web.routes.tilResultat

internal fun Route.gjenopptaUtbetaling(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
) {
    post("$revurderingPath/gjenoppta") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBody<GjenopptaUtbetalingBody> { body ->
                    val navIdent = call.suUserContext.navIdent

                    val revurderingsårsak = Revurderingsårsak.tryCreate(
                        årsak = body.årsak,
                        begrunnelse = body.begrunnelse,
                    ).getOrHandle { return@withSakId call.svar(it.tilResultat()) }

                    val request = GjenopptaYtelseRequest.Opprett(
                        sakId = sakId,
                        saksbehandler = NavIdentBruker.Saksbehandler(navIdent),
                        revurderingsårsak = revurderingsårsak,
                    )

                    revurderingService.gjenopptaYtelse(request).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Opprettet revurdering for gjenopptak av ytelse for sak:$sakId")
                            call.audit(it.fnr, AuditLogEvent.Action.CREATE, it.id)
                            call.svar(
                                Resultat.json(
                                    HttpStatusCode.Created,
                                    serialize(it.toJson(satsFactory)),
                                ),
                            )
                        },
                    )
                }
            }
        }
    }

    patch("$revurderingPath/gjenoppta/{revurderingId}") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withBody<GjenopptaUtbetalingBody> { body ->
                        val navIdent = call.suUserContext.navIdent

                        val revurderingsårsak = Revurderingsårsak.tryCreate(
                            årsak = body.årsak,
                            begrunnelse = body.begrunnelse,
                        ).getOrHandle { return@withRevurderingId call.svar(it.tilResultat()) }

                        val request = GjenopptaYtelseRequest.Oppdater(
                            sakId = sakId,
                            saksbehandler = NavIdentBruker.Saksbehandler(navIdent),
                            revurderingsårsak = revurderingsårsak,
                            revurderingId = revurderingId,
                        )

                        revurderingService.gjenopptaYtelse(request).fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = {
                                call.sikkerlogg("Oppdaterer revurdering for gjenopptak av ytelse for sak:$sakId")
                                call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                                call.svar(
                                    Resultat.json(
                                        HttpStatusCode.OK,
                                        serialize(it.toJson(satsFactory)),
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    post("$revurderingPath/gjenoppta/{revurderingId}/iverksett") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    revurderingService.iverksettGjenopptakAvYtelse(
                        revurderingId = revurderingId,
                        attestant = NavIdentBruker.Attestant(call.suUserContext.navIdent),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Iverksatt gjenopptak av utbetalinger for sak:$sakId")
                            call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                            call.svar(
                                Resultat.json(
                                    HttpStatusCode.OK,
                                    serialize(it.toJson(satsFactory)),
                                ),
                            )
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
        KunneIkkeGjenopptaYtelse.FantIkkeRevurdering -> {
            Revurderingsfeilresponser.fantIkkeRevurdering
        }
        KunneIkkeGjenopptaYtelse.FantIngenVedtak -> {
            Revurderingsfeilresponser.fantIngenVedtakSomKanRevurderes
        }
        KunneIkkeGjenopptaYtelse.KunneIkkeOppretteRevurdering -> {
            InternalServerError.errorJson(
                message = "Kunne ikke opprette revurdering for gjenopptak",
                code = "kunne_ikke_opprette_revurdering_for_gjenopptak",
            )
        }
        is KunneIkkeGjenopptaYtelse.KunneIkkeSimulere -> {
            feil.tilResultat()
        }
        KunneIkkeGjenopptaYtelse.SisteVedtakErIkkeStans -> {
            InternalServerError.errorJson(
                message = "Kan ikke opprette revurdering for gjenopptak, siste vetak er ikke stans",
                code = "siste_vedtak_ikke_stans",
            )
        }
        is KunneIkkeGjenopptaYtelse.UgyldigTypeForOppdatering -> {
            BadRequest.errorJson(
                message = "Ugyldig tilstand for oppdatering: ${this.type}",
                code = "ugyldig_tilstand_for_oppdatering",
            )
        }
        KunneIkkeGjenopptaYtelse.FantIkkeSak -> fantIkkeSak
        KunneIkkeGjenopptaYtelse.SakHarÅpenBehandling -> {
            harAlleredeÅpenBehandling
        }
    }
}

private fun KunneIkkeIverksetteGjenopptakAvYtelse.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeIverksetteGjenopptakAvYtelse.FantIkkeRevurdering -> {
            Revurderingsfeilresponser.fantIkkeRevurdering
        }
        is KunneIkkeIverksetteGjenopptakAvYtelse.KunneIkkeUtbetale -> {
            when (val kunneIkkeUtbetale = this.feil) {
                is UtbetalGjenopptakFeil.KunneIkkeSimulere -> {
                    kunneIkkeUtbetale.feil.tilResultat()
                }
                is UtbetalGjenopptakFeil.KunneIkkeUtbetale -> {
                    kunneIkkeUtbetale.feil.tilResultat()
                }
            }
        }
        is KunneIkkeIverksetteGjenopptakAvYtelse.UgyldigTilstand -> {
            BadRequest.errorJson(
                message = "Kan ikke iverksette gjenopptak av utbetalinger for revurdering av type: ${this.faktiskTilstand}, eneste gyldige tilstand er ${this.målTilstand}",
                code = "kunne_ikke_iverksette_gjenopptak_ugyldig_tilstand",
            )
        }
        KunneIkkeIverksetteGjenopptakAvYtelse.SimuleringIndikererFeilutbetaling -> {
            BadRequest.errorJson(
                message = "Iverksetting av gjenopptak vil føre til feilutbetaling",
                code = "kunne_ikke_iverksette_gjenopptak_fører_til_feilutbetaling",
            )
        }

        KunneIkkeIverksetteGjenopptakAvYtelse.LagringFeilet -> {
            lagringFeilet
        }
    }
}

private fun SimulerGjenopptakFeil.tilResultat(): Resultat {
    return when (this) {
        is SimulerGjenopptakFeil.KunneIkkeGenerereUtbetaling -> {
            feil.tilResultat()
        }
        is SimulerGjenopptakFeil.KunneIkkeSimulere -> {
            feil.tilResultat()
        }
    }
}

private fun Utbetalingsstrategi.Gjenoppta.Feil.tilResultat(): Resultat {
    return when (this) {
        Utbetalingsstrategi.Gjenoppta.Feil.FantIngenUtbetalinger -> {
            InternalServerError.errorJson(
                message = "Utbetalingsstrategi (gjenoppta): Fant ingen utbetalinger",
                code = "fant_ingen_utbetalinger",
            )
        }
        Utbetalingsstrategi.Gjenoppta.Feil.KanIkkeGjenopptaOpphørtePeriode -> {
            InternalServerError.errorJson(
                message = "Utbetalingsstrategi (gjenoppta): Kan ikke gjenoppta opphørte utbetalinger",
                code = "kan_ikke_gjenoppta_opphørte_utbetalinger",
            )
        }
        Utbetalingsstrategi.Gjenoppta.Feil.SisteUtbetalingErIkkeStans -> {
            InternalServerError.errorJson(
                message = "Utbetalingsstrategi (gjenoppta): Siste utbetaling er ikke en stans",
                code = "siste_utbetaling_er_ikke_stans",
            )
        }
    }
}
