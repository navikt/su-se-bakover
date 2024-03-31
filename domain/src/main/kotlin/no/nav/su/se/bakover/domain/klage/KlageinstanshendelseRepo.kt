package no.nav.su.se.bakover.domain.klage

import behandling.klage.domain.UprosessertKlageinstanshendelse
import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface KlageinstanshendelseRepo {
    fun lagre(hendelse: UprosessertKlageinstanshendelse)
    fun lagre(hendelse: ProsessertKlageinstanshendelse, transactionContext: TransactionContext = defaultTransactionContext())
    fun hentUbehandlaKlageinstanshendelser(): List<UprosessertKlageinstanshendelse>
    fun markerSomFeil(id: UUID)
    fun defaultTransactionContext(): TransactionContext
}
