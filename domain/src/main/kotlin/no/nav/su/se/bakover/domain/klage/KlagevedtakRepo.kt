package no.nav.su.se.bakover.domain.klage

import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface KlagevedtakRepo {
    fun lagre(klagevedtak: UprosessertFattetKlageinstansvedtak)
    fun lagre(klagevedtak: ProsessertKlageinstansvedtak, transactionContext: TransactionContext = defaultTransactionContext())
    fun hentUbehandlaKlagevedtak(): List<UprosessertFattetKlageinstansvedtak>
    fun markerSomFeil(id: UUID)
    fun defaultTransactionContext(): TransactionContext
}
