package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import behandling.domain.UnderkjennAttesteringsgrunnBehandling
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.extensions.enumContains
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
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.underkjenn.KunneIkkeUnderkjenneRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.InputValidator.validerTekst
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.UgyldigInput
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.UgyldigInputValideringFeilResponse
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.UgyldigInputValideringsfeil
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.tilUgyldigFeltMelding
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
            val navIdent = call.suUserContext.navIdent

            call.withRevurderingId { revurderingId ->
                Either.catch { deserialize<UnderkjennBody>(call) }.fold(
                    ifLeft = {
                        log.info("Ugyldig body: ", it)
                        return@authorize call.svar(ugyldigBody)
                    },
                    ifRight = { body ->
                        val feil = mutableListOf<UgyldigInput>()
                        feil.validerTekst("grunn", body.grunn, 100)
                        feil.validerTekst("kommentar", body.kommentar, 2000)
                        if (feil.isNotEmpty()) {
                            log.error("VALIDERING: Feil for underkjenn revurdering. Begrunnelse: ${feil.map { it.begrunnelse }}")
                            sikkerLogg.error("VALIDERING: Feil for underkjenn revurdering. feil: $feil")
                            return@authorize call.svar(
                                Resultat.json(
                                    httpCode = BadRequest,
                                    json = serialize(
                                        UgyldigInputValideringFeilResponse(
                                            message = "Ugyldig input underkjenn revurdering",
                                            code = UGYLDIG_INPUT_UNDERKJENN_REVURDERING,
                                            errors = feil.map {
                                                UgyldigInputValideringsfeil(
                                                    felt = it.felt,
                                                    begrunnelse = it.tilUgyldigFeltMelding(),
                                                )
                                            },
                                        ),
                                    ),
                                ),
                            )
                        }
                        body.toDomain(navIdent, clock).fold(
                            ifLeft = { return@authorize call.svar(it) },
                            ifRight = { underkjent ->
                                revurderingService.underkjenn(
                                    revurderingId = RevurderingId(revurderingId),
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
                                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                                        return@authorize call.svar(
                                            Resultat.json(
                                                HttpStatusCode.OK,
                                                serialize(it.toJson(formuegrenserFactory)),
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

internal const val UGYLDIG_INPUT_UNDERKJENN_REVURDERING = "ugyldig_input_underkjenn_revurdering"
