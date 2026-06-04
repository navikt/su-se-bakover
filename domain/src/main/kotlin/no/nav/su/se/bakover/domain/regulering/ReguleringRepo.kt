package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.time.Year
import java.util.UUID

interface ReguleringRepo {
    fun hent(id: ReguleringId): Regulering?
    fun hentStatusForÅpneManuelleReguleringer(): List<ReguleringSomKreverManuellBehandling>
    fun hentForSakId(sakId: UUID, sessionContext: SessionContext = defaultSessionContext()): Reguleringer
    fun lagre(regulering: Regulering, sessionContext: TransactionContext = defaultTransactionContext())
    fun markerSomIkkeSendtTilOppdrag(id: ReguleringId, sessionContext: TransactionContext? = null)
    fun markerSomSendtTilOppdrag(id: ReguleringId, sessionContext: TransactionContext? = null)
    fun hentIverksatteReguleringerSomIkkeErSendtTilOppdrag(år: Year): List<IverksattRegulering>
    fun defaultSessionContext(): SessionContext
    fun defaultTransactionContext(): TransactionContext
}
