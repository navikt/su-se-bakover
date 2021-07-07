package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.patch
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeUnderkjenneRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeAktørId
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.kunneIkkeOppretteOppgave
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.routes.søknadsbehandling.enumContains
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withRevurderingId

data class UnderkjennBody(
    val grunn: String,
    val kommentar: String
) {
    private fun valid() = enumContains<Attestering.Underkjent.Grunn>(grunn) && kommentar.isNotBlank()

    internal fun toDomain(navIdent: String): Either<Resultat, Attestering.Underkjent> {
        if (valid()) {
            return Attestering.Underkjent(
                attestant = NavIdentBruker.Attestant(navIdent),
                grunn = Attestering.Underkjent.Grunn.valueOf(this.grunn),
                kommentar = this.kommentar,
                tidspunkt = Tidspunkt.now()
            ).right()
        }
        return HttpStatusCode.BadRequest.errorJson(
            message = "Grunn er feil, eller kommentar finnes ikke.",
            code = "ugyldig_body"
        ).left()
    }
}

internal fun Route.underkjennRevurdering(
    revurderingService: RevurderingService
) {
    authorize(Brukerrolle.Attestant) {
        patch("$revurderingPath/{revurderingId}/underkjenn") {
            val navIdent = call.suUserContext.navIdent

            call.withRevurderingId { revurderingId ->
                Either.catch { deserialize<UnderkjennBody>(call) }.fold(
                    ifLeft = {
                        log.info("Ugyldig body: ", it)
                        call.svar(HttpStatusCode.BadRequest.message("Ugyldig body"))
                    },
                    ifRight = { body ->
                        body.toDomain(navIdent).fold(
                            ifLeft = { call.svar(it) },
                            ifRight = { underkjent ->
                                revurderingService.underkjenn(
                                    revurderingId = revurderingId,
                                    attestering = underkjent
                                ).fold(
                                    ifLeft = {
                                        val resultat = when (it) {
                                            KunneIkkeUnderkjenneRevurdering.FantIkkeRevurdering -> fantIkkeRevurdering
                                            KunneIkkeUnderkjenneRevurdering.FantIkkeAktørId -> fantIkkeAktørId
                                            KunneIkkeUnderkjenneRevurdering.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
                                            is KunneIkkeUnderkjenneRevurdering.UgyldigTilstand -> ugyldigTilstand(
                                                it.fra,
                                                it.til
                                            )
                                        }
                                        call.svar(resultat)
                                    },
                                    ifRight = {
                                        call.sikkerlogg("Underkjente behandling med id: $revurderingId")
                                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                                        call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson())))
                                    }
                                )
                            }
                        )
                    }
                )
            }
        }
    }
}
