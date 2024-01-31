package vilkår.skatt.domain.journalpost

/**
 * TODO - per dags dato er denne kun brukt i forbindelse med skatt
 *  Der kan være en mulighet at vi vil at denne skal være mer generell
 */
sealed interface KunneIkkeLageJournalpostUtenforSak {
    data object FagsystemIdErTom : KunneIkkeLageJournalpostUtenforSak
}
