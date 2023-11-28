package no.nav.su.se.bakover.web.routes.sak

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.fnr.KunneIkkeOppdatereFødselsnummer
import no.nav.su.se.bakover.domain.sak.fnr.OppdaterFødselsnummerPåSakCommand
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock

internal fun Route.oppdaterFødselsnummerPåSakRoute(
    sakService: SakService,
    clock: Clock,
    formuegrenserFactory: FormuegrenserFactory,
) {
    put("$SAK_PATH/{sakId}/fødselsnummer") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.svar(
                    sakService.oppdaterFødselsnummer(
                        OppdaterFødselsnummerPåSakCommand(
                            sakId,
                            NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                        ),
                    ).fold(
                        {
                            when (it) {
                                KunneIkkeOppdatereFødselsnummer.SakHarAlleredeSisteFødselsnummer -> Feilresponser.sakHarAlleredeSisteFødselsnummer
                            }
                        },
                        {
                            Resultat.json(
                                HttpStatusCode.OK,
                                serialize(it.toJson(clock, formuegrenserFactory)),
                            )
                        },
                    ),
                )
            }
        }
    }
}
