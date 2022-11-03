package no.nav.su.se.bakover.utenlandsopphold.domain.korriger

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.application.journal.JournalpostId

sealed interface KunneIkkeKorrigereUtenlandsopphold {
    object OverlappendePeriode : KunneIkkeKorrigereUtenlandsopphold
    object UtdatertSaksversjon : KunneIkkeKorrigereUtenlandsopphold

    /** Kan være en kombinasjon av at vi ikke klarte å sjekke om journalposten eksisterer, at saksbehandler ikke hadde tilgang eller journalposten ikke eksisterer. */
    data class KunneIkkeBekrefteJournalposter(
        val journalposter: NonEmptyList<JournalpostId>,
    ) : KunneIkkeKorrigereUtenlandsopphold
}
