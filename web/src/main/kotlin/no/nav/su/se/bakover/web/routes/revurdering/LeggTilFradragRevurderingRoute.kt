package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Fradragsgrunnlag
import no.nav.su.se.bakover.domain.grunnlag.fradrag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.vilkår.fradag.KunneIkkeLeggeTilFradragsgrunnlag
import no.nav.su.se.bakover.web.routes.grunnlag.tilResultat
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragRequestJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragRequestJson.Companion.toFradrag
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock

internal fun Route.leggTilFradragRevurdering(
    revurderingService: RevurderingService,
    clock: Clock,
    formuegrenserFactory: FormuegrenserFactory,
) {
    data class BeregningForRevurderingBody(
        val fradrag: List<FradragRequestJson>,
    ) {
        fun toDomain(clock: Clock): Either<Resultat, List<Fradragsgrunnlag>> =
            fradrag.toFradrag().map {
                it.map { fradrag ->
                    Fradragsgrunnlag.tryCreate(
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

    post("$REVURDERING_PATH/{revurderingId}/fradrag") {
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
                                    Resultat.json(HttpStatusCode.OK, serialize(it.toJson(formuegrenserFactory)))
                                }
                            }.getOrElse { it },
                        )
                    }
                }
            }
        }
    }
}
