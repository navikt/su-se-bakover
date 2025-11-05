package no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.registrer

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.utenlandsopphold.application.registrer.RegistrerUtenlandsoppholdService
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.RegistrerteUtenlandsoppholdJson.Companion.toJson
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.kunneIkkeBekrefteJournalposter
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.overlappendePerioder
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.utdatertSaksversjon
import vilkår.utenlandsopphold.domain.registrer.KunneIkkeRegistereUtenlandsopphold.KunneIkkeValidereJournalposter
import vilkår.utenlandsopphold.domain.registrer.KunneIkkeRegistereUtenlandsopphold.OverlappendePeriode
import vilkår.utenlandsopphold.domain.registrer.KunneIkkeRegistereUtenlandsopphold.UtdatertSaksversjon

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
                            correlationId = getOrCreateCorrelationIdFromThreadLocal(),
                            brukerroller = call.suUserContext.roller,
                        ),
                    ).onRight {
                        call.svar(Resultat.json(HttpStatusCode.Created, serialize(it.toJson())))
                    }.onLeft {
                        call.svar(
                            when (it) {
                                OverlappendePeriode -> overlappendePerioder
                                UtdatertSaksversjon -> utdatertSaksversjon
                                is KunneIkkeValidereJournalposter -> kunneIkkeBekrefteJournalposter(
                                    it.journalposter,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
