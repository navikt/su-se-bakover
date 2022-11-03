package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.sak.Saksnummer
import java.util.UUID

interface ReguleringRepo {
    fun hent(id: UUID): Regulering?
    fun hentReguleringerSomIkkeErIverksatt(): List<Regulering.OpprettetRegulering>
    fun hentForSakId(sakId: UUID, sessionContext: SessionContext = defaultSessionContext()): List<Regulering>
    fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer>
    fun lagre(regulering: Regulering, sessionContext: TransactionContext = defaultTransactionContext())
    fun defaultSessionContext(): SessionContext
    fun defaultTransactionContext(): TransactionContext
}
