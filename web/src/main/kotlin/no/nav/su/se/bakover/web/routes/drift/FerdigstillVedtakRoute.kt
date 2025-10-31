package no.nav.su.se.bakover.web.routes.drift

import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withVedtakId
import no.nav.su.se.bakover.vedtak.application.FerdigstillVedtakService
import no.nav.su.se.bakover.web.routes.vedtak.tilResultat

internal fun Route.ferdigstillVedtakRoutes(
    ferdigstillVedtakService: FerdigstillVedtakService,
) {
    post("$DRIFT_PATH/vedtak/{vedtakId}/ferdigstill") {
        authorize(Brukerrolle.Drift) {
            call.withVedtakId { vedtakId ->
                ferdigstillVedtakService.ferdigstillVedtak(vedtakId).fold(
                    ifLeft = {
                        call.svar(it.tilResultat())
                    },
                    ifRight = {
                        call.svar(Resultat.okJson())
                    },
                )
            }
        }
    }
}
