package vilkår.utenlandsopphold.domain.korriger

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.journal.JournalpostId

sealed interface KunneIkkeKorrigereUtenlandsopphold {
    data object OverlappendePeriode : KunneIkkeKorrigereUtenlandsopphold
    data object UtdatertSaksversjon : KunneIkkeKorrigereUtenlandsopphold

    /** Kan være en kombinasjon av at vi ikke klarte å sjekke om journalposten eksisterer, at saksbehandler ikke hadde tilgang eller journalposten ikke eksisterer. */
    data class KunneIkkeBekrefteJournalposter(
        val journalposter: NonEmptyList<JournalpostId>,
    ) : KunneIkkeKorrigereUtenlandsopphold
}
