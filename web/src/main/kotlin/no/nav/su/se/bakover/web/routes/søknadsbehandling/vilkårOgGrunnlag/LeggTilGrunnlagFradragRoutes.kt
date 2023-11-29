package no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.merge
import arrow.core.right
import beregning.domain.fradrag.FradragFactory
import beregning.domain.fradrag.FradragTilhører
import beregning.domain.fradrag.Fradragstype
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeBehandling
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.utenforBehandlingsperioden
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.fradrag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag
import no.nav.su.se.bakover.web.routes.periode.toPeriodeOrResultat
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SØKNADSBEHANDLING_PATH
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.UtenlandskInntektJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.toJson
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock
import java.util.UUID
import kotlin.reflect.KClass

internal fun Route.leggTilGrunnlagFradrag(
    behandlingService: SøknadsbehandlingService,
    clock: Clock,
    formuegrenserFactory: FormuegrenserFactory,
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
                        fradrag = FradragFactory.nyFradragsperiode(
                            periode = fradrag.periode.toPeriodeOrResultat().getOrElse { feilResultat ->
                                return feilResultat.left()
                            },
                            fradragstype = fradrag.type.let {
                                Fradragstype.tryParse(it, fradrag.beskrivelse).getOrElse {
                                    return Behandlingsfeilresponser.ugyldigFradragstype.left()
                                }
                            },
                            månedsbeløp = fradrag.beløp,
                            utenlandskInntekt = fradrag.utenlandskInntekt?.toUtenlandskInntekt()
                                ?.getOrElse { feilResultat ->
                                    return feilResultat.left()
                                },
                            tilhører = fradrag.tilhører.let { FradragTilhører.valueOf(it) },
                        ),
                    ).getOrElse {
                        return when (it) {
                            Grunnlag.Fradragsgrunnlag.UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag -> Behandlingsfeilresponser.ugyldigFradragstype.left()
                        }
                    }
                },
            ).right()
    }

    post("$SØKNADSBEHANDLING_PATH/{behandlingId}/fradrag") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBehandlingId { behandlingId ->
                    call.withBody<Body> { body ->
                        call.svar(
                            body.toCommand(behandlingId, clock).flatMap { command ->
                                behandlingService.leggTilFradragsgrunnlag(
                                    command,
                                    saksbehandler = call.suUserContext.saksbehandler,
                                )
                                    .mapLeft { it.tilResultat() }
                                    .map {
                                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                                        call.sikkerlogg("Lagret fradrag for behandling $behandlingId på $sakId")
                                        Resultat.json(HttpStatusCode.Created, serialize(it.toJson(formuegrenserFactory)))
                                    }
                            }.merge(),
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

        is KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag -> {
            Feilresponser.kunneIkkeLeggeTilFradragsgrunnlag
        }
    }
}

private data class FradragsgrunnlagJson(
    val periode: PeriodeJson,
    val type: String,
    val beskrivelse: String?,
    val beløp: Double,
    val utenlandskInntekt: UtenlandskInntektJson?,
    val tilhører: String,
)

internal data object Behandlingsfeilresponser {
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
