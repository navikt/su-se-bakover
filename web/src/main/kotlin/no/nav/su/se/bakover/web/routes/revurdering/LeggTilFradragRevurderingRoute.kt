package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilFradragsgrunnlag
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragRequestJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragRequestJson.Companion.toFradrag
import no.nav.su.se.bakover.web.routes.søknadsbehandling.tilResultat
import java.time.Clock

internal fun Route.leggTilFradragRevurdering(
    revurderingService: RevurderingService,
    clock: Clock,
    satsFactory: SatsFactory,
) {
    data class BeregningForRevurderingBody(
        val fradrag: List<FradragRequestJson>,
    ) {
        fun toDomain(clock: Clock): Either<Resultat, List<Grunnlag.Fradragsgrunnlag>> =
            fradrag.toFradrag().map {
                it.map { fradrag ->
                    Grunnlag.Fradragsgrunnlag.tryCreate(
                        fradrag = fradrag,
                        opprettet = Tidspunkt.now(clock),
                    ).getOrElse {
                        return BadRequest.errorJson(
                            message = "Kunne ikke lage fradrag",
                            code = "kunne_ikke_lage_fradrag",
                        ).left()
                    }
                }
            }
    }

    post("$revurderingPath/{revurderingId}/fradrag") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withBody<BeregningForRevurderingBody> { body ->
                        call.svar(
                            body.toDomain(clock).flatMap { fradrag ->
                                revurderingService.leggTilFradragsgrunnlag(
                                    LeggTilFradragsgrunnlagRequest(revurderingId, fradrag),
                                ).mapLeft {
                                    when (it) {
                                        KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling -> {
                                            Revurderingsfeilresponser.fantIkkeRevurdering
                                        }

                                        is KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand -> {
                                            Feilresponser.ugyldigTilstand(fra = it.fra, til = it.til)
                                        }

                                        is KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag -> {
                                            it.feil.tilResultat()
                                        }
                                    }
                                }.map {
                                    call.audit(it.revurdering.fnr, AuditLogEvent.Action.UPDATE, it.revurdering.id)
                                    call.sikkerlogg("Lagret fradrag for revudering $revurderingId på $sakId")
                                    Resultat.json(HttpStatusCode.OK, serialize(it.toJson(satsFactory)))
                                }
                            }.getOrHandle { it },
                        )
                    }
                }
            }
        }
    }
}
