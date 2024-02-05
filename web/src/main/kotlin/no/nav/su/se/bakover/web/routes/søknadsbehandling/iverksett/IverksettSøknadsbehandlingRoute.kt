package no.nav.su.se.bakover.web.routes.søknadsbehandling.iverksett

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.KunneIkkeIverksetteSøknadsbehandling
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SØKNADSBEHANDLING_PATH
import no.nav.su.se.bakover.web.routes.søknadsbehandling.jsonBody
import no.nav.su.se.bakover.web.routes.tilResultat
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock

internal fun Route.iverksettSøknadsbehandlingRoute(
    service: IverksettSøknadsbehandlingService,
    formuegrenserFactory: FormuegrenserFactory,
    clock: Clock,
    applicationConfig: ApplicationConfig,
) {
    patch("$SØKNADSBEHANDLING_PATH/{behandlingId}/iverksett") {
        authorize(Brukerrolle.Attestant) {
            call.withBehandlingId { behandlingId ->

                val navIdent = if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Local) {
                    "attestant"
                } else {
                    call.suUserContext.navIdent
                }

                service.iverksett(
                    IverksettSøknadsbehandlingCommand(
                        behandlingId = SøknadsbehandlingId(behandlingId),
                        attestering = Attestering.Iverksatt(NavIdentBruker.Attestant(navIdent), Tidspunkt.now(clock)),
                    ),
                ).fold(
                    {
                        call.svar(it.tilResultat())
                    },
                    {
                        val søknadsbehandling = it.second
                        call.sikkerlogg("Iverksatte behandling med id: $behandlingId")
                        call.audit(søknadsbehandling.fnr, AuditLogEvent.Action.UPDATE, søknadsbehandling.id.value)
                        SuMetrics.vedtakIverksatt(SuMetrics.Behandlingstype.SØKNAD)
                        call.svar(HttpStatusCode.OK.jsonBody(søknadsbehandling, formuegrenserFactory))
                    },
                )
            }
        }
    }
}

internal fun KunneIkkeIverksetteSøknadsbehandling.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeIverksetteSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> Feilresponser.attestantOgSaksbehandlerKanIkkeVæreSammePerson
        is KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale -> this.utbetalingFeilet.tilResultat()
        is KunneIkkeIverksetteSøknadsbehandling.KunneIkkeGenerereVedtaksbrev -> Feilresponser.Brev.kunneIkkeGenerereBrev
        KunneIkkeIverksetteSøknadsbehandling.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving -> Feilresponser.sakAvventerKravgrunnlagForTilbakekreving
        KunneIkkeIverksetteSøknadsbehandling.SimuleringFørerTilFeilutbetaling -> HttpStatusCode.BadRequest.errorJson(
            message = "Simulering fører til feilutbetaling.",
            code = "simulering_fører_til_feilutbetaling",
        )
        is KunneIkkeIverksetteSøknadsbehandling.OverlappendeStønadsperiode -> this.underliggendeFeil.tilResultat()
        is KunneIkkeIverksetteSøknadsbehandling.InneholderUfullstendigeBosituasjoner -> Feilresponser.inneholderUfullstendigeBosituasjoner
        is KunneIkkeIverksetteSøknadsbehandling.KontrollsimuleringFeilet -> this.underliggende.tilResultat()
    }
}
