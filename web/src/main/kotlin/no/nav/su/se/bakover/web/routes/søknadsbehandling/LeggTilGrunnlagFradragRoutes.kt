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
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkeBehandling
import no.nav.su.se.bakover.web.routes.Feilresponser.utenforBehandlingsperioden
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.UtenlandskInntektJson
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withSakId
import java.time.Clock
import java.util.UUID
import kotlin.reflect.KClass

internal fun Route.leggTilGrunnlagFradrag(
    behandlingService: SøknadsbehandlingService,
    clock: Clock,
) {

    data class Body(
        val fradrag: List<FradragsgrunnlagJson>,
    ) {
        fun toCommand(behandlingId: UUID, clock: Clock): Either<Resultat, LeggTilFradragsgrunnlagRequest> =
            LeggTilFradragsgrunnlagRequest(
                behandlingId = behandlingId,
                fradragsgrunnlag = fradrag.map { fradrag ->
                    Grunnlag.Fradragsgrunnlag.tryCreate(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        fradrag = FradragFactory.ny(
                            periode = fradrag.periode.toPeriode().getOrHandle { feilResultat ->
                                return feilResultat.left()
                            },
                            type = fradrag.type.let {
                                Fradragstype.tryParse(it).getOrHandle {
                                    return Behandlingsfeilresponser.ugyldigFradragstype.left()
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
                            Grunnlag.Fradragsgrunnlag.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag -> Behandlingsfeilresponser.ugyldigFradragstype.left()
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
                            body.toCommand(behandlingId, clock).flatMap { command ->
                                behandlingService.leggTilFradragsgrunnlag(command)
                                    .mapLeft {
                                        it.tilResultat()
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

internal fun KunneIkkeLeggeTilFradragsgrunnlag.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling -> {
            fantIkkeBehandling
        }
        KunneIkkeLeggeTilFradragsgrunnlag.GrunnlagetMåVæreInnenforBehandlingsperioden -> {
            utenforBehandlingsperioden
        }
        is KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand -> {
            Behandlingsfeilresponser.ugyldigTilstand(fra = this.fra)
        }
        KunneIkkeLeggeTilFradragsgrunnlag.PeriodeMangler -> {
            HttpStatusCode.BadRequest.errorJson("periode mangler", "periode_mangler")
        }
        is KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag -> {
            Feilresponser.kunneIkkeLeggeTilFradragsgrunnlag
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

internal object Behandlingsfeilresponser {
    val ugyldigFradragstype = HttpStatusCode.BadRequest.errorJson(
        "ugyldig fradragstype",
        "fradrag_ugyldig_fradragstype",
    )

    fun ugyldigTilstand(fra: KClass<*>): Resultat {
        return HttpStatusCode.BadRequest.errorJson(
            "Kan ikke legge til fradrag i tilstanden ${fra.simpleName}",
            "ugyldig_tilstand",
        )
    }
}
