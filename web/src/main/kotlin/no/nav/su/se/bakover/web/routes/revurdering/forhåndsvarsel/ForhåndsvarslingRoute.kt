package no.nav.su.se.bakover.web.routes.revurdering.forhåndsvarsel

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.web.routes.revurdering.REVURDERING_PATH
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.tilResultat
import no.nav.su.se.bakover.web.routes.revurdering.toJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.InputValidator
import org.slf4j.LoggerFactory
import vilkår.formue.domain.FormuegrenserFactory

internal fun Route.forhåndsvarslingRoute(
    revurderingService: RevurderingService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    data class ForhåndsvarsleBody(val fritekst: String?)
    post("$REVURDERING_PATH/{revurderingId}/forhandsvarsel") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBody<ForhåndsvarsleBody> { body ->
                val ugyldigeFelt = InputValidator.validerTekst("fritekst", body.fritekst, 5000)
                if (ugyldigeFelt != null) {
                    log.error("VALIDERING: Feil i fritekst for forhåndsvarsel. Begrunnelse: ${ugyldigeFelt.begrunnelse}")
                    sikkerLogg.error("VALIDERING: Feil i fritekst for forhåndsvarsel. Ugyldig felt: $ugyldigeFelt")
                    call.svar(
                        BadRequest.errorJson(
                            ugyldigeFelt.begrunnelse,
                            UGYLDIG_INPUT_FORHÅNDSVARSEL,
                        ),
                    )
                    return@withBody
                }
                call.withRevurderingId { revurderingId ->
                    revurderingService.lagreOgSendForhåndsvarsel(
                        RevurderingId(revurderingId),
                        NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                        fritekst = body.fritekst,
                    ).map {
                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                        call.sikkerlogg("Forhåndsvarslet bruker med revurderingId $revurderingId")
                        call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(formuegrenserFactory))))
                    }.mapLeft {
                        call.svar(it.tilResultat())
                    }
                }
            }
        }
    }

    data class ForhåndsvarselBrevutkastBody(val fritekst: String)
    post("$REVURDERING_PATH/{revurderingId}/brevutkastForForhandsvarsel") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withBody<ForhåndsvarselBrevutkastBody> { body ->
                    val ugyldigeFelt = InputValidator.validerTekst("fritekst", body.fritekst, 5000)
                    if (ugyldigeFelt != null) {
                        log.error("VALIDERING: Feil i fritekst for brevutkast forhåndsvarsel. Begrunnelse: ${ugyldigeFelt.begrunnelse}")
                        sikkerLogg.error("VALIDERING: Feil i fritekst for brevutkast forhåndsvarsel. Ugyldig felt: $ugyldigeFelt")
                        call.svar(
                            BadRequest.errorJson(
                                ugyldigeFelt.begrunnelse,
                                UGYLDIG_INPUT_FORHÅNDSVARSEL,
                            ),
                        )
                        return@withBody
                    }
                    val revurdering =
                        revurderingService.hentRevurdering(RevurderingId(revurderingId)) ?: return@authorize call.svar(
                            fantIkkeRevurdering,
                        )

                    revurderingService.lagBrevutkastForForhåndsvarsling(RevurderingId(revurderingId), call.suUserContext.saksbehandler, body.fritekst).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Laget brevutkast for forhåndsvarsel for revurdering med id $revurderingId")
                            call.audit(revurdering.fnr, AuditLogEvent.Action.ACCESS, revurderingId)
                            call.respondBytes(it.getContent(), ContentType.Application.Pdf)
                        },
                    )
                }
            }
        }
    }
}

internal const val UGYLDIG_INPUT_FORHÅNDSVARSEL = "ugyldig_input_forhåndsvarsel"
