package no.nav.su.se.bakover.web.routes.journalpost

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.journalpost.Journalpost
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeHenteJournalposter

data class JournalpostJson(val journalpostId: String, val tittel: String) {
    companion object {
        fun List<Journalpost>.toJson(): String =
            serialize(this.map { JournalpostJson(it.id.toString(), it.tittel) })
    }
}

internal fun KunneIkkeHenteJournalposter.tilResultat(): Resultat = when (this) {
    KunneIkkeHenteJournalposter.ClientError -> HttpStatusCode.InternalServerError.errorJson(
        "Feil ved henting av journalposter",
        "feil_ved_henting_av_journalposter",
    )
}
