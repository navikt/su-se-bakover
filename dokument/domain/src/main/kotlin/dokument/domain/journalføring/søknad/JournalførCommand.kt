package dokument.domain.journalføring.søknad

import java.util.UUID

/**
 * @property internDokumentId SU-app sin ID. Brukes for dedup og sporing gjennom verdikjeden.
 */
interface JournalførCommand {
    val internDokumentId: UUID
}
