package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.Attestering
import no.nav.su.se.bakover.common.extensions.enumContains
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.attestantOgSaksbehandlerKanIkkeVæreSammePerson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeAktørId
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.kunneIkkeOppretteOppgave
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigBody
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigTilstand
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.deserialize
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.underkjenn.KunneIkkeUnderkjenneRevurdering
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock

data class UnderkjennBody(
    val grunn: String,
    val kommentar: String,
) {
    private fun valid() = enumContains<Attestering.Underkjent.Grunn>(grunn) && kommentar.isNotBlank()

    internal fun toDomain(navIdent: String, clock: Clock): Either<Resultat, Attestering.Underkjent> {
        if (valid()) {
            return Attestering.Underkjent(
                attestant = NavIdentBruker.Attestant(navIdent),
                grunn = Attestering.Underkjent.Grunn.valueOf(this.grunn),
                kommentar = this.kommentar,
                opprettet = Tidspunkt.now(clock),
            ).right()
        }
        return ugyldigBody.left()
    }
}

internal fun Route.underkjennRevurdering(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
    clock: Clock,
    log: Logger = LoggerFactory.getLogger("Route.underkjennRevurdering"),
) {
    patch("$REVURDERING_PATH/{revurderingId}/underkjenn") {
        authorize(Brukerrolle.Attestant) {
            val navIdent = call.suUserContext.navIdent

            call.withRevurderingId { revurderingId ->
                Either.catch { deserialize<UnderkjennBody>(call) }.fold(
                    ifLeft = {
                        log.info("Ugyldig body: ", it)
                        return@authorize call.svar(ugyldigBody)
                    },
                    ifRight = { body ->
                        body.toDomain(navIdent, clock).fold(
                            ifLeft = { return@authorize call.svar(it) },
                            ifRight = { underkjent ->
                                revurderingService.underkjenn(
                                    revurderingId = revurderingId,
                                    attestering = underkjent,
                                ).fold(
                                    ifLeft = {
                                        val resultat = when (it) {
                                            KunneIkkeUnderkjenneRevurdering.FantIkkeRevurdering -> fantIkkeRevurdering
                                            KunneIkkeUnderkjenneRevurdering.FantIkkeAktørId -> fantIkkeAktørId
                                            KunneIkkeUnderkjenneRevurdering.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
                                            is KunneIkkeUnderkjenneRevurdering.UgyldigTilstand -> ugyldigTilstand(
                                                it.fra,
                                                it.til,
                                            )
                                            KunneIkkeUnderkjenneRevurdering.SaksbehandlerOgAttestantKanIkkeVæreSammePerson -> attestantOgSaksbehandlerKanIkkeVæreSammePerson
                                        }
                                        return@authorize call.svar(resultat)
                                    },
                                    ifRight = {
                                        call.sikkerlogg("Underkjente behandling med id: $revurderingId")
                                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                                        return@authorize call.svar(
                                            Resultat.json(
                                                HttpStatusCode.OK,
                                                serialize(it.toJson(satsFactory)),
                                            ),
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            }
        }
    }
}
