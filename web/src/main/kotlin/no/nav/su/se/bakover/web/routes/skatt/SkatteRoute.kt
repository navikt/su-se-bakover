package no.nav.su.se.bakover.web.routes.skatt

import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withFnr
import no.nav.su.se.bakover.domain.skatt.KunneIkkeHenteSkattemelding
import no.nav.su.se.bakover.service.skatt.SkatteService
import no.nav.su.se.bakover.web.routes.skatt.SkattegrunnlagJSON.Companion.toStringifiedJson

internal const val skattPath = "/skatt"

internal fun Route.skattRoutes(skatteService: SkatteService) {
    get("$skattPath/person/{fnr}") {
        call.withFnr { fnr ->
            skatteService.hentSamletSkattegrunnlag(fnr, call.suUserContext.saksbehandler).let {
                call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
                call.svar(Resultat.json(OK, it.toStringifiedJson()))
            }
        }
    }
}

internal fun KunneIkkeHenteSkattemelding.tilErrorJson(): ErrorJson = when (this) {
    is KunneIkkeHenteSkattemelding.FinnesIkke -> ErrorJson(
        "Ingen summert skattegrunnlag funnet på oppgitt fødselsnummer og inntektsår",
        "ingen_skattegrunnlag_for_gitt_fnr_og_år",
    )

    KunneIkkeHenteSkattemelding.ManglerRettigheter -> ErrorJson(
        "Autentiserings- eller autoriseringsfeil mot Sigrun/Skatteetaten. Mangler bruker noen rettigheter?",
        "mangler_rettigheter_mot_skatt",
    )

    KunneIkkeHenteSkattemelding.Nettverksfeil -> ErrorJson(
        "Får ikke kontakt med Sigrun/Skatteetaten. Prøv igjen senere.",
        "nettverksfeil_skatt",
    )

    KunneIkkeHenteSkattemelding.PersonFeil -> ErrorJson(
        "Personfeil ved oppslag",
        "feil_ved_oppslag_person",
    )

    KunneIkkeHenteSkattemelding.UkjentFeil -> ErrorJson(
        "Uforventet feil oppstod ved kall til Sigrun/Skatteetaten.",
        "uforventet_feil_mot_skatt",
    )
}
