package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.merge
import arrow.core.raise.either
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.detHarKommetNyeOverlappendeVedtak
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeSak
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.lagringFeilet
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.GjenopptaYtelseRequest
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.GjenopptaYtelseService
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeSimulereGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.web.inputvalidation.sti
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.tilResultat
import no.nav.su.se.bakover.web.routes.tilResultat
import org.slf4j.LoggerFactory
import vilkår.formue.domain.FormuegrenserFactory

internal fun Route.gjenopptaUtbetaling(
    service: GjenopptaYtelseService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    post("$REVURDERING_PATH/gjenoppta") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withValidertBody<GjenopptaUtbetalingBody>(
                    log,
                    maksLengde = mapOf(
                        sti(GjenopptaUtbetalingBody::årsak) to 100,
                        sti(GjenopptaUtbetalingBody::begrunnelse) to 2000,
                    ),
                ) { body ->
                    either {
                        val revurderingsårsak = Revurderingsårsak.tryCreate(
                            årsak = body.årsak,
                            begrunnelse = body.begrunnelse,
                        ).mapLeft { ugyldigRevurderingsårsak -> ugyldigRevurderingsårsak.tilResultat() }.bind()

                        val request = GjenopptaYtelseRequest.Opprett(
                            sakId = sakId,
                            saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                            revurderingsårsak = revurderingsårsak,
                        )

                        val revurdering = service.gjenopptaYtelse(request).mapLeft { ugyldigRevurderingsårsak -> ugyldigRevurderingsårsak.tilResultat() }.bind().first
                        // TODO jah: Returner potensielle forskjeller mellom utbetaling og simulering
                        call.sikkerlogg("Opprettet revurdering for gjenopptak av ytelse for sak:$sakId")
                        call.audit(revurdering.fnr, AuditLogEvent.Action.CREATE, revurdering.id.value)
                        Resultat.json(HttpStatusCode.Created, serialize(revurdering.toJson(formuegrenserFactory)))
                    }.merge().let { resultat -> call.svar(resultat) }
                }
            }
        }
    }

    patch("$REVURDERING_PATH/gjenoppta/{revurderingId}") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withValidertBody<GjenopptaUtbetalingBody>(
                        log,
                        maksLengde = mapOf(
                            sti(GjenopptaUtbetalingBody::årsak) to 100,
                            sti(GjenopptaUtbetalingBody::begrunnelse) to 2000,
                        ),
                    ) { body ->
                        either {
                            val revurderingsårsak = Revurderingsårsak.tryCreate(
                                årsak = body.årsak,
                                begrunnelse = body.begrunnelse,
                            ).mapLeft { ugyldigRevurderingsårsak -> ugyldigRevurderingsårsak.tilResultat() }.bind()

                            val request = GjenopptaYtelseRequest.Oppdater(
                                sakId = sakId,
                                saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                                revurderingsårsak = revurderingsårsak,
                                revurderingId = RevurderingId(revurderingId),
                            )

                            val revurdering = service.gjenopptaYtelse(request).mapLeft { ugyldigRevurderingsårsak -> ugyldigRevurderingsårsak.tilResultat() }.bind().first
                            // TODO jah: Returner potensielle forskjeller mellom utbetaling og simulering
                            call.sikkerlogg("Oppdaterer revurdering for gjenopptak av ytelse for sak:$sakId")
                            call.audit(revurdering.fnr, AuditLogEvent.Action.UPDATE, revurdering.id.value)
                            Resultat.json(HttpStatusCode.OK, serialize(revurdering.toJson(formuegrenserFactory)))
                        }.merge().let { resultat -> call.svar(resultat) }
                    }
                }
            }
        }
    }

    post("$REVURDERING_PATH/gjenoppta/{revurderingId}/iverksett") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    service.iverksettGjenopptakAvYtelse(
                        revurderingId = RevurderingId(revurderingId),
                        attestant = NavIdentBruker.Attestant(call.suUserContext.navIdent),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Iverksatt gjenopptak av utbetalinger for sak:$sakId")
                            call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                            call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(formuegrenserFactory))))
                        },
                    )
                }
            }
        }
    }
}

class GjenopptaUtbetalingBody(
    val årsak: String,
    val begrunnelse: String,
)

private fun KunneIkkeSimulereGjenopptakAvYtelse.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeSimulereGjenopptakAvYtelse.FantIkkeRevurdering -> {
            Revurderingsfeilresponser.fantIkkeRevurdering
        }

        KunneIkkeSimulereGjenopptakAvYtelse.FantIngenVedtak -> {
            Revurderingsfeilresponser.fantIngenVedtakSomKanRevurderes
        }

        KunneIkkeSimulereGjenopptakAvYtelse.KunneIkkeOppretteRevurdering -> {
            InternalServerError.errorJson(
                message = "Kunne ikke opprette revurdering for gjenopptak",
                code = "kunne_ikke_opprette_revurdering_for_gjenopptak",
            )
        }

        is KunneIkkeSimulereGjenopptakAvYtelse.KunneIkkeSimulere -> {
            this.feil.tilResultat()
        }

        KunneIkkeSimulereGjenopptakAvYtelse.SisteVedtakErIkkeStans -> {
            BadRequest.errorJson(
                message = "Kan ikke opprette revurdering for gjenopptak, siste vetak er ikke stans",
                code = "siste_vedtak_ikke_stans",
            )
        }

        is KunneIkkeSimulereGjenopptakAvYtelse.UgyldigTypeForOppdatering -> {
            BadRequest.errorJson(
                message = "Ugyldig tilstand for oppdatering: ${this.type}",
                code = "ugyldig_tilstand_for_oppdatering",
            )
        }

        KunneIkkeSimulereGjenopptakAvYtelse.FantIkkeSak -> fantIkkeSak

        KunneIkkeSimulereGjenopptakAvYtelse.FinnesÅpenGjenopptaksbehandling -> {
            BadRequest.errorJson(
                message = "Finnes allerede en åpen gjenopptaksbehandling.",
                code = "finnes_åpen_gjenopptaksbehandling",
            )
        }

        is KunneIkkeSimulereGjenopptakAvYtelse.KunneIkkeGenerereUtbetaling -> this.underliggende.tilResultat()
    }
}

private fun KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.FantIkkeRevurdering -> {
            Revurderingsfeilresponser.fantIkkeRevurdering
        }

        is KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.KunneIkkeUtbetale -> {
            Feilresponser.kunneIkkeUtbetale
        }

        is KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.UgyldigTilstand -> {
            BadRequest.errorJson(
                message = "Kan ikke iverksette gjenopptak av utbetalinger for revurdering av type: ${this.faktiskTilstand}, eneste gyldige tilstand er ${this.målTilstand}",
                code = "kunne_ikke_iverksette_gjenopptak_ugyldig_tilstand",
            )
        }

        KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.SimuleringIndikererFeilutbetaling -> {
            BadRequest.errorJson(
                message = "Iverksetting av gjenopptak vil føre til feilutbetaling",
                code = "kunne_ikke_iverksette_gjenopptak_fører_til_feilutbetaling",
            )
        }

        KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.LagringFeilet -> lagringFeilet

        KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.DetHarKommetNyeOverlappendeVedtak -> detHarKommetNyeOverlappendeVedtak
        is KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.KontrollsimuleringFeilet -> this.underliggende.tilResultat()
        is KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.KunneIkkeGenerereUtbetaling -> this.underliggende.tilResultat()
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
            BadRequest.errorJson(
                message = "Utbetalingsstrategi (gjenoppta): Kan ikke gjenoppta opphørte utbetalinger",
                code = "kan_ikke_gjenoppta_opphørte_utbetalinger",
            )
        }

        Utbetalingsstrategi.Gjenoppta.Feil.SisteUtbetalingErIkkeStans -> {
            BadRequest.errorJson(
                message = "Utbetalingsstrategi (gjenoppta): Siste utbetaling er ikke en stans",
                code = "siste_utbetaling_er_ikke_stans",
            )
        }
    }
}
