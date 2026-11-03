package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.merge
import arrow.core.raise.either
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.tid.FørsteDagIMåneden
import no.nav.su.se.bakover.common.domain.tid.somFørsteDagIMåneden
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.detHarKommetNyeOverlappendeVedtak
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeSak
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.kunneIkkeUtbetale
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ukjentFeil
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
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.stans.KunneIkkeIverksetteStansYtelse
import no.nav.su.se.bakover.domain.revurdering.stans.KunneIkkeStanseYtelse
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseRequest
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseService
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.web.inputvalidation.sti
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.tilResultat
import no.nav.su.se.bakover.web.routes.tilResultat
import org.slf4j.LoggerFactory
import vilkår.formue.domain.FormuegrenserFactory
import økonomi.domain.simulering.SimulerStansFeilet
import økonomi.domain.utbetaling.KunneIkkeGenerereUtbetalingsstrategiForStans
import java.time.LocalDate

internal fun Route.stansUtbetaling(
    service: StansYtelseService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Oppretter en ny stansbehandling.
     */
    post("$REVURDERING_PATH/stans") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withValidertBody<StansUtbetalingBody>(
                    log,
                    maksLengde = mapOf(
                        sti(StansUtbetalingBody::årsak) to 100,
                        sti(StansUtbetalingBody::begrunnelse) to 2000,
                    ),
                ) { body ->
                    either {
                        val fraOgMed = body.fraOgMedSomFørsteDagIMåneden().bind()
                        val revurderingsårsak = Revurderingsårsak.tryCreate(
                            årsak = body.årsak,
                            begrunnelse = body.begrunnelse,
                        ).mapLeft { it.tilResultat() }.bind()

                        val request = StansYtelseRequest.Opprett(
                            sakId = sakId,
                            fraOgMed = fraOgMed,
                            saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                            revurderingsårsak = revurderingsårsak,
                        )

                        val revurdering = service.stansAvYtelse(request).mapLeft { it.tilResultat() }.bind()
                        call.sikkerlogg("Opprettet revurdering for stans av ytelse for $sakId")
                        call.audit(revurdering.fnr, AuditLogEvent.Action.CREATE, revurdering.id.value)
                        Resultat.json(HttpStatusCode.Created, serialize(revurdering.toJson(formuegrenserFactory)))
                    }.merge().let { call.svar(it) }
                }
            }
        }
    }

    /**
     * Oppdaterer en allerede opprettet stansbehandling.
     */
    patch("$REVURDERING_PATH/stans/{revurderingId}") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withValidertBody<StansUtbetalingBody>(
                        log,
                        maksLengde = mapOf(
                            sti(StansUtbetalingBody::årsak) to 100,
                            sti(StansUtbetalingBody::begrunnelse) to 2000,
                        ),
                    ) { body ->
                        either {
                            val fraOgMed = body.fraOgMedSomFørsteDagIMåneden().bind()
                            val revurderingsårsak = Revurderingsårsak.tryCreate(
                                årsak = body.årsak,
                                begrunnelse = body.begrunnelse,
                            ).mapLeft { it.tilResultat() }.bind()

                            val request = StansYtelseRequest.Oppdater(
                                sakId = sakId,
                                fraOgMed = fraOgMed,
                                saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                                revurderingsårsak = revurderingsårsak,
                                revurderingId = RevurderingId(revurderingId),
                            )

                            val revurdering = service.stansAvYtelse(request).mapLeft { it.tilResultat() }.bind()
                            call.sikkerlogg("Oppdaterer revurdering for stans av ytelse for sak:$sakId")
                            call.audit(revurdering.fnr, AuditLogEvent.Action.UPDATE, revurdering.id.value)
                            Resultat.json(HttpStatusCode.OK, serialize(revurdering.toJson(formuegrenserFactory)))
                        }.merge().let { call.svar(it) }
                    }
                }
            }
        }
    }

    post("$REVURDERING_PATH/stans/{revurderingId}/iverksett") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    service.iverksettStansAvYtelse(
                        revurderingId = RevurderingId(revurderingId),
                        attestant = NavIdentBruker.Attestant(call.suUserContext.navIdent),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Iverksatt stans av utbetalinger for sak:$sakId")
                            call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                            call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(formuegrenserFactory))))
                        },
                    )
                }
            }
        }
    }
}

private fun ugyldigFraOgMed() = HttpStatusCode.BadRequest.errorJson(
    message = "Fra og med må være første dag i måneden",
    code = "ugyldig_fra_og_med",
)

class StansUtbetalingBody(
    val fraOgMed: LocalDate,
    val årsak: String,
    val begrunnelse: String,
)

private fun StansUtbetalingBody.fraOgMedSomFørsteDagIMåneden(): Either<Resultat, FørsteDagIMåneden> {
    return try {
        fraOgMed.somFørsteDagIMåneden().right()
    } catch (_: IllegalArgumentException) {
        ugyldigFraOgMed().left()
    }
}

private fun KunneIkkeStanseYtelse.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeStanseYtelse.FantIkkeRevurdering -> fantIkkeRevurdering

        is KunneIkkeStanseYtelse.KunneIkkeOppretteRevurdering -> {
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

        is KunneIkkeStanseYtelse.FantIkkeSak -> fantIkkeSak

        is KunneIkkeStanseYtelse.UkjentFeil -> ukjentFeil

        is KunneIkkeStanseYtelse.FinnesÅpenStansbehandling -> {
            HttpStatusCode.BadRequest.errorJson(
                message = "Finnes allerede en åpen stansbehandling.",
                code = "finnes_åpen_stansbehandling",
            )
        }

        is KunneIkkeStanseYtelse.SimuleringInneholderFeilutbetaling -> Feilresponser.simuleringFørerTilFeilutbetaling
    }
}

private fun KunneIkkeIverksetteStansYtelse.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeIverksetteStansYtelse.FantIkkeRevurdering -> fantIkkeRevurdering

        is KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale -> kunneIkkeUtbetale

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
        is KunneIkkeIverksetteStansYtelse.KontrollsimuleringFeilet -> underliggende.tilResultat()
        is KunneIkkeIverksetteStansYtelse.KunneIkkeGenerereUtbetaling -> underliggende.tilResultat()
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

private fun KunneIkkeGenerereUtbetalingsstrategiForStans.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeGenerereUtbetalingsstrategiForStans.FantIngenUtbetalinger -> {
            BadRequest.errorJson(
                message = "Utbetalingsstrategi (stans): Fant ingen utbetalinger",
                code = "fant_ingen_utbetalinger",
            )
        }

        KunneIkkeGenerereUtbetalingsstrategiForStans.IngenUtbetalingerEtterStansDato -> {
            BadRequest.errorJson(
                message = "Utbetalingsstrategi (stans): Fant ingen utbetalinger etter stansdato",
                code = "fant_ingen_utbetalinger_etter_stansdato",
            )
        }

        KunneIkkeGenerereUtbetalingsstrategiForStans.KanIkkeStanseOpphørtePerioder -> {
            BadRequest.errorJson(
                message = "Utbetalingsstrategi (stans): Kan ikke stanse opphørte utbetalinger",
                code = "kan_ikke_stanse_opphørte_utbetalinger",
            )
        }

        KunneIkkeGenerereUtbetalingsstrategiForStans.SisteUtbetalingErEnStans -> {
            BadRequest.errorJson(
                message = "Utbetalingsstrategi (stans): Kan ikke stanse utbetalinger som allerede er stanset",
                code = "utbetaling_allerede_stanset",
            )
        }

        KunneIkkeGenerereUtbetalingsstrategiForStans.SisteUtbetalingErOpphør -> {
            BadRequest.errorJson(
                message = "Utbetalingsstrategi (stans): Kan ikke stanse utbetalinger som allerede er opphørt",
                code = "utbetaling_allerede_opphørt",
            )
        }

        KunneIkkeGenerereUtbetalingsstrategiForStans.StansDatoErIkkeFørsteDatoIInneværendeEllerNesteMåned -> {
            BadRequest.errorJson(
                message = "Utbetalingsstrategi (stans): Stansdato er ikke første dato i inneværende eller neste måned",
                code = "stansdato_ikke_første_i_inneværende_eller_neste_måned",
            )
        }
    }
}
