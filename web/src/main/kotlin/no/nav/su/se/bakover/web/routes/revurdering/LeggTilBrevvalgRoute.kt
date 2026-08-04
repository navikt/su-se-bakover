package no.nav.su.se.bakover.web.routes.revurdering

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
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeLeggeTilVedtaksbrevvalg
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.brev.LeggTilBrevvalgRequest
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.InputValidator
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.tilUgyldigFeltMelding
import org.slf4j.LoggerFactory
import vilkår.formue.domain.FormuegrenserFactory

internal fun Route.leggTilBrevvalgRevurderingRoute(
    revurderingService: RevurderingService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    data class Body(
        val valg: LeggTilBrevvalgRequest.Valg,
        val begrunnelse: String?,
    )

    post("$REVURDERING_PATH/{revurderingId}/brevvalg") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withBody<Body> { body ->
                    val ugyldigeFelt = InputValidator.validerTekst("begrunnelse", body.begrunnelse, 2000)
                    if (ugyldigeFelt != null) {
                        log.error("VALIDERING: Feil i begrunnelse for legg til brevvalg. Begrunnelse: ${ugyldigeFelt.begrunnelse}")
                        sikkerLogg.error("VALIDERING: Feil i begrunnelse for legg til brevvalg. Ugyldig felt: $ugyldigeFelt")
                        call.svar(
                            BadRequest.errorJson(
                                ugyldigeFelt.tilUgyldigFeltMelding(),
                                UGYLDIG_INPUT_LEGG_TIL_BREVVALG,
                            ),
                        )
                        return@withBody
                    }
                    call.svar(
                        revurderingService.leggTilBrevvalg(
                            LeggTilBrevvalgRequest(
                                behandlingsId = RevurderingId(revurderingId),
                                valg = body.valg,
                                begrunnelse = body.begrunnelse,
                                saksbehandler = call.suUserContext.saksbehandler,
                            ),
                        ).fold(
                            ifLeft = { it.tilResultat() },
                            ifRight = {
                                call.sikkerlogg("Oppdaterte brevvalg for revurdering:$revurderingId")
                                call.audit(it.fnr, AuditLogEvent.Action.UPDATE, revurderingId)
                                Resultat.json(HttpStatusCode.Created, serialize(it.toJson(formuegrenserFactory)))
                            },
                        ),
                    )
                }
            }
        }
    }
}

internal const val UGYLDIG_INPUT_LEGG_TIL_BREVVALG = "ugyldig_input_legg_til_brevvalg"

internal fun KunneIkkeLeggeTilVedtaksbrevvalg.tilResultat(): Resultat {
    return when (val f = this) {
        is KunneIkkeLeggeTilVedtaksbrevvalg.UgyldigTilstand -> {
            Feilresponser.ugyldigTilstand(f.tilstand, f.tilstand)
        }
    }
}
