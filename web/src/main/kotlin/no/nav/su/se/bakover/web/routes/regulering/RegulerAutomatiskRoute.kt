package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.separateEither
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigMåned
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.lesUUID
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withReguleringId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.regulering.KunneIkkeAvslutte
import no.nav.su.se.bakover.domain.regulering.KunneIkkeRegulereManuelt
import no.nav.su.se.bakover.domain.regulering.ReguleringService
import no.nav.su.se.bakover.domain.regulering.StartAutomatiskReguleringForInnsynCommand
import no.nav.su.se.bakover.web.routes.grunnlag.UføregrunnlagJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragRequestJson
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal fun Route.reguler(
    reguleringService: ReguleringService,
    clock: Clock,
) {
    post("$reguleringPath/automatisk") {
        authorize(Brukerrolle.Drift) {
            data class Body(val fraOgMedMåned: String)
            call.withBody<Body> { body ->
                when (val m = Måned.parse(body.fraOgMedMåned)) {
                    null -> call.svar(ugyldigMåned)
                    else -> {
                        CoroutineScope(Dispatchers.IO).launch {
                            reguleringService.startAutomatiskRegulering(m)
                        }
                        call.svar(Resultat.okJson())
                    }
                }
            }
        }
    }

    post("$reguleringPath/manuell/{reguleringId}") {
        authorize(Brukerrolle.Saksbehandler) {
            data class Body(val fradrag: List<FradragRequestJson>, val uføre: List<UføregrunnlagJson>)
            call.withReguleringId { id ->
                call.withBody<Body> { body ->
                    reguleringService.regulerManuelt(
                        reguleringId = id,
                        uføregrunnlag = body.uføre.toDomain(clock).getOrElse { return@authorize call.svar(it) },
                        fradrag = body.fradrag.toDomain(clock).getOrElse { return@authorize call.svar(it) },
                        saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                    ).fold(
                        ifLeft = {
                            when (it) {
                                KunneIkkeRegulereManuelt.AlleredeFerdigstilt -> HttpStatusCode.BadRequest.errorJson(
                                    "Reguleringen er allerede ferdigstilt",
                                    "regulering_allerede_ferdigstilt",
                                )

                                KunneIkkeRegulereManuelt.FantIkkeRegulering -> HttpStatusCode.BadRequest.errorJson(
                                    "Fant ikke regulering",
                                    "fant_ikke_regulering",
                                )

                                KunneIkkeRegulereManuelt.BeregningFeilet -> HttpStatusCode.InternalServerError.errorJson(
                                    "Beregning feilet",
                                    "beregning_feilet",
                                )

                                KunneIkkeRegulereManuelt.SimuleringFeilet -> HttpStatusCode.InternalServerError.errorJson(
                                    "Simulering feilet",
                                    "simulering_feilet",
                                )

                                KunneIkkeRegulereManuelt.StansetYtelseMåStartesFørDenKanReguleres -> HttpStatusCode.BadRequest.errorJson(
                                    "Stanset ytelse må startes før den kan reguleres",
                                    "stanset_ytelse_må_startes_før_den_kan_reguleres",
                                )

                                is KunneIkkeRegulereManuelt.KunneIkkeFerdigstille -> HttpStatusCode.InternalServerError.errorJson(
                                    "Kunne ikke ferdigstille regulering på grunn av ${it.feil}",
                                    "kunne_ikke_ferdigstille_regulering",
                                )

                                KunneIkkeRegulereManuelt.FantIkkeSak -> Feilresponser.fantIkkeSak
                                KunneIkkeRegulereManuelt.StansetYtelseMåStartesFørDenKanReguleres -> HttpStatusCode.BadRequest.errorJson(
                                    "Kan ikke regulere mens sak avventer kravgrunnlag",
                                    "Kan_ikke_regulere_mens_sak_avventer_kravgrunnlag",
                                )

                                KunneIkkeRegulereManuelt.AvventerKravgrunnlag -> HttpStatusCode.BadRequest.errorJson(
                                    "Avventer kravgrunnlag",
                                    "regulering_avventer_kravgrunnlag",
                                )

                                KunneIkkeRegulereManuelt.HarPågåendeEllerBehovForAvkorting -> HttpStatusCode.BadRequest.errorJson(
                                    "Har pågående eller behov for avkorting",
                                    "regulering_har_pågående_eller_behov_for_avkorting",
                                )
                            }.let { feilResultat ->
                                call.svar(feilResultat)
                            }
                        },
                        ifRight = {
                            call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                            call.svar(Resultat.okJson())
                        },
                    )
                }
            }
        }
    }

    post("$reguleringPath/avslutt/{reguleringId}") {
        authorize(Brukerrolle.Saksbehandler) {
            call.lesUUID("reguleringId").fold(
                ifLeft = {
                    call.svar(HttpStatusCode.BadRequest.errorJson(it, "reguleringId_mangler_eller_feil_format"))
                },
                ifRight = {
                    reguleringService.avslutt(it).fold(
                        ifLeft = { feilmelding ->
                            call.svar(
                                when (feilmelding) {
                                    KunneIkkeAvslutte.FantIkkeRegulering -> HttpStatusCode.BadRequest.errorJson(
                                        "Fant ikke regulering",
                                        "fant_ikke_regulering",
                                    )

                                    KunneIkkeAvslutte.UgyldigTilstand -> HttpStatusCode.BadRequest.errorJson(
                                        "Ugyldig tilstand på reguleringen",
                                        "regulering_ugyldig_tilstand",
                                    )
                                },
                            )
                        },
                        ifRight = {
                            call.svar(Resultat.okJson())
                        },
                    )
                },
            )
        }
    }

    post("$reguleringPath/automatisk/dry") {
        authorize(Brukerrolle.Drift) {
            /**
             * @param fraOgMedMåned Måned i formatet yyyy-MM
             * @param virkningstidspunkt Dato i formatet yyyy-MM-dd, hvis null settes den til fraOgMedMåned
             * @param ikrafttredelse Dato i formatet yyyy-MM-dd, hvis null settes den til virkningstidspunkt
             * @param grunnbeløp Hvis null, bruker vi bare eksisterende verdier
             * @param garantipensjonOrdinær Hvis null, bruker vi bare eksisterende verdier
             * @param garantipensjonHøy Hvis null, bruker vi bare eksisterende verdier
             */
            data class Body(
                val fraOgMedMåned: String,
                val virkningstidspunkt: String?,
                val ikrafttredelse: String?,
                val grunnbeløp: Int? = null,
                val garantipensjonOrdinær: Int? = null,
                val garantipensjonHøy: Int? = null,
            ) {
                fun toCommand(): Either<Resultat, StartAutomatiskReguleringForInnsynCommand> {
                    val parsedFraOgMedMåned = Måned.parse(fraOgMedMåned) ?: return ugyldigMåned.left()
                    val parsedVirkningstidspunkt =
                        virkningstidspunkt?.let { LocalDate.parse(it) ?: return Feilresponser.ugyldigDato.left() }
                    val parsedIkrafttredelse =
                        ikrafttredelse?.let { LocalDate.parse(it) ?: return Feilresponser.ugyldigDato.left() }
                    return StartAutomatiskReguleringForInnsynCommand(
                        fraOgMedMåned = parsedFraOgMedMåned,
                        virkningstidspunkt = parsedVirkningstidspunkt ?: parsedFraOgMedMåned.fraOgMed,
                        ikrafttredelse = parsedIkrafttredelse ?: parsedFraOgMedMåned.fraOgMed,
                        grunnbeløp = grunnbeløp,
                        garantipensjonOrdinær = garantipensjonOrdinær,
                        garantipensjonHøy = garantipensjonHøy,
                    ).right()
                }
            }
            call.withBody<Body> { body ->
                call.svar(
                    body.toCommand().fold(
                        { it },
                        {
                            CoroutineScope(Dispatchers.IO).launch {
                                reguleringService.startAutomatiskReguleringForInnsyn(
                                    command = it,
                                )
                            }
                            Resultat.okJson()
                        },
                    ),
                )
            }
        }
    }
}

private fun List<FradragRequestJson>.toDomain(clock: Clock): Either<Resultat, List<Grunnlag.Fradragsgrunnlag>> {
    val (resultat, f) = this
        .map { it.toFradrag() }
        .separateEither()

    if (resultat.isNotEmpty()) return resultat.first().left()

    return f.map {
        Grunnlag.Fradragsgrunnlag.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fradrag = it,
        ).getOrElse {
            return HttpStatusCode.BadRequest.errorJson(
                message = "Kunne ikke lage fradrag",
                code = "kunne_ikke_lage_fradrag",
            ).left()
        }
    }.right()
}

@JvmName("toDomainUføregrunnlagJson")
private fun List<UføregrunnlagJson>.toDomain(clock: Clock): Either<Resultat, List<Grunnlag.Uføregrunnlag>> {
    return this.map {
        Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            periode = it.periode.toPeriode(),
            uføregrad = Uføregrad.tryParse(it.uføregrad).getOrElse {
                return Feilresponser.Uføre.uføregradMåVæreMellomEnOgHundre.left()
            },
            forventetInntekt = it.forventetInntekt,
        )
    }.right()
}
