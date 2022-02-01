package no.nav.su.se.bakover.web.routes.revurdering.forhåndsvarsel

import arrow.core.Either
import arrow.core.flatMap
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeAvslutteRevurdering
import no.nav.su.se.bakover.service.revurdering.Forhåndsvarselhandling
import no.nav.su.se.bakover.service.revurdering.FortsettEtterForhåndsvarselFeil
import no.nav.su.se.bakover.service.revurdering.FortsettEtterForhåndsvarslingRequest
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkePersonEllerSaksbehandlerNavn
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.tilResultat
import no.nav.su.se.bakover.web.routes.revurdering.revurderingPath
import no.nav.su.se.bakover.web.routes.revurdering.tilResultat
import no.nav.su.se.bakover.web.routes.revurdering.toJson
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId

internal fun Route.forhåndsvarslingRoute(
    revurderingService: RevurderingService,
) {

    data class ForhåndsvarsleBody(val forhåndsvarselhandling: Forhåndsvarselhandling, val fritekst: String)
    data class ForhåndsvarselBrevutkastBody(val fritekst: String)

    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/{revurderingId}/forhandsvarsel") {
            call.withBody<ForhåndsvarsleBody> { body ->
                call.withRevurderingId { revurderingId ->
                    revurderingService.lagreOgSendForhåndsvarsel(
                        revurderingId,
                        NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                        body.forhåndsvarselhandling,
                        fritekst = body.fritekst,
                    ).map {
                        call.sikkerlogg("Forhåndsvarslet bruker med revurderingId $revurderingId")
                        call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson())))
                    }.mapLeft {
                        call.svar(it.tilResultat())
                    }
                }
            }
        }

        post("$revurderingPath/{revurderingId}/brevutkastForForhandsvarsel") {
            call.withRevurderingId { revurderingId ->
                call.withBody<ForhåndsvarselBrevutkastBody> { body ->
                    val revurdering = revurderingService.hentRevurdering(revurderingId)
                        ?: return@withRevurderingId call.svar(fantIkkeRevurdering)

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

        data class FortsettEtterForhåndsvarslingBody(
            val begrunnelse: String,
            /**
             * @see BeslutningEtterForhåndsvarsling
             */
            val valg: String,
            val fritekstTilBrev: String?,
        )

        post("$revurderingPath/{revurderingId}/fortsettEtterForhåndsvarsel") {
            call.withRevurderingId { revurderingId ->
                call.withBody<FortsettEtterForhåndsvarslingBody> { body ->
                    when (body.valg) {
                        BeslutningEtterForhåndsvarsling.FortsettSammeOpplysninger.beslutning ->
                            Either.Right(
                                FortsettEtterForhåndsvarslingRequest.FortsettMedSammeOpplysninger(
                                    revurderingId = revurderingId,
                                    begrunnelse = body.begrunnelse,
                                    fritekstTilBrev = body.fritekstTilBrev ?: "",
                                    saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                                ),
                            )
                        BeslutningEtterForhåndsvarsling.FortsettMedAndreOpplysninger.beslutning ->
                            Either.Right(
                                FortsettEtterForhåndsvarslingRequest.FortsettMedAndreOpplysninger(
                                    revurderingId = revurderingId,
                                    begrunnelse = body.begrunnelse,
                                    saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                                ),
                            )
                        BeslutningEtterForhåndsvarsling.AvsluttUtenEndringer.beslutning ->
                            Either.Right(
                                FortsettEtterForhåndsvarslingRequest.AvsluttUtenEndringer(
                                    revurderingId = revurderingId,
                                    begrunnelse = body.begrunnelse,
                                    fritekstTilBrev = body.fritekstTilBrev,
                                    saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                                ),
                            )
                        else -> Either.Left(HttpStatusCode.BadRequest.errorJson("Ugyldig valg", "ugyldig_valg"))
                    }
                        .flatMap {
                            revurderingService.fortsettEtterForhåndsvarsling(it)
                                .mapLeft { fortsettEtterForhåndsvarselFeil ->
                                    when (fortsettEtterForhåndsvarselFeil) {
                                        is FortsettEtterForhåndsvarselFeil.Attestering -> fortsettEtterForhåndsvarselFeil.subError.tilResultat()
                                        FortsettEtterForhåndsvarselFeil.FantIkkeRevurdering -> fantIkkeRevurdering
                                        FortsettEtterForhåndsvarselFeil.RevurderingErIkkeForhåndsvarslet -> HttpStatusCode.BadRequest.errorJson(
                                            "Revurderingen er ikke forhåndsvarslet",
                                            "ikke_forhåndsvarslet",
                                        )
                                        FortsettEtterForhåndsvarselFeil.MåVæreEnSimulertRevurdering -> HttpStatusCode.BadRequest.errorJson(
                                            "Revurderingen er ikke i riktig tilstand for å beslutte forhåndsvarslingen",
                                            "ikke_riktig_tilstand_for_å_beslutte_forhåndsvarslingen",
                                        )
                                        is FortsettEtterForhåndsvarselFeil.KunneIkkeAvslutteRevurdering -> when (fortsettEtterForhåndsvarselFeil.subError) {
                                            KunneIkkeAvslutteRevurdering.FantIkkeRevurdering -> fantIkkeRevurdering
                                            is KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse -> (fortsettEtterForhåndsvarselFeil.subError as KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse).feil.tilResultat()
                                            is KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetRevurdering -> (fortsettEtterForhåndsvarselFeil.subError as KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetRevurdering).feil.tilResultat()
                                            is KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetStansAvYtelse -> (fortsettEtterForhåndsvarselFeil.subError as KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetStansAvYtelse).feil.tilResultat()
                                            KunneIkkeAvslutteRevurdering.KunneIkkeLageDokument -> Feilresponser.Brev.kunneIkkeLageBrevutkast
                                            KunneIkkeAvslutteRevurdering.FantIkkePersonEllerSaksbehandlerNavn -> fantIkkePersonEllerSaksbehandlerNavn
                                        }
                                        is FortsettEtterForhåndsvarselFeil.UgyldigTilstandsovergang -> Feilresponser.ugyldigTilstand(
                                            fortsettEtterForhåndsvarselFeil.fra,
                                            fortsettEtterForhåndsvarselFeil.til,
                                        )
                                    }
                                }
                        }
                        .fold(
                            { call.svar(it) },
                            { call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson()))) },
                        )
                }
            }
        }
    }
}
