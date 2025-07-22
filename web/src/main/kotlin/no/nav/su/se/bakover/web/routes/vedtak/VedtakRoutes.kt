package no.nav.su.se.bakover.web.routes.vedtak

import behandling.søknadsbehandling.presentation.tilResultat
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.infrastructure.web.withVedtakId
import no.nav.su.se.bakover.vedtak.application.NySøknadCommand
import no.nav.su.se.bakover.vedtak.application.VedtakService
import no.nav.su.se.bakover.web.routes.sak.SAK_PATH
import no.nav.su.se.bakover.web.routes.søknadsbehandling.jsonBody
import vedtak.domain.KunneIkkeStarteNySøknadsbehandling
import vilkår.formue.domain.FormuegrenserFactory

internal const val VEDTAK_PATH = "$SAK_PATH/{sakId}/vedtak"

fun Route.vedtakRoutes(
    vedtakService: VedtakService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    data class Body(
        val årsak: String? = null,
        val omgjøringsgrunn: String? = null,
    )
    post("$VEDTAK_PATH/{vedtakId}/nySoknadsbehandling") {
        call.withSakId { sakId ->
            call.withVedtakId { vedtakId ->
                call.withBody<Body> { body ->
                    vedtakService.startNySøknadsbehandlingForAvslag(
                        sakId,
                        vedtakId,
                        call.suUserContext.saksbehandler,
                        NySøknadCommand(body.årsak, body.omgjøringsgrunn),
                    )
                        .fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = { call.svar(HttpStatusCode.Created.jsonBody(it, formuegrenserFactory)) },
                        )
                }
            }
        }
    }
}

internal fun KunneIkkeStarteNySøknadsbehandling.tilResultat(): Resultat = when (this) {
    KunneIkkeStarteNySøknadsbehandling.FantIkkeVedtak -> Feilresponser.fantIkkeVedtak
    KunneIkkeStarteNySøknadsbehandling.FantIkkeSak -> Feilresponser.fantIkkeSak
    is KunneIkkeStarteNySøknadsbehandling.FeilVedOpprettelseAvSøknadsbehandling -> this.feil.tilResultat()
    KunneIkkeStarteNySøknadsbehandling.VedtakErIkkeAvslag -> HttpStatusCode.BadRequest.errorJson(
        "Kan ikke starte ny søknadsbehandling på et vedtak som ikke er avslag",
        "vedtak_er_ikke_avslag",
    )

    is KunneIkkeStarteNySøknadsbehandling.FeilVedHentingAvPersonForOpprettelseAvOppgave -> Feilresponser.kunneIkkeOppretteOppgave
    KunneIkkeStarteNySøknadsbehandling.FeilVedOpprettelseAvOppgave -> Feilresponser.kunneIkkeOppretteOppgave
    KunneIkkeStarteNySøknadsbehandling.ÅpenBehandlingFinnes -> HttpStatusCode.BadRequest.errorJson(
        "Kan ikke starte ny søknadsbehandling når det allerede finnes en åpen søknadsbehandling",
        "åpen_behandling_finnes",
    )

    KunneIkkeStarteNySøknadsbehandling.MåHaGyldingOmgjøringsgrunn -> TODO()
    KunneIkkeStarteNySøknadsbehandling.UgyldigRevurderingsÅrsak -> TODO()
}
