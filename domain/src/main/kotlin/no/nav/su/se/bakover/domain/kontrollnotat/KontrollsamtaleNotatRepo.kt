package no.nav.su.se.bakover.domain.kontrollnotat

import no.nav.su.se.bakover.common.journal.JournalpostId
import java.util.UUID

interface KontrollsamtaleNotatRepo {
    fun lagre(
        kontrollsamtaleNotat: KontrollsamtaleNotat,
        sakId: UUID,
    ): Boolean

    fun hentForId(kontrollsamtaleNotatId: UUID): KontrollsamtaleNotat?

    fun hentKontrollsamtaleNotat(
        sakId: UUID,
    ): KontrollsamtaleNotat?

    fun oppdaterJournalpostId(
        kontrollsamtaleNotatId: UUID,
        journalpostId: JournalpostId,
    ): Boolean

    fun hentSakIdForKontrollsamtaleNotat(
        kontrollsamtaleNotatId: UUID,
    ): UUID?

    fun hentUtenJournalpostId(): List<KontrollsamtaleNotat>
}
