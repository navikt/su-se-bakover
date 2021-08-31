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
        fun List<FormueBody>.toServiceRequest(revurderingId: UUID): Either<Resultat, LeggTilFormuegrunnlagRequest> {
            if (this.isEmpty()) {
                return Revurderingsfeilresponser.formueListeKanIkkeVæreTom.left()
            }

            return LeggTilFormuegrunnlagRequest(
                revurderingId = revurderingId,
                formuegrunnlag = NonEmptyList.fromListUnsafe(
                    this.map { formueBody ->
                        LeggTilFormuegrunnlagRequest.Grunnlag(
                            periode = formueBody.periode.toPeriode()
                                .getOrHandle { return it.left() },
                            epsFormue = formueBody.epsFormue?.let {
                                Formuegrunnlag.Verdier.tryCreate(
                                    verdiIkkePrimærbolig = formueBody.epsFormue.verdiIkkePrimærbolig,
                                    verdiEiendommer = formueBody.epsFormue.verdiEiendommer,
                                    verdiKjøretøy = formueBody.epsFormue.verdiKjøretøy,
                                    innskudd = formueBody.epsFormue.innskudd,
                                    verdipapir = formueBody.epsFormue.verdipapir,
                                    pengerSkyldt = formueBody.epsFormue.pengerSkyldt,
                                    kontanter = formueBody.epsFormue.kontanter,
                                    depositumskonto = formueBody.epsFormue.depositumskonto,
                                ).getOrHandle {
                                    return it.tilResultat().left()
                                }
                            },
                            søkersFormue = Formuegrunnlag.Verdier.tryCreate(
                                verdiIkkePrimærbolig = formueBody.søkersFormue.verdiIkkePrimærbolig,
                                verdiEiendommer = formueBody.søkersFormue.verdiEiendommer,
                                verdiKjøretøy = formueBody.søkersFormue.verdiKjøretøy,
                                innskudd = formueBody.søkersFormue.innskudd,
                                verdipapir = formueBody.søkersFormue.verdipapir,
                                pengerSkyldt = formueBody.søkersFormue.pengerSkyldt,
                                kontanter = formueBody.søkersFormue.kontanter,
                                depositumskonto = formueBody.søkersFormue.depositumskonto,
                            ).getOrHandle {
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
    KunneIkkeLageFormueVerdier.DepositumErStørreEnnInnskudd -> HttpStatusCode.BadRequest.errorJson(
        "Depositum er mindre enn innskudd",
        "depositum_mindre_enn_innskudd",
    )
    KunneIkkeLageFormueVerdier.VerdierKanIkkeVæreNegativ -> HttpStatusCode.BadRequest.errorJson(
        "Verdier kan ikke være negativ",
        "verdier_kan_ikke_være_negativ",
    )
}

private fun KunneIkkeLeggeTilFormuegrunnlag.tilResultat() = when (this) {
    KunneIkkeLeggeTilFormuegrunnlag.FantIkkeRevurdering -> Revurderingsfeilresponser.fantIkkeRevurdering
    is KunneIkkeLeggeTilFormuegrunnlag.UgyldigTilstand -> Revurderingsfeilresponser.ugyldigTilstand(this.fra, this.til)
    KunneIkkeLeggeTilFormuegrunnlag.IkkeLovMedOverlappendePerioder -> Revurderingsfeilresponser.ikkeLovMedOverlappendePerioder
    KunneIkkeLeggeTilFormuegrunnlag.EpsFormueperiodeErUtenforBosituasjonPeriode -> Revurderingsfeilresponser.epsFormueperiodeErUtenforBosituasjonPeriode
    KunneIkkeLeggeTilFormuegrunnlag.FormuePeriodeErUtenforBehandlingsperioden -> Revurderingsfeilresponser.formuePeriodeErUtenforBehandlingsperioden
    KunneIkkeLeggeTilFormuegrunnlag.MåHaEpsHvisManHarSattEpsFormue -> Revurderingsfeilresponser.måHaEpsHvisManHarSattEpsFormue
    KunneIkkeLeggeTilFormuegrunnlag.FradragForEpsSomIkkeHarEPS -> Feilresponser.kanIkkeHaEpsFradragUtenEps
    KunneIkkeLeggeTilFormuegrunnlag.FradragManglerBosituasjon -> Feilresponser.fradragManglerBosituasjon
    KunneIkkeLeggeTilFormuegrunnlag.MåLeggeTilBosituasjonFørFradrag -> Feilresponser.måHaBosituasjonFørFradrag
}
