package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.merge
import arrow.core.raise.either
import arrow.core.right
import behandling.domain.UnderkjennAttesteringsgrunnBehandling
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.extensions.enumContains
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.attestantOgSaksbehandlerKanIkkeVæreSammePerson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigBody
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigTilstand
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.underkjenn.KunneIkkeUnderkjenneRevurdering
import no.nav.su.se.bakover.web.inputvalidation.sti
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock

data class UnderkjennBody(
    val grunn: String,
    val kommentar: String,
) {
    private fun valid() = enumContains<UnderkjennAttesteringsgrunnBehandling>(grunn) && kommentar.isNotBlank()

    internal fun toDomain(navIdent: String, clock: Clock): Either<Resultat, Attestering.Underkjent> {
        if (valid()) {
            return Attestering.Underkjent(
                attestant = NavIdentBruker.Attestant(navIdent),
                grunn = UnderkjennAttesteringsgrunnBehandling.valueOf(this.grunn),
                kommentar = this.kommentar,
                opprettet = Tidspunkt.now(clock),
            ).right()
        }
        return ugyldigBody.left()
    }
}

internal fun Route.underkjennRevurdering(
    revurderingService: RevurderingService,
    formuegrenserFactory: FormuegrenserFactory,
    clock: Clock,
    log: Logger = LoggerFactory.getLogger("no.nav.su.se.bakover.web.routes.revurdering.Route.underkjennRevurdering"),
) {
    patch("$REVURDERING_PATH/{revurderingId}/underkjenn") {
        authorize(Brukerrolle.Attestant) {
            call.withRevurderingId { revurderingId ->
                call.withValidertBody<UnderkjennBody>(
                    log,
                    maksLengde = mapOf(
                        sti(UnderkjennBody::grunn) to 100,
                        sti(UnderkjennBody::kommentar) to 2000,
                    ),
                ) { body ->
                    either {
                        val underkjent = body.toDomain(call.suUserContext.navIdent, clock).bind()
                        val revurdering = revurderingService.underkjenn(
                            revurderingId = RevurderingId(revurderingId),
                            attestering = underkjent,
                        ).mapLeft { kunneIkkeUnderkjenneRevurdering ->
                            when (kunneIkkeUnderkjenneRevurdering) {
                                KunneIkkeUnderkjenneRevurdering.FantIkkeRevurdering -> fantIkkeRevurdering
                                is KunneIkkeUnderkjenneRevurdering.UgyldigTilstand -> ugyldigTilstand(
                                    kunneIkkeUnderkjenneRevurdering.fra,
                                    kunneIkkeUnderkjenneRevurdering.til,
                                )
                                KunneIkkeUnderkjenneRevurdering.SaksbehandlerOgAttestantKanIkkeVæreSammePerson -> attestantOgSaksbehandlerKanIkkeVæreSammePerson
                            }
                        }.bind()

                        call.sikkerlogg("Underkjente behandling med id: $revurderingId")
                        call.audit(revurdering.fnr, AuditLogEvent.Action.UPDATE, revurdering.id.value)
                        Resultat.json(HttpStatusCode.OK, serialize(revurdering.toJson(formuegrenserFactory)))
                    }.merge().let { resultat -> call.svar(resultat) }
                }
            }
        }
    }
}
