package no.nav.su.se.bakover.web.routes.søknadsbehandling

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
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilFormuegrunnlag
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilFormuegrunnlag.FantIkkeSøknadsbehandling
import no.nav.su.se.bakover.service.vilkår.LeggTilFormuegrunnlagRequest
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.Feilresponser.depositumErHøyereEnnInnskudd
import no.nav.su.se.bakover.web.routes.grunnlag.FormuegrunnlagJson
import no.nav.su.se.bakover.web.routes.grunnlag.tilResultat
import no.nav.su.se.bakover.web.routes.periode.toPeriodeOrResultat
import no.nav.su.se.bakover.web.routes.søknadsbehandling.FormueBody.Companion.toServiceRequest
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withSakId
import java.util.UUID

private data class FormueBody(
    val periode: PeriodeJson,
    val epsFormue: FormuegrunnlagJson.VerdierJson?,
    val søkersFormue: FormuegrunnlagJson.VerdierJson,
    val begrunnelse: String?,
    val måInnhenteMerInformasjon: Boolean,
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

        fun List<FormueBody>.toServiceRequest(behandlingId: UUID): Either<Resultat, LeggTilFormuegrunnlagRequest> {
            if (this.isEmpty()) {
                return HttpStatusCode.BadRequest.errorJson(
                    "Formueliste kan ikke være tom",
                    "formueliste_kan_ikke_være_tom",
                ).left()
            }

            return LeggTilFormuegrunnlagRequest(
                behandlingId = behandlingId,
                formuegrunnlag = NonEmptyList.fromListUnsafe(
                    this.map { formueBody ->
                        LeggTilFormuegrunnlagRequest.Grunnlag.Søknadsbehandling(
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
                            måInnhenteMerInformasjon = formueBody.måInnhenteMerInformasjon,
                        )
                    },
                ),
            ).right()
        }
    }
}

internal fun Route.leggTilFormueForSøknadsbehandlingRoute(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
) {
    post("$behandlingPath/{behandlingId}/formuegrunnlag") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBehandlingId { behandlingId ->
                    call.withBody<List<FormueBody>> { body ->
                        body.toServiceRequest(behandlingId).mapLeft {
                            call.svar(it)
                        }.map { request ->
                            søknadsbehandlingService.leggTilFormuegrunnlag(
                                request,
                            ).map {
                                call.sikkerlogg("Lagret formue for revudering $behandlingId på $sakId")
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
    FantIkkeSøknadsbehandling -> Feilresponser.fantIkkeBehandling
    is KunneIkkeLeggeTilFormuegrunnlag.KunneIkkeLeggeTilFormuegrunnlagTilSøknadsbehandling -> when (val f = this.feil) {
        is Søknadsbehandling.KunneIkkeLeggeTilFormuegrunnlag.UgyldigTilstand -> Feilresponser.ugyldigTilstand(
            fra = f.fra,
            til = f.til,
        )
    }
    is KunneIkkeLeggeTilFormuegrunnlag.KunneIkkeMappeTilDomenet -> this.feil.tilResultat()
}
