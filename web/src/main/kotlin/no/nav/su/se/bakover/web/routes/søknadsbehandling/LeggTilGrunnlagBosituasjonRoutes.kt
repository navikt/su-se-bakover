package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.right
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.BosituasjonValg
import no.nav.su.se.bakover.service.vilkår.FullførBosituasjonRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody
import java.util.UUID

internal fun Route.leggTilGrunnlagBosituasjonRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
) {
    data class EpsBody(
        val epsFnr: String?,
    ) {
        fun toLeggTilBosituasjonEpsgrunnlagRequest(behandlingId: UUID): Either<Resultat, LeggTilBosituasjonEpsRequest> {
            return LeggTilBosituasjonEpsRequest(
                behandlingId = behandlingId,
                epsFnr = epsFnr?.let { Fnr(epsFnr) },
            ).right()
        }
    }

    data class BosituasjonBody(
        val bosituasjon: BosituasjonValg,
        val begrunnelse: String?
    ) {
        fun toFullførBosituasjongrunnlagRequest(behandlingId: UUID): Either<Resultat, FullførBosituasjonRequest> {
            return FullførBosituasjonRequest(
                behandlingId = behandlingId,
                bosituasjon = bosituasjon,
                begrunnelse = begrunnelse,
            ).right()
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/grunnlag/bosituasjon/eps") {
            call.withBehandlingId { behandlingId ->
                call.withBody<EpsBody> { body ->
                    call.svar(
                        body.toLeggTilBosituasjonEpsgrunnlagRequest(behandlingId)
                            .flatMap { leggTilBosituasjonEpsgrunnlagRequest ->
                                søknadsbehandlingService.leggTilBosituasjonEpsgrunnlag(
                                    leggTilBosituasjonEpsgrunnlagRequest,
                                ).mapLeft {
                                    when (it) {
                                        SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
                                        is SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag.UgyldigTilstand -> Revurderingsfeilresponser.ugyldigTilstand(
                                            it.fra,
                                            it.til,
                                        )
                                        SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KlarteIkkeHentePersonIPdl -> Feilresponser.fantIkkePerson
                                        is SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KunneIkkeEndreBosituasjonEpsGrunnlag -> Feilresponser.kunneIkkeLeggeTilFradragsgrunnlag
                                    }
                                }.map {
                                    Resultat.json(HttpStatusCode.Created, serialize(it.toJson()))
                                }
                            }.getOrHandle {
                                it
                            },
                    )
                }
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/grunnlag/bosituasjon/fullfør") {
            call.withBehandlingId { behandlingId ->
                call.withBody<BosituasjonBody> { body ->
                    call.svar(
                        body.toFullførBosituasjongrunnlagRequest(behandlingId)
                            .flatMap { fullføreBosituasjongrunnlagRequest ->
                                søknadsbehandlingService.fullførBosituasjongrunnlag(
                                    fullføreBosituasjongrunnlagRequest,
                                ).mapLeft {
                                    when (it) {
                                        SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
                                        is SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag.UgyldigTilstand -> Revurderingsfeilresponser.ugyldigTilstand(
                                            it.fra,
                                            it.til,
                                        )
                                        SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag.KlarteIkkeLagreBosituasjon -> Feilresponser.kunneIkkeLeggeTilBosituasjonsgrunnlag
                                        SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag.KlarteIkkeHentePersonIPdl -> Feilresponser.fantIkkePerson
                                        is SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag.KunneIkkeEndreBosituasjongrunnlag -> Feilresponser.kunneIkkeLeggeTilBosituasjonsgrunnlag
                                    }
                                }.map {
                                    Resultat.json(HttpStatusCode.Created, serialize(it.toJson()))
                                }
                            }.getOrHandle {
                                it
                            },
                    )
                }
            }
        }
    }
}
