package no.nav.su.se.bakover.domain.klage

import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface KlagevedtakRepo {
    fun lagre(klagevedtak: UprosessertFattetKlagevedtak)
    fun lagre(klagevedtak: ProsessertKlagevedtak, transactionContext: TransactionContext = defaultTransactionContext())
    fun hentUbehandlaKlagevedtak(): List<UprosessertFattetKlagevedtak>
    fun markerSomFeil(id: UUID)
    fun defaultTransactionContext(): TransactionContext
}
