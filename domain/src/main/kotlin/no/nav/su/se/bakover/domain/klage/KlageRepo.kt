package no.nav.su.se.bakover.domain.klage

import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.time.LocalDate
import java.util.UUID

interface KlageRepo {
    fun lagre(klage: Klage, transactionContext: TransactionContext = defaultTransactionContext())
    fun hentKlage(klageId: KlageId): Klage?
    fun hentKlager(sakid: UUID, sessionContext: SessionContext = defaultSessionContext()): List<Klage>
    fun hentVedtaksbrevDatoSomDetKlagesPå(klageId: KlageId): LocalDate?
    fun defaultSessionContext(): SessionContext
    fun defaultTransactionContext(): TransactionContext
}
