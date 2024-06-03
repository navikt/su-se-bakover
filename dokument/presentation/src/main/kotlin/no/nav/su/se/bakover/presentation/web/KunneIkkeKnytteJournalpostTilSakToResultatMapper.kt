package no.nav.su.se.bakover.presentation.web

import dokument.domain.journalføring.KunneIkkeSjekkeTilknytningTilSak
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson

fun KunneIkkeSjekkeTilknytningTilSak.toErrorJson(): Resultat {
    return when (this) {
        KunneIkkeSjekkeTilknytningTilSak.FantIkkeJournalpost -> HttpStatusCode.BadRequest.errorJson(
            "Fant ikke journalpost",
            "fant_ikke_journalpost",
        )

        KunneIkkeSjekkeTilknytningTilSak.IkkeTilgang -> HttpStatusCode.Unauthorized.errorJson(
            "Ikke tilgang til Journalpost",
            "ikke_tilgang_til_journalpost",
        )

        KunneIkkeSjekkeTilknytningTilSak.TekniskFeil -> HttpStatusCode.InternalServerError.errorJson(
            "Teknisk feil ved henting av journalpost",
            "teknisk_feil_ved_henting_av_journalpost",
        )

        KunneIkkeSjekkeTilknytningTilSak.Ukjent -> HttpStatusCode.InternalServerError.errorJson(
            "Ukjent feil ved henting av journalpost",
            "ukjent_feil_ved_henting_av_journalpost",
        )

        KunneIkkeSjekkeTilknytningTilSak.UgyldigInput -> HttpStatusCode.BadRequest.errorJson(
            "Ugyldig journalpostId",
            "ugyldig_journalpostId",
        )

        KunneIkkeSjekkeTilknytningTilSak.JournalpostIkkeKnyttetTilSak -> HttpStatusCode.BadRequest.errorJson(
            "Journalposten er ikke knyttet til saken",
            "journalpost_ikke_knyttet_til_sak",
        )
    }
}
