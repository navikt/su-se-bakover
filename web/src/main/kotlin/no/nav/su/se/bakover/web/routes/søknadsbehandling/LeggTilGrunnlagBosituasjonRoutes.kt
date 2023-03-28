package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.merge
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeBehandling
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.vilkår.bosituasjon.BosituasjonValg
import no.nav.su.se.bakover.domain.vilkår.bosituasjon.FullførBosituasjonRequest
import no.nav.su.se.bakover.domain.vilkår.bosituasjon.KunneIkkeLeggeTilBosituasjonEpsGrunnlag
import no.nav.su.se.bakover.domain.vilkår.bosituasjon.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.grunnlag.tilResultat
import java.util.UUID

internal fun Route.leggTilGrunnlagBosituasjonRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
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
        val begrunnelse: String?,
    ) {
        fun toFullførBosituasjongrunnlagRequest(behandlingId: UUID): Either<Resultat, FullførBosituasjonRequest> {
            return FullførBosituasjonRequest(
                behandlingId = behandlingId,
                bosituasjon = bosituasjon,
            ).right()
        }
    }

    post("$behandlingPath/{behandlingId}/grunnlag/bosituasjon/eps") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withBody<EpsBody> { body ->
                    call.svar(
                        body.toLeggTilBosituasjonEpsgrunnlagRequest(behandlingId)
                            .map { leggTilBosituasjonEpsgrunnlagRequest ->
                                søknadsbehandlingService.leggTilBosituasjonEpsgrunnlag(
                                    leggTilBosituasjonEpsgrunnlagRequest,
                                    saksbehandler = call.suUserContext.saksbehandler,
                                ).fold(
                                    {
                                        when (it) {
                                            KunneIkkeLeggeTilBosituasjonEpsGrunnlag.FantIkkeBehandling -> fantIkkeBehandling
                                            KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KlarteIkkeHentePersonIPdl -> Feilresponser.fantIkkePerson
                                            is KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KunneIkkeOppdatereBosituasjon -> it.feil.tilResultat()
                                        }
                                    },
                                    {
                                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                                        Resultat.json(HttpStatusCode.Created, serialize(it.toJson(satsFactory)))
                                    },
                                )
                            }.merge(),
                    )
                }
            }
        }
    }

    post("$behandlingPath/{behandlingId}/grunnlag/bosituasjon/fullfør") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withBody<BosituasjonBody> { body ->
                    call.svar(
                        body.toFullførBosituasjongrunnlagRequest(behandlingId)
                            .flatMap { fullføreBosituasjongrunnlagRequest ->
                                søknadsbehandlingService.fullførBosituasjongrunnlag(
                                    fullføreBosituasjongrunnlagRequest,
                                    saksbehandler = call.suUserContext.saksbehandler,
                                ).mapLeft {
                                    when (it) {
                                        SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag.FantIkkeBehandling -> fantIkkeBehandling
                                        SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag.KlarteIkkeLagreBosituasjon -> Feilresponser.kunneIkkeLeggeTilBosituasjonsgrunnlag
                                        SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag.KlarteIkkeHentePersonIPdl -> Feilresponser.fantIkkePerson
                                        is SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag.KunneIkkeEndreBosituasjongrunnlag -> Feilresponser.kunneIkkeLeggeTilBosituasjonsgrunnlag
                                    }
                                }.map {
                                    call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                                    Resultat.json(HttpStatusCode.Created, serialize(it.toJson(satsFactory)))
                                }
                            }.merge(),
                    )
                }
            }
        }
    }
}
