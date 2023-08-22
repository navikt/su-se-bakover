package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface ReguleringRepo {
    fun hent(id: UUID): Regulering?
    fun hentReguleringerSomIkkeErIverksatt(): List<ReguleringSomKreverManuellBehandling>
    fun hentForSakId(sakId: UUID, sessionContext: SessionContext = defaultSessionContext()): List<Regulering>
    fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer>
    fun lagre(regulering: Regulering, sessionContext: TransactionContext = defaultTransactionContext())
    fun defaultSessionContext(): SessionContext
    fun defaultTransactionContext(): TransactionContext
}
