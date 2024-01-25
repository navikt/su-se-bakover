package no.nav.su.se.bakover.web.routes.vedtak

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withVedtakId
import no.nav.su.se.bakover.vedtak.domain.KunneIkkeStarteNyBehandling
import no.nav.su.se.bakover.vedtak.application.VedtakService
import no.nav.su.se.bakover.web.routes.sak.SAK_PATH
import no.nav.su.se.bakover.web.routes.søknadsbehandling.jsonBody
import vilkår.formue.domain.FormuegrenserFactory

internal const val VEDTAK_PATH = "$SAK_PATH/{sakId}/vedtak}"

internal fun Route.vedtakRoutes(
    vedtakService: VedtakService,
    formuegrenserFactory: FormuegrenserFactory,
) {

    post("$VEDTAK_PATH/{vedtakId}/nyBehandling") {
        call.withVedtakId {
            vedtakService.startNyBehandlingFor(it).fold(
                ifLeft = { call.svar(it.tilResultat()) },
                ifRight = { call.svar(HttpStatusCode.Created.jsonBody(it, formuegrenserFactory)) },
            )
        }
    }
}


internal fun KunneIkkeStarteNyBehandling.tilResultat(): Resultat = when (this) {
    KunneIkkeStarteNyBehandling.FantIkkeVedtak -> Feilresponser.fantIkkeVedtak
}
