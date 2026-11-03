package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.merge
import arrow.core.raise.either
import behandling.domain.fradrag.LeggTilFradragsgrunnlagRequest
import common.presentation.beregning.FradragRequestJson
import common.presentation.beregning.FradragRequestJson.Companion.toFradrag
import common.presentation.beregning.UtenlandskInntektJson
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
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
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.vilkår.fradag.KunneIkkeLeggeTilFradragsgrunnlag
import no.nav.su.se.bakover.web.inputvalidation.sti
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import no.nav.su.se.bakover.web.routes.grunnlag.tilResultat
import org.slf4j.LoggerFactory
import vilkår.formue.domain.FormuegrenserFactory
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import java.time.Clock

data class LeggTilFradragRevurderingBody(
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

internal fun Route.leggTilFradragRevurdering(
    revurderingService: RevurderingService,
    clock: Clock,
    formuegrenserFactory: FormuegrenserFactory,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    post("$REVURDERING_PATH/{revurderingId}/fradrag") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withValidertBody<LeggTilFradragRevurderingBody>(
                        log,
                        maksLengde = mapOf(
                            sti(LeggTilFradragRevurderingBody::fradrag, FradragRequestJson::type) to 100,
                            sti(LeggTilFradragRevurderingBody::fradrag, FradragRequestJson::beskrivelse) to 2000,
                            sti(LeggTilFradragRevurderingBody::fradrag, FradragRequestJson::tilhører) to 100,
                            sti(
                                LeggTilFradragRevurderingBody::fradrag,
                                FradragRequestJson::utenlandskInntekt,
                                UtenlandskInntektJson::valuta,
                            ) to 100,
                        ),
                    ) { body ->
                        either {
                            val fradrag = body.toDomain(clock).bind()
                            val oppdatert = revurderingService.leggTilFradragsgrunnlag(
                                LeggTilFradragsgrunnlagRequest(RevurderingId(revurderingId), fradrag),
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

                                    KunneIkkeLeggeTilFradragsgrunnlag.FradrageneMåSlåsSammen -> Feilresponser.fradragMåSlåesSammen
                                }
                            }.bind()

                            call.audit(oppdatert.revurdering.fnr, AuditLogEvent.Action.UPDATE, oppdatert.revurdering.id.value)
                            call.sikkerlogg("Lagret fradrag for revudering $revurderingId på $sakId")
                            Resultat.json(HttpStatusCode.OK, serialize(oppdatert.toJson(formuegrenserFactory)))
                        }.merge().let { call.svar(it) }
                    }
                }
            }
        }
    }
}
