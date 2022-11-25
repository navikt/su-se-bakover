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
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.metrics.SuMetrics
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.KunneIkkeIverksetteSøknadsbehandling
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
                    IverksettSøknadsbehandlingCommand(
                        behandlingId = behandlingId,
                        attestering = Attestering.Iverksatt(NavIdentBruker.Attestant(navIdent), Tidspunkt.now(clock)),
                    ),
                ).fold(
                    {
                        call.svar(kunneIkkeIverksetteMelding(it))
                    },
                    {
                        val søknadsbehandling = it.second
                        call.sikkerlogg("Iverksatte behandling med id: $behandlingId")
                        call.audit(søknadsbehandling.fnr, AuditLogEvent.Action.UPDATE, søknadsbehandling.id)
                        SuMetrics.vedtakIverksatt(SuMetrics.Behandlingstype.SØKNAD)
                        call.svar(HttpStatusCode.OK.jsonBody(søknadsbehandling, satsFactory))
                    },
                )
            }
        }
    }
}

private fun kunneIkkeIverksetteMelding(value: KunneIkkeIverksetteSøknadsbehandling): Resultat {
    return when (value) {
        is KunneIkkeIverksetteSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> Feilresponser.attestantOgSaksbehandlerKanIkkeVæreSammePerson
        is KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale -> value.utbetalingFeilet.tilResultat()
        is KunneIkkeIverksetteSøknadsbehandling.KunneIkkeGenerereVedtaksbrev -> Feilresponser.Brev.kunneIkkeGenerereBrev
        KunneIkkeIverksetteSøknadsbehandling.AvkortingErUfullstendig -> Feilresponser.avkortingErUfullstendig
        KunneIkkeIverksetteSøknadsbehandling.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving -> Feilresponser.sakAvventerKravgrunnlagForTilbakekreving
    }
}
