package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.service.revurdering.OppdaterRevurderingRequest
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.begrunnelseKanIkkeVæreTom
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.epsFormueMedFlereBosituasjonsperioderMåRevurderes
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.formueSomFørerTilOpphørMåRevurderes
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.måVelgeInformasjonSomRevurderes
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.tidslinjeForVedtakErIkkeKontinuerlig
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.ugyldigÅrsak
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.utenlandsoppholdSomFørerTilOpphørMåRevurderes
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.uteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeSak
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIngenVedtakSomKanRevurderes
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import java.time.LocalDate

internal fun Route.oppdaterRevurderingRoute(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
) {
    data class Body(
        val fraOgMed: LocalDate,
        val tilOgMed: LocalDate,
        val årsak: String,
        val begrunnelse: String,
        val informasjonSomRevurderes: List<Revurderingsteg>,
    )

    put("$revurderingPath/{revurderingId}") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withBody<Body> { body ->
                    val navIdent = call.suUserContext.navIdent

                    revurderingService.oppdaterRevurdering(
                        OppdaterRevurderingRequest(
                            revurderingId = revurderingId,
                            periode = Periode.create(
                                fraOgMed = body.fraOgMed,
                                tilOgMed = body.tilOgMed
                            ),
                            årsak = body.årsak,
                            begrunnelse = body.begrunnelse,
                            saksbehandler = NavIdentBruker.Saksbehandler(navIdent),
                            informasjonSomRevurderes = body.informasjonSomRevurderes,
                        ),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Oppdaterte perioden på revurdering med id: $revurderingId")
                            call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                            call.svar(
                                Resultat.json(
                                    HttpStatusCode.OK,
                                    serialize(it.toJson(satsFactory))
                                )
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun KunneIkkeOppdatereRevurdering.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeOppdatereRevurdering.FantIkkeRevurdering -> fantIkkeRevurdering
        is KunneIkkeOppdatereRevurdering.UgyldigTilstand -> Feilresponser.ugyldigTilstand(this.fra, this.til)
        KunneIkkeOppdatereRevurdering.UgyldigBegrunnelse -> begrunnelseKanIkkeVæreTom
        KunneIkkeOppdatereRevurdering.UgyldigÅrsak -> ugyldigÅrsak
        KunneIkkeOppdatereRevurdering.KanIkkeOppdatereRevurderingSomErForhåndsvarslet -> HttpStatusCode.BadRequest.errorJson(
            "Kan ikke oppdatere revurdering som er forhåndsvarslet",
            "kan_ikke_oppdatere_revurdering_som_er_forhåndsvarslet",
        )
        KunneIkkeOppdatereRevurdering.MåVelgeInformasjonSomSkalRevurderes -> måVelgeInformasjonSomRevurderes
        KunneIkkeOppdatereRevurdering.FantIkkeSak -> fantIkkeSak
        KunneIkkeOppdatereRevurdering.FantIngenVedtakSomKanRevurderes -> fantIngenVedtakSomKanRevurderes
        KunneIkkeOppdatereRevurdering.TidslinjeForVedtakErIkkeKontinuerlig -> tidslinjeForVedtakErIkkeKontinuerlig
        KunneIkkeOppdatereRevurdering.FormueSomFørerTilOpphørMåRevurderes -> formueSomFørerTilOpphørMåRevurderes
        KunneIkkeOppdatereRevurdering.EpsFormueMedFlereBosituasjonsperioderMåRevurderes -> epsFormueMedFlereBosituasjonsperioderMåRevurderes
        is KunneIkkeOppdatereRevurdering.UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode -> uteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(
            periode,
        )
        KunneIkkeOppdatereRevurdering.UtenlandsoppholdSomFørerTilOpphørMåRevurderes -> utenlandsoppholdSomFørerTilOpphørMåRevurderes
    }
}
