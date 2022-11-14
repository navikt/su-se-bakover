package no.nav.su.se.bakover.web.routes.søknadsbehandling.iverksett

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.metrics.SuMetrics
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeIverksette
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingService
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.søknadsbehandling.behandlingPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.jsonBody
import no.nav.su.se.bakover.web.routes.tilResultat
import java.time.Clock

internal fun Route.iverksettSøknadsbehandlingRoute(
    service: IverksettSøknadsbehandlingService,
    satsFactory: SatsFactory,
    clock: Clock,
) {
    patch("$behandlingPath/{behandlingId}/iverksett") {
        authorize(Brukerrolle.Attestant) {
            call.withBehandlingId { behandlingId ->

                val navIdent = call.suUserContext.navIdent

                service.iverksett(
                    IverksettRequest(
                        behandlingId = behandlingId,
                        attestering = Attestering.Iverksatt(NavIdentBruker.Attestant(navIdent), Tidspunkt.now(clock)),
                    ),
                ).fold(
                    {
                        call.svar(kunneIkkeIverksetteMelding(it))
                    },
                    {
                        call.sikkerlogg("Iverksatte behandling med id: $behandlingId")
                        call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                        SuMetrics.vedtakIverksatt(SuMetrics.Behandlingstype.SØKNAD)
                        call.svar(HttpStatusCode.OK.jsonBody(it, satsFactory))
                    },
                )
            }
        }
    }
}

private fun kunneIkkeIverksetteMelding(value: KunneIkkeIverksette): Resultat {
    return when (value) {
        is KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> Feilresponser.attestantOgSaksbehandlerKanIkkeVæreSammePerson
        is KunneIkkeIverksette.KunneIkkeUtbetale -> value.utbetalingFeilet.tilResultat()
        is KunneIkkeIverksette.KunneIkkeGenerereVedtaksbrev -> Feilresponser.Brev.kunneIkkeGenerereBrev
        is KunneIkkeIverksette.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
        KunneIkkeIverksette.AvkortingErUfullstendig -> Feilresponser.avkortingErUfullstendig
        KunneIkkeIverksette.HarAlleredeBlittAvkortetAvEnAnnen -> Feilresponser.avkortingErAlleredeAvkortet
        KunneIkkeIverksette.HarBlittAnnullertAvEnAnnen -> Feilresponser.avkortingErAlleredeAnnullert
        KunneIkkeIverksette.KunneIkkeOpprettePlanlagtKontrollsamtale -> HttpStatusCode.InternalServerError.errorJson(
            "Kunne ikke opprette kontrollsamtale",
            "kunne_ikke_opprette_kontrollsamtale",
        )

        KunneIkkeIverksette.LagringFeilet -> Feilresponser.lagringFeilet
        KunneIkkeIverksette.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving -> Feilresponser.sakAvventerKravgrunnlagForTilbakekreving
    }
}
