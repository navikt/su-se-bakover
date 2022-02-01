package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageFormueVerdier
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilFormuegrunnlag
import no.nav.su.se.bakover.service.revurdering.LeggTilFormuegrunnlagRequest
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.Feilresponser.depositumErHøyereEnnInnskudd
import no.nav.su.se.bakover.web.routes.grunnlag.FormuegrunnlagJson
import no.nav.su.se.bakover.web.routes.revurdering.FormueBody.Companion.toServiceRequest
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import no.nav.su.se.bakover.web.withSakId
import java.util.UUID

private data class FormueBody(
    val periode: PeriodeJson,
    val epsFormue: FormuegrunnlagJson.VerdierJson?,
    val søkersFormue: FormuegrunnlagJson.VerdierJson,
    val begrunnelse: String?,
) {

    companion object {
        private fun lagFormuegrunnlag(json: FormuegrunnlagJson.VerdierJson) =
            Formuegrunnlag.Verdier.tryCreate(
                verdiIkkePrimærbolig = json.verdiIkkePrimærbolig,
                verdiEiendommer = json.verdiEiendommer,
                verdiKjøretøy = json.verdiKjøretøy,
                innskudd = json.innskudd,
                verdipapir = json.verdipapir,
                pengerSkyldt = json.pengerSkyldt,
                kontanter = json.kontanter,
                depositumskonto = json.depositumskonto,
            )

        fun List<FormueBody>.toServiceRequest(revurderingId: UUID): Either<Resultat, LeggTilFormuegrunnlagRequest> {
            if (this.isEmpty()) {
                return HttpStatusCode.BadRequest.errorJson(
                    "Formueliste kan ikke være tom",
                    "formueliste_kan_ikke_være_tom",
                ).left()
            }

            return LeggTilFormuegrunnlagRequest(
                revurderingId = revurderingId,
                formuegrunnlag = NonEmptyList.fromListUnsafe(
                    this.map { formueBody ->
                        LeggTilFormuegrunnlagRequest.Grunnlag(
                            periode = formueBody.periode.toPeriode()
                                .getOrHandle { return it.left() },
                            epsFormue = formueBody.epsFormue?.let {
                                lagFormuegrunnlag(formueBody.epsFormue).getOrHandle {
                                    return it.tilResultat().left()
                                }
                            },
                            søkersFormue = lagFormuegrunnlag(formueBody.søkersFormue).getOrHandle {
                                return it.tilResultat().left()
                            },
                            begrunnelse = formueBody.begrunnelse,
                        )
                    },
                ),
            ).right()
        }
    }
}

internal fun Route.leggTilFormueRevurderingRoute(
    revurderingService: RevurderingService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/{revurderingId}/formuegrunnlag") {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withBody<List<FormueBody>> { body ->
                        body.toServiceRequest(revurderingId).mapLeft {
                            call.svar(it)
                        }.map { request ->
                            revurderingService.leggTilFormuegrunnlag(
                                request,
                            ).map {
                                call.sikkerlogg("Lagret formue for revudering $revurderingId på $sakId")
                                call.svar(
                                    Resultat.json(
                                        HttpStatusCode.OK,
                                        serialize(it.toJson()),
                                    ),
                                )
                            }.mapLeft { kunneIkkeLeggeTilFormuegrunnlag ->
                                call.svar(kunneIkkeLeggeTilFormuegrunnlag.tilResultat())
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun KunneIkkeLageFormueVerdier.tilResultat() = when (this) {
    KunneIkkeLageFormueVerdier.DepositumErStørreEnnInnskudd -> depositumErHøyereEnnInnskudd
    KunneIkkeLageFormueVerdier.VerdierKanIkkeVæreNegativ -> HttpStatusCode.BadRequest.errorJson(
        "Verdier kan ikke være negativ",
        "verdier_kan_ikke_være_negativ",
    )
}

private fun KunneIkkeLeggeTilFormuegrunnlag.tilResultat() = when (this) {
    KunneIkkeLeggeTilFormuegrunnlag.FantIkkeRevurdering -> Revurderingsfeilresponser.fantIkkeRevurdering
    is KunneIkkeLeggeTilFormuegrunnlag.UgyldigTilstand -> Feilresponser.ugyldigTilstand(this.fra, this.til)
    KunneIkkeLeggeTilFormuegrunnlag.IkkeLovMedOverlappendePerioder -> Feilresponser.overlappendeVurderingsperioder
    KunneIkkeLeggeTilFormuegrunnlag.EpsFormueperiodeErUtenforBosituasjonPeriode -> HttpStatusCode.BadRequest.errorJson(
        "Ikke lov med formueperiode utenfor bosituasjonperioder",
        "ikke_lov_med_formueperiode_utenfor_bosituasjonperiode",
    )
    KunneIkkeLeggeTilFormuegrunnlag.FormuePeriodeErUtenforBehandlingsperioden -> HttpStatusCode.BadRequest.errorJson(
        "Ikke lov med formueperiode utenfor behandlingsperioden",
        "ikke_lov_med_formueperiode_utenfor_behandlingsperioden",
    )
    KunneIkkeLeggeTilFormuegrunnlag.MåHaEpsHvisManHarSattEpsFormue -> HttpStatusCode.BadRequest.errorJson(
        "Ikke lov med formue for eps hvis man ikke har eps",
        "ikke_lov_med_formue_for_eps_hvis_man_ikke_har_eps",
    )
}
