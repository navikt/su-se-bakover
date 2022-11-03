package no.nav.su.se.bakover.web.routes.revurdering.forhåndsvarsel

import arrow.core.Either
import arrow.core.flatMap
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.revurdering.Forhåndsvarselhandling
import no.nav.su.se.bakover.service.revurdering.FortsettEtterForhåndsvarselFeil
import no.nav.su.se.bakover.service.revurdering.FortsettEtterForhåndsvarslingRequest
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.tilResultat
import no.nav.su.se.bakover.web.routes.revurdering.revurderingPath
import no.nav.su.se.bakover.web.routes.revurdering.tilResultat
import no.nav.su.se.bakover.web.routes.revurdering.toJson

internal fun Route.forhåndsvarslingRoute(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
) {
    data class ForhåndsvarsleBody(val forhåndsvarselhandling: Forhåndsvarselhandling, val fritekst: String)
    post("$revurderingPath/{revurderingId}/forhandsvarsel") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBody<ForhåndsvarsleBody> { body ->
                call.withRevurderingId { revurderingId ->
                    revurderingService.lagreOgSendForhåndsvarsel(
                        revurderingId,
                        NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                        body.forhåndsvarselhandling,
                        fritekst = body.fritekst,
                    ).map {
                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                        call.sikkerlogg("Forhåndsvarslet bruker med revurderingId $revurderingId")
                        call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(satsFactory))))
                    }.mapLeft {
                        call.svar(it.tilResultat())
                    }
                }
            }
        }
    }

    data class ForhåndsvarselBrevutkastBody(val fritekst: String)
    post("$revurderingPath/{revurderingId}/brevutkastForForhandsvarsel") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withBody<ForhåndsvarselBrevutkastBody> { body ->
                    val revurdering =
                        revurderingService.hentRevurdering(revurderingId) ?: return@withRevurderingId call.svar(
                            fantIkkeRevurdering,
                        )

                    revurderingService.lagBrevutkastForForhåndsvarsling(revurderingId, body.fritekst).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Laget brevutkast for forhåndsvarsel for revurdering med id $revurderingId")
                            call.audit(revurdering.fnr, AuditLogEvent.Action.ACCESS, revurderingId)
                            call.respondBytes(it, ContentType.Application.Pdf)
                        },
                    )
                }
            }
        }
    }

    post("$revurderingPath/{revurderingId}/fortsettEtterForhåndsvarsel") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withBody<FortsettEtterForhåndsvarslingBody> { body ->
                    when (body.valg) {
                        FortsettEtterForhåndsvarslingBody.BeslutningEtterForhåndsvarsling.FORTSETT_MED_SAMME_OPPLYSNINGER -> {
                            Either.fromNullable(body.fritekstTilBrev).mapLeft {
                                HttpStatusCode.BadRequest.errorJson(
                                    "Må fylle ut fritekst til vedtaksbrev",
                                    "må_fylle_ut_fritekst_til_vedtaksbrev",
                                )
                            }.map { fritekstTilBrev ->
                                FortsettEtterForhåndsvarslingRequest.FortsettMedSammeOpplysninger(
                                    revurderingId = revurderingId,
                                    begrunnelse = body.begrunnelse,
                                    fritekstTilBrev = fritekstTilBrev,
                                    saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                                )
                            }
                        }

                        FortsettEtterForhåndsvarslingBody.BeslutningEtterForhåndsvarsling.FORTSETT_MED_ANDRE_OPPLYSNINGER -> Either.Right(
                            FortsettEtterForhåndsvarslingRequest.FortsettMedAndreOpplysninger(
                                revurderingId = revurderingId,
                                begrunnelse = body.begrunnelse,
                                saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                            ),
                        )
                    }.flatMap {
                        revurderingService.fortsettEtterForhåndsvarsling(it)
                            .mapLeft { fortsettEtterForhåndsvarselFeil ->
                                when (fortsettEtterForhåndsvarselFeil) {
                                    is FortsettEtterForhåndsvarselFeil.Attestering -> fortsettEtterForhåndsvarselFeil.subError.tilResultat()
                                    FortsettEtterForhåndsvarselFeil.FantIkkeRevurdering -> fantIkkeRevurdering
                                    FortsettEtterForhåndsvarselFeil.MåVæreEnSimulertRevurdering -> HttpStatusCode.BadRequest.errorJson(
                                        "Revurderingen er ikke i riktig tilstand for å beslutte forhåndsvarslingen",
                                        "ikke_riktig_tilstand_for_å_beslutte_forhåndsvarslingen",
                                    )

                                    is FortsettEtterForhåndsvarselFeil.UgyldigTilstandsovergang -> Feilresponser.ugyldigTilstand(
                                        fortsettEtterForhåndsvarselFeil.fra,
                                        fortsettEtterForhåndsvarselFeil.til,
                                    )
                                }
                            }
                    }.fold(
                        { call.svar(it) },
                        { call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(satsFactory)))) },
                    )
                }
            }
        }
    }
}
