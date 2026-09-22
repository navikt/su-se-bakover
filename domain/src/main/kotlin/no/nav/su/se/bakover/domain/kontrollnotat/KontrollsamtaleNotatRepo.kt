package no.nav.su.se.bakover.domain.kontrollnotat

import no.nav.su.se.bakover.common.journal.JournalpostId
import java.util.UUID

interface KontrollsamtaleNotatRepo {
    fun lagre(
        kontrollsamtaleNotat: KontrollsamtaleNotat,
        sakId: UUID,
    )
    fun hentKontrollsamtaleNotat(
        sakId: UUID,
    ): KontrollsamtaleNotat?

    fun oppdaterJournalpostId(
        kontrollsamtaleNotatId: UUID,
        journalpostId: JournalpostId,
    )

    fun hentSakIdForKontrollsamtaleNotat(
        kontrollsamtaleNotatId: UUID,
    ): UUID?

    fun hentUtenJournalpostId(): List<KontrollsamtaleNotat>
}
