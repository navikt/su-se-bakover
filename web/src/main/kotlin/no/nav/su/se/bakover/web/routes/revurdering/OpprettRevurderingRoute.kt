package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.metrics.SuMetrics
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppretteRevurdering.FantIkkeSak
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppretteRevurdering.UgyldigBegrunnelse
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppretteRevurdering.UgyldigÅrsak
import no.nav.su.se.bakover.service.revurdering.OpprettRevurderingRequest
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkeAktørId
import no.nav.su.se.bakover.web.routes.Feilresponser.harAlleredeÅpenBehandling
import no.nav.su.se.bakover.web.routes.Feilresponser.kunneIkkeOppretteOppgave
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.begrunnelseKanIkkeVæreTom
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.formueSomFørerTilOpphørMåRevurderes
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.måVelgeInformasjonSomRevurderes
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.tidslinjeForVedtakErIkkeKontinuerlig
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.ugyldigÅrsak
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.utenlandsoppholdSomFørerTilOpphørMåRevurderes
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.OpprettelseOgOppdateringAvRevurdering.uteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeSak
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIngenVedtakSomKanRevurderes
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.ugyldigPeriode
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withSakId
import java.time.LocalDate

internal fun Route.opprettRevurderingRoute(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
) {
    data class Body(
        val fraOgMed: LocalDate,
        val årsak: String,
        val begrunnelse: String,
        val informasjonSomRevurderes: List<Revurderingsteg>,
    )
    post(revurderingPath) {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBody<Body> { body ->
                    val navIdent = call.suUserContext.navIdent

                    revurderingService.opprettRevurdering(
                        OpprettRevurderingRequest(
                            sakId = sakId,
                            fraOgMed = body.fraOgMed,
                            årsak = body.årsak,
                            begrunnelse = body.begrunnelse,
                            saksbehandler = NavIdentBruker.Saksbehandler(navIdent),
                            informasjonSomRevurderes = body.informasjonSomRevurderes,
                        ),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Opprettet en ny revurdering på sak med id $sakId")
                            call.audit(it.fnr, AuditLogEvent.Action.CREATE, it.id)
                            SuMetrics.behandlingStartet(SuMetrics.Behandlingstype.REVURDERING)
                            call.svar(
                                Resultat.json(
                                    HttpStatusCode.Created,
                                    serialize(it.toJson(satsFactory)),
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun KunneIkkeOppretteRevurdering.tilResultat(): Resultat {
    return when (this) {
        is FantIkkeSak -> fantIkkeSak
        is UgyldigBegrunnelse -> begrunnelseKanIkkeVæreTom
        is UgyldigÅrsak -> ugyldigÅrsak
        KunneIkkeOppretteRevurdering.MåVelgeInformasjonSomSkalRevurderes -> måVelgeInformasjonSomRevurderes
        is KunneIkkeOppretteRevurdering.FeilVedOpprettelseAvRevurdering -> this.feil.tilResultat()
    }
}

internal fun Sak.KunneIkkeOppretteRevurdering.tilResultat() = when (this) {
    Sak.KunneIkkeOppretteRevurdering.FantIkkeAktørId -> fantIkkeAktørId
    Sak.KunneIkkeOppretteRevurdering.FantIngenVedtakSomKanRevurderes -> fantIngenVedtakSomKanRevurderes
    Sak.KunneIkkeOppretteRevurdering.FormueSomFørerTilOpphørMåRevurderes -> formueSomFørerTilOpphørMåRevurderes
    Sak.KunneIkkeOppretteRevurdering.HarÅpenBehandling -> harAlleredeÅpenBehandling
    is Sak.KunneIkkeOppretteRevurdering.KunneIkkeHenteGjeldendeVedtaksdataSak -> this.feil.tilResultat()
    Sak.KunneIkkeOppretteRevurdering.KunneIkkeOppretteOppgave -> kunneIkkeOppretteOppgave
    Sak.KunneIkkeOppretteRevurdering.TidslinjeForVedtakErIkkeKontinuerlig -> tidslinjeForVedtakErIkkeKontinuerlig
    Sak.KunneIkkeOppretteRevurdering.UtenlandsoppholdSomFørerTilOpphørMåRevurderes -> utenlandsoppholdSomFørerTilOpphørMåRevurderes
    is Sak.KunneIkkeOppretteRevurdering.UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode -> uteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(
        this.periode,
    )
}

internal fun Sak.KunneIkkeHenteGjeldendeVedtaksdata.tilResultat() = when (this) {
    is Sak.KunneIkkeHenteGjeldendeVedtaksdata.FinnesIngenVedtakSomKanRevurderes -> fantIngenVedtakSomKanRevurderes
    is Sak.KunneIkkeHenteGjeldendeVedtaksdata.UgyldigPeriode -> ugyldigPeriode(this.feil)
}
