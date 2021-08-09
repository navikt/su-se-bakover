package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.service.revurdering.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson.Companion.toFradrag
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withSakId
import java.time.Clock
import kotlin.reflect.KClass

internal fun Route.leggTilGrunnlagFradrag(
    behandlingService: SøknadsbehandlingService,
    clock: Clock,
) {
    data class FradragListeBody(
        val fradrag: List<FradragJson>,
    ) {
        fun toDomain(clock: Clock): Either<Resultat, List<Grunnlag.Fradragsgrunnlag>> =
            fradrag.toFradrag().map {
                it.map { fradrag ->
                    Grunnlag.Fradragsgrunnlag(
                        fradrag = fradrag,
                        opprettet = Tidspunkt.now(clock),
                    )
                }
            }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/grunnlag/fradrag") {
            call.withSakId { sakId ->
                call.withBehandlingId { behandlingId ->
                    call.withBody<FradragListeBody> { body ->
                        call.svar(
                            body.toDomain(clock).flatMap { fradrag ->
                                behandlingService.leggTilFradragGrunnlag(
                                    LeggTilFradragsgrunnlagRequest(
                                        behandlingId,
                                        fradrag,
                                    ),
                                ).mapLeft {
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
                                }.map {
                                    call.sikkerlogg("Lagret fradrag for behandling $behandlingId på $sakId")
                                    Resultat.json(
                                        HttpStatusCode.OK,
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

internal object Behandlingsfeilresponser {
    val fantIkkeBehandling = HttpStatusCode.NotFound.errorJson(
        "Fant ikke behandling",
        "fant_ikke_behandling",
    )

    val fradragsperiodeErUtenforBehandlingsperioden = HttpStatusCode.BadRequest.errorJson(
        "Ikke lov med fradragperiode utenfor behandlingsperioden",
        "ikke_lov_med_fradragperiode_utenfor_behandlingsperioden",
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
