package no.nav.su.se.bakover.utenlandsopphold.infrastruture.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.lesLong
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.utenlandsopphold.application.annuller.AnnullerUtenlandsoppholdService
import no.nav.su.se.bakover.utenlandsopphold.application.korriger.KorrigerUtenlandsoppholdService
import no.nav.su.se.bakover.utenlandsopphold.application.registrer.RegistrerUtenlandsoppholdService
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.annuller.annullerUtenlandsoppholdRoute
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.korriger.korrigerUtenlandsoppholdRoute
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.registrer.registerUtenlandsoppholdRoute

fun Route.utenlandsoppholdRoutes(
    registerService: RegistrerUtenlandsoppholdService,
    korrigerService: KorrigerUtenlandsoppholdService,
    annullerService: AnnullerUtenlandsoppholdService,
) {
    korrigerUtenlandsoppholdRoute(korrigerService)
    registerUtenlandsoppholdRoute(registerService)
    annullerUtenlandsoppholdRoute(annullerService)
}

internal suspend fun ApplicationCall.withVersjon(ifRight: suspend (Long) -> Unit) {
    this.lesLong("versjon").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.errorJson(it, "versjon_mangler_eller_feil_format")) },
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

fun kunneIkkeBekrefteJournalposter(journalposter: List<JournalpostId>) = HttpStatusCode.InternalServerError.errorJson(
    message = "Kunne ikke bekrefte journalposter: ${journalposter.joinToString()}",
    code = "kunne_ikke_bekrefte_journalposter",
)
