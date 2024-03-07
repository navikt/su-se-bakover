package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import common.presentation.periode.toPeriodeOrResultat
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.depositumErHøyereEnnInnskudd
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.vilkår.formue.KunneIkkeLeggeTilFormuegrunnlag
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.web.routes.grunnlag.FormuegrunnlagJson
import no.nav.su.se.bakover.web.routes.grunnlag.tilResultat
import no.nav.su.se.bakover.web.routes.revurdering.FormueBody.Companion.toServiceRequest
import vilkår.formue.domain.FormuegrenserFactory
import vilkår.formue.domain.KunneIkkeLageFormueVerdier
import vilkår.formue.domain.Verdier
import vilkår.vurderinger.tilResultat
import java.time.Clock
import java.util.UUID

private data class FormueBody(
    val periode: PeriodeJson,
    val epsFormue: FormuegrunnlagJson.VerdierJson?,
    val søkersFormue: FormuegrunnlagJson.VerdierJson,
    val begrunnelse: String?,
) {

    companion object {
        private fun lagFormuegrunnlag(json: FormuegrunnlagJson.VerdierJson) =
            Verdier.tryCreate(
                verdiIkkePrimærbolig = json.verdiIkkePrimærbolig,
                verdiEiendommer = json.verdiEiendommer,
                verdiKjøretøy = json.verdiKjøretøy,
                innskudd = json.innskudd,
                verdipapir = json.verdipapir,
                pengerSkyldt = json.pengerSkyldt,
                kontanter = json.kontanter,
                depositumskonto = json.depositumskonto,
            )

        fun List<FormueBody>.toServiceRequest(revurderingId: UUID, saksbehandler: NavIdentBruker.Saksbehandler, clock: Clock): Either<Resultat, LeggTilFormuevilkårRequest> {
            if (this.isEmpty()) {
                return HttpStatusCode.BadRequest.errorJson(
                    "Formueliste kan ikke være tom",
                    "formueliste_kan_ikke_være_tom",
                ).left()
            }

            return LeggTilFormuevilkårRequest(
                behandlingId = RevurderingId(revurderingId),
                formuegrunnlag = this.map { formueBody ->
                    LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                        periode = formueBody.periode.toPeriodeOrResultat()
                            .getOrElse { return it.left() },
                        epsFormue = formueBody.epsFormue?.let {
                            lagFormuegrunnlag(formueBody.epsFormue).getOrElse {
                                return it.tilResultat().left()
                            }
                        },
                        søkersFormue = lagFormuegrunnlag(formueBody.søkersFormue).getOrElse {
                            return it.tilResultat().left()
                        },
                        begrunnelse = formueBody.begrunnelse,
                    )
                }.toNonEmptyList(),
                saksbehandler = saksbehandler,
                tidspunkt = Tidspunkt.now(clock),

            ).right()
        }
    }
}

internal fun Route.leggTilFormueRevurderingRoute(
    revurderingService: RevurderingService,
    formuegrenserFactory: FormuegrenserFactory,
    clock: Clock,
) {
    post("$REVURDERING_PATH/{revurderingId}/formuegrunnlag") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withBody<List<FormueBody>> { body ->
                        body.toServiceRequest(revurderingId, call.suUserContext.saksbehandler, clock)
                            .mapLeft { call.svar(it) }
                            .map { request ->
                                revurderingService.leggTilFormuegrunnlag(request)
                                    .map {
                                        call.audit(it.revurdering.fnr, AuditLogEvent.Action.UPDATE, it.revurdering.id.value)
                                        call.sikkerlogg("Lagret formue for revudering $revurderingId på $sakId")
                                        call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(formuegrenserFactory))))
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
    KunneIkkeLeggeTilFormuegrunnlag.FantIkkeRevurdering -> Revurderingsfeilresponser.fantIkkeRevurdering
    is KunneIkkeLeggeTilFormuegrunnlag.UgyldigTilstand -> Feilresponser.ugyldigTilstand(this.fra, this.til)
    is KunneIkkeLeggeTilFormuegrunnlag.Konsistenssjekk -> this.feil.tilResultat()
    is KunneIkkeLeggeTilFormuegrunnlag.KunneIkkeMappeTilDomenet -> this.feil.tilResultat()
}
