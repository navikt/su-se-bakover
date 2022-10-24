package no.nav.su.se.bakover.utenlandsopphold.domain.registrer

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.application.journal.JournalpostId

sealed interface KunneIkkeRegistereUtenlandsopphold {
    object OverlappendePeriode : KunneIkkeRegistereUtenlandsopphold
    object UtdatertSaksversjon : KunneIkkeRegistereUtenlandsopphold

    /** Kan være en kombinasjon av at vi ikke klarte å sjekke om journalposten eksisterer, at saksbehandler ikke hadde tilgang eller journalposten ikke eksisterer. */
    data class KunneIkkeValidereJournalposter(
        val journalposter: NonEmptyList<JournalpostId>,
    ) : KunneIkkeRegistereUtenlandsopphold
}
