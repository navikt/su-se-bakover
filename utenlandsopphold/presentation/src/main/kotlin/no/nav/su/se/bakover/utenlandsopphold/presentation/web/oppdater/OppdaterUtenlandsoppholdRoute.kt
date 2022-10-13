package no.nav.su.se.bakover.utenlandsopphold.presentation.web.oppdater

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.getCorrelationId
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.utenlandsopphold.application.oppdater.OppdaterUtenlandsoppholdService
import no.nav.su.se.bakover.utenlandsopphold.domain.oppdater.KunneIkkeOppdatereUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.presentation.web.RegistrerteUtenlandsoppholdJson.Companion.toJson
import no.nav.su.se.bakover.utenlandsopphold.presentation.web.overlappendePerioder
import no.nav.su.se.bakover.utenlandsopphold.presentation.web.utdatertSaksversjon
import no.nav.su.se.bakover.utenlandsopphold.presentation.web.withUtenlandsoppholdId
import no.nav.su.se.bakover.web.features.authorize

fun Route.oppdaterUtenlandsoppholdRoute(
    service: OppdaterUtenlandsoppholdService,
) {
    put("/saker/{sakId}/utenlandsopphold/{utenlandsoppholdId}") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withUtenlandsoppholdId { utenlandsoppholdId ->
                    call.withBody<OppdaterUtenlandsoppholdJson> { json ->
                        service.oppdater(
                            json.toCommand(
                                sakId = sakId,
                                opprettetAv = call.suUserContext.saksbehandler,
                                correlationId = getCorrelationId()!!,
                                brukerroller = call.suUserContext.roller,
                                utenlandsoppholdId = utenlandsoppholdId,
                            ),
                        ).tap {
                            call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson())))
                        }.tapLeft {
                            call.svar(
                                when (it) {
                                    KunneIkkeOppdatereUtenlandsopphold.OverlappendePeriode -> overlappendePerioder
                                    KunneIkkeOppdatereUtenlandsopphold.UtdatertSaksversjon -> utdatertSaksversjon
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
