package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.UtenlandskInntektJson
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withSakId
import java.util.UUID
import kotlin.reflect.KClass

internal fun Route.leggTilGrunnlagFradrag(
    behandlingService: SøknadsbehandlingService,
) {

    data class Body(
        val fradrag: List<FradragsgrunnlagJson>,
    ) {
        fun toCommand(behandlingId: UUID): Either<Resultat, LeggTilFradragsgrunnlagRequest> =
            LeggTilFradragsgrunnlagRequest(
                behandlingId = behandlingId,
                fradragsrunnlag = fradrag.map { fradrag ->
                    Grunnlag.Fradragsgrunnlag.tryCreate(
                        fradrag = FradragFactory.ny(
                            periode = fradrag.periode.toPeriode().getOrHandle { feilResultat ->
                                return feilResultat.left()
                            },
                            type = fradrag.type.let {
                                Fradragstype.tryParse(it).getOrHandle {
                                    return HttpStatusCode.BadRequest.errorJson(
                                        "Ugyldig fradragstype",
                                        "ugyldig_fradragstype",
                                    )
                                        .left()
                                }
                            },
                            månedsbeløp = fradrag.beløp,
                            utenlandskInntekt = fradrag.utenlandskInntekt?.toUtenlandskInntekt()
                                ?.getOrHandle { feilResultat ->
                                    return feilResultat.left()
                                },
                            tilhører = fradrag.tilhører.let { FradragTilhører.valueOf(it) },
                        ),
                    ).getOrHandle {
                        return when (it) {
                            Grunnlag.Fradragsgrunnlag.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag -> HttpStatusCode.BadRequest.errorJson(
                                "Ugyldig fradragstype",
                                "ugyldig_fradragstype",
                            ).left()
                        }
                    }
                },
            ).right()
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/grunnlag/fradrag") {
            call.withSakId { sakId ->
                call.withBehandlingId { behandlingId ->
                    call.withBody<Body> { body ->
                        call.svar(
                            body.toCommand(behandlingId).flatMap { command ->
                                behandlingService.leggTilFradragGrunnlag(command)
                                    .mapLeft {
                                        when (it) {
                                            SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling -> Behandlingsfeilresponser.fantIkkeBehandling
                                            SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag.FradragsgrunnlagUtenforPeriode -> Behandlingsfeilresponser.fradragsperiodeErUtenforBehandlingsperioden
                                            SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag.HarIkkeEktelle -> Behandlingsfeilresponser.måHaEpsHvisManHarfradragForEps
                                            SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag.KlarteIkkeLagreFradrag -> Behandlingsfeilresponser.kunneIkkeLagreFradrag
                                            SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag.UgyldigFradragstypeForGrunnlag -> Behandlingsfeilresponser.ugyldigFradragstype
                                            is SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand -> Behandlingsfeilresponser.ugyldigTilstand(
                                                fra = it.fra,
                                                til = it.til,
                                            )
                                        }
                                    }
                                    .map {
                                        call.sikkerlogg("Lagret fradrag for behandling $behandlingId på $sakId")
                                        Resultat.json(
                                            HttpStatusCode.Created,
                                            serialize(it.toJson()),
                                        )
                                    }
                            }.getOrHandle { it },
                        )
                    }
                }
            }
        }
    }
}

private data class FradragsgrunnlagJson(
    val periode: PeriodeJson,
    val type: String,
    val beløp: Double,
    val utenlandskInntekt: UtenlandskInntektJson?,
    val tilhører: String,
)

// sealed class UgyldigFradrag {
//     object IkkeLovMedFradragUtenforPerioden : UgyldigFradrag()
//     object UgyldigFradragstype : UgyldigFradrag()
//     object HarIkkeEktelle : UgyldigFradrag()
// }

// fun toFradrag(
//     stønadsperiode: Stønadsperiode,
//     harEktefelle: Boolean,
//     clock: Clock,
// ): Either<UgyldigFradrag, List<Fradrag>> =
//     fradrag.map {
//         // map til grunnlag for å låne valideringer
//         Grunnlag.Fradragsgrunnlag(
//             fradrag = FradragFactory.ny(
//                 type = it.type,
//                 månedsbeløp = it.månedsbeløp,
//                 periode = it.periode ?: stønadsperiode.periode,
//                 utenlandskInntekt = it.utenlandskInntekt,
//                 tilhører = it.tilhører,
//             ),
//             opprettet = Tidspunkt.now(clock),
//         )
//     }.valider(stønadsperiode.periode, harEktefelle)
//         .mapLeft { valideringsfeil ->
//             when (valideringsfeil) {
//                 Grunnlag.Fradragsgrunnlag.Validator.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag -> UgyldigFradrag.UgyldigFradragstype
//                 Grunnlag.Fradragsgrunnlag.Validator.UgyldigFradragsgrunnlag.UtenforBehandlingsperiode -> UgyldigFradrag.IkkeLovMedFradragUtenforPerioden
//                 Grunnlag.Fradragsgrunnlag.Validator.UgyldigFradragsgrunnlag.HarIkkeEktelle -> UgyldigFradrag.HarIkkeEktelle
//             }
//         }
//         .map { fradragsgrunnlag ->
//             fradragsgrunnlag.map { it.fradrag }
//         }

internal object Behandlingsfeilresponser {
    val fantIkkeBehandling = HttpStatusCode.NotFound.errorJson(
        "Fant ikke behandling",
        "fant_ikke_behandling",
    )

    val fradragsperiodeErUtenforBehandlingsperioden = HttpStatusCode.BadRequest.errorJson(
        "Ikke lov med fradrag utenfor perioden",
        "ikke_lov_med_fradrag_utenfor_perioden",
    )

    val måHaEpsHvisManHarfradragForEps = HttpStatusCode.BadRequest.errorJson(
        "Ikke lov med fradrag for eps hvis man ikke har eps",
        "ikke_lov_med_fradrag_for_eps_hvis_man_ikke_har_eps",
    )

    val kunneIkkeLagreFradrag = HttpStatusCode.InternalServerError.errorJson(
        "Kunne ikke lagre fradrag",
        "kunne_ikke_lagre_fradrag",
    )

    val ugyldigFradragstype = HttpStatusCode.BadRequest.errorJson(
        "ugyldig fradragstype",
        "fradrag_ugyldig_fradragstype",
    )

    fun ugyldigTilstand(fra: KClass<*>, til: KClass<*>): Resultat {
        return HttpStatusCode.BadRequest.errorJson(
            "Kan ikke gå fra tilstanden ${fra.simpleName} til tilstanden ${til.simpleName}",
            "ugyldig_tilstand",
        )
    }
}
