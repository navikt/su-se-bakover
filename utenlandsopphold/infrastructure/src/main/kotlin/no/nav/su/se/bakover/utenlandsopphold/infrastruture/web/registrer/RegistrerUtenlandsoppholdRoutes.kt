package no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.registrer

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.getCorrelationId
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.utenlandsopphold.application.registrer.RegistrerUtenlandsoppholdService
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.KunneIkkeRegistereUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.RegistrerteUtenlandsoppholdJson.Companion.toJson
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.overlappendePerioder
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.utdatertSaksversjon
import no.nav.su.se.bakover.web.features.authorize

fun Route.registerUtenlandsoppholdRoute(
    service: RegistrerUtenlandsoppholdService,
) {
    post("/saker/{sakId}/utenlandsopphold") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withBody<RegistrerUtenlandsoppholdJson> { json ->
                    service.registrer(
                        json.toCommand(
                            sakId = sakId,
                            opprettetAv = call.suUserContext.saksbehandler,
                            correlationId = getCorrelationId()!!,
                            brukerroller = call.suUserContext.roller,
                        ),
                    ).tap {
                        call.svar(Resultat.json(HttpStatusCode.Created, serialize(it.toJson())))
                    }.tapLeft {
                        call.svar(
                            when (it) {
                                KunneIkkeRegistereUtenlandsopphold.OverlappendePeriode -> overlappendePerioder
                                KunneIkkeRegistereUtenlandsopphold.UtdatertSaksversjon -> utdatertSaksversjon
                            },
                        )
                    }
                }
            }
        }
    }
}
