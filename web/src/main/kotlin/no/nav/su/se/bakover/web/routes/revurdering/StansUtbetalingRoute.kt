package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.detHarKommetNyeOverlappendeVedtak
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeSak
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ukjentFeil
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
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerStansFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalStansFeil
import no.nav.su.se.bakover.domain.revurdering.stans.KunneIkkeIverksetteStansYtelse
import no.nav.su.se.bakover.domain.revurdering.stans.KunneIkkeStanseYtelse
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseRequest
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseService
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.tilResultat
import no.nav.su.se.bakover.web.routes.tilResultat
import java.time.LocalDate

internal fun Route.stansUtbetaling(
    service: StansYtelseService,
    satsFactory: SatsFactory,
) {
    /**
     * Oppretter en ny stansbehandling.
     */
    post("$revurderingPath/stans") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBody<StansUtbetalingBody> { body ->
                    val navIdent = call.suUserContext.navIdent

                    val revurderingsårsak = Revurderingsårsak.tryCreate(
                        årsak = body.årsak,
                        begrunnelse = body.begrunnelse,
                    ).getOrElse { return@authorize call.svar(it.tilResultat()) }

                    val request = StansYtelseRequest.Opprett(
                        sakId = sakId,
                        fraOgMed = body.fraOgMed,
                        saksbehandler = NavIdentBruker.Saksbehandler(navIdent),
                        revurderingsårsak = revurderingsårsak,
                    )

                    service.stansAvYtelse(request).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Opprettet revurdering for stans av ytelse for $sakId")
                            call.audit(it.fnr, AuditLogEvent.Action.CREATE, it.id)
                            call.svar(Resultat.json(HttpStatusCode.Created, serialize(it.toJson(satsFactory))))
                        },
                    )
                }
            }
        }
    }

    /**
     * Oppdaterer en allerede opprettet stansbehandling.
     */
    patch("$revurderingPath/stans/{revurderingId}") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withBody<StansUtbetalingBody> { body ->
                        val revurderingsårsak = Revurderingsårsak.tryCreate(
                            årsak = body.årsak,
                            begrunnelse = body.begrunnelse,
                        ).getOrElse { return@authorize call.svar(it.tilResultat()) }

                        val request = StansYtelseRequest.Oppdater(
                            sakId = sakId,
                            fraOgMed = body.fraOgMed,
                            saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                            revurderingsårsak = revurderingsårsak,
                            revurderingId = revurderingId,
                        )

                        service.stansAvYtelse(request).fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = {
                                call.sikkerlogg("Oppdaterer revurdering for stans av ytelse for sak:$sakId")
                                call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                                call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(satsFactory))))
                            },
                        )
                    }
                }
            }
        }
    }

    post("$revurderingPath/stans/{revurderingId}/iverksett") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    service.iverksettStansAvYtelse(
                        revurderingId = revurderingId,
                        attestant = NavIdentBruker.Attestant(call.suUserContext.navIdent),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Iverksatt stans av utbetalinger for sak:$sakId")
                            call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                            call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(satsFactory))))
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
        KunneIkkeStanseYtelse.FantIkkeRevurdering -> fantIkkeRevurdering

        KunneIkkeStanseYtelse.KunneIkkeOppretteRevurdering -> {
            HttpStatusCode.InternalServerError.errorJson(
                message = "Kunne ikke opprette revurdering for stans",
                code = "kunne_ikke_opprette_revurdering_for_stans",
            )
        }

        is KunneIkkeStanseYtelse.SimuleringAvStansFeilet -> this.feil.tilResultat()

        is KunneIkkeStanseYtelse.UgyldigTypeForOppdatering -> {
            HttpStatusCode.BadRequest.errorJson(
                message = "Ugyldig tilstand for oppdatering: ${this.type}",
                code = "ugyldig_tilstand_for_oppdatering",
            )
        }

        KunneIkkeStanseYtelse.FantIkkeSak -> fantIkkeSak

        is KunneIkkeStanseYtelse.UkjentFeil -> ukjentFeil

        KunneIkkeStanseYtelse.FinnesÅpenStansbehandling -> {
            HttpStatusCode.BadRequest.errorJson(
                message = "Finnes allerede en åpen stansbehandling.",
                code = "finnes_åpen_stansbehandling",
            )
        }
    }
}

private fun KunneIkkeIverksetteStansYtelse.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeIverksetteStansYtelse.FantIkkeRevurdering -> fantIkkeRevurdering

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

        is KunneIkkeIverksetteStansYtelse.UgyldigTilstand -> {
            HttpStatusCode.BadRequest.errorJson(
                "Kan ikke iverksette stans av utbetalinger for revurdering av type: ${this.faktiskTilstand}, eneste gyldige tilstand er ${this.målTilstand}",
                "kunne_ikke_iverksette_stans_ugyldig_tilstand",
            )
        }

        KunneIkkeIverksetteStansYtelse.SimuleringIndikererFeilutbetaling -> {
            HttpStatusCode.BadRequest.errorJson(
                "Iverksetting av stans vil føre til feilutbetaling",
                "kunne_ikke_iverksette_stans_fører_til_feilutbetaling",
            )
        }

        is KunneIkkeIverksetteStansYtelse.UkjentFeil -> ukjentFeil

        KunneIkkeIverksetteStansYtelse.DetHarKommetNyeOverlappendeVedtak -> detHarKommetNyeOverlappendeVedtak
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

        Utbetalingsstrategi.Stans.Feil.StansDatoErIkkeFørsteDatoIInneværendeEllerNesteMåned -> {
            HttpStatusCode.InternalServerError.errorJson(
                message = "Utbetalingsstrategi (stans): Stansdato er ikke første dato i inneværende eller neste måned",
                code = "stansdato_ikke_første_i_inneværende_eller_neste_måned",
            )
        }
    }
}
