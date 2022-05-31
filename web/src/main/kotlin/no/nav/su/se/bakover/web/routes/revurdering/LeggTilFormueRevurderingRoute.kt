package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageFormueVerdier
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilFormuegrunnlag
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.vilkår.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.Feilresponser.depositumErHøyereEnnInnskudd
import no.nav.su.se.bakover.web.routes.grunnlag.FormuegrunnlagJson
import no.nav.su.se.bakover.web.routes.grunnlag.tilResultat
import no.nav.su.se.bakover.web.routes.periode.toPeriodeOrResultat
import no.nav.su.se.bakover.web.routes.revurdering.FormueBody.Companion.toServiceRequest
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

        fun List<FormueBody>.toServiceRequest(revurderingId: UUID): Either<Resultat, LeggTilFormuevilkårRequest> {
            if (this.isEmpty()) {
                return HttpStatusCode.BadRequest.errorJson(
                    "Formueliste kan ikke være tom",
                    "formueliste_kan_ikke_være_tom",
                ).left()
            }

            return LeggTilFormuevilkårRequest(
                behandlingId = revurderingId,
                formuegrunnlag = NonEmptyList.fromListUnsafe(
                    this.map { formueBody ->
                        LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                            periode = formueBody.periode.toPeriodeOrResultat()
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
    satsFactory: SatsFactory,
) {
    post("$revurderingPath/{revurderingId}/formuegrunnlag") {
        authorize(Brukerrolle.Saksbehandler) {
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
                                        serialize(it.toJson(satsFactory)),
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
    is KunneIkkeLeggeTilFormuegrunnlag.Konsistenssjekk -> this.feil.tilResultat()
    is KunneIkkeLeggeTilFormuegrunnlag.KunneIkkeMappeTilDomenet -> this.feil.tilResultat()
}
