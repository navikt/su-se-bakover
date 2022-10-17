package no.nav.su.se.bakover.utenlandsopphold.infrastruture.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.lesUUID
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.utenlandsopphold.application.oppdater.OppdaterUtenlandsoppholdService
import no.nav.su.se.bakover.utenlandsopphold.application.registrer.RegistrerUtenlandsoppholdService
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.annuller.annulerUtenlandsoppholdRoute
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.oppdater.oppdaterUtenlandsoppholdRoute
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.registrer.registerUtenlandsoppholdRoute
import java.util.UUID

fun Route.utenlandsoppholdRoutes(
    registerService: RegistrerUtenlandsoppholdService,
    oppdaterService: OppdaterUtenlandsoppholdService,
) {
    oppdaterUtenlandsoppholdRoute(oppdaterService)
    registerUtenlandsoppholdRoute(registerService)
    annulerUtenlandsoppholdRoute()
}

internal suspend fun ApplicationCall.withUtenlandsoppholdId(ifRight: suspend (UUID) -> Unit) {
    this.lesUUID("utenlandsoppholdId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.errorJson(it, "utenlandsoppholdId_mangler_eller_feil_format")) },
        ifRight = { ifRight(it) },
    )
}

val overlappendePerioder = HttpStatusCode.BadRequest.errorJson(
    message = "Ønsket periode overlapper med tidligere perioder",
    code = "overlappende_perioder",
)

val utdatertSaksversjon = HttpStatusCode.BadRequest.errorJson(
    message = "Utdatert sak. Vennligst hent sak på nytt / refresh siden.",
    code = "utdatert_saksversjon",
)
