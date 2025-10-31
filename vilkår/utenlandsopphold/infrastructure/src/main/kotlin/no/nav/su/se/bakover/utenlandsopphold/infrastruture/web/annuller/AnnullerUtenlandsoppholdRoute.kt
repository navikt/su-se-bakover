package no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.annuller

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.utenlandsopphold.application.annuller.AnnullerUtenlandsoppholdService
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.RegistrerteUtenlandsoppholdJson.Companion.toJson
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.utdatertSaksversjon
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.withVersjon
import vilkår.utenlandsopphold.domain.annuller.KunneIkkeAnnullereUtenlandsopphold

fun Route.annullerUtenlandsoppholdRoute(
    service: AnnullerUtenlandsoppholdService,
) {
    // TODO jah: Her skulle vi egentlig brukt DELETE, men virker som ktor fjerner body da.
    patch("/saker/{sakId}/utenlandsopphold/{versjon}") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withVersjon { versjon ->
                    call.withBody<AnnullerUtenlandsoppholdJson> {
                        service.annuller(
                            it.toCommand(
                                sakId = sakId,
                                opprettetAv = call.suUserContext.saksbehandler,
                                correlationId = getOrCreateCorrelationIdFromThreadLocal(),
                                brukerroller = call.suUserContext.roller,
                                annullererVersjon = versjon,
                            ),
                        ).onRight {
                            call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson())))
                        }.onLeft {
                            call.svar(
                                when (it) {
                                    KunneIkkeAnnullereUtenlandsopphold.UtdatertSaksversjon -> utdatertSaksversjon
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
