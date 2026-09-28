package no.nav.su.se.bakover.domain.klage

import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.LocalDate
import java.util.UUID

/*
    Betyr at det er klage som er oversendt uten svar fra Klage/KABAL
 */
data class OversendtKlageUtenKlageinstanshendelse(
    val klageId: KlageId,
    val sakId: UUID,
)

interface KlageRepo {
    fun lagre(klage: Klage, transactionContext: TransactionContext = defaultTransactionContext())
    fun hentKlage(klageId: KlageId): Klage?
    fun hentKlager(sakid: UUID, sessionContext: SessionContext = defaultSessionContext()): List<Klage>
    fun hentOversendteKlagerUtenKlageinstanshendelserFør(
        grense: Tidspunkt,
    ): List<OversendtKlageUtenKlageinstanshendelse>
    fun hentVedtaksbrevDatoSomDetKlagesPå(klageId: KlageId): LocalDate?
    fun defaultSessionContext(): SessionContext
    fun defaultTransactionContext(): TransactionContext
    fun knyttMotOmgjøring(klageId: KlageId, behandlingId: UUID, sessionContext: SessionContext = defaultSessionContext())
}
