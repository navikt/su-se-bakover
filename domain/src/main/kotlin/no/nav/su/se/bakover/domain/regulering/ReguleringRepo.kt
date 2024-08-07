package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement
import java.util.UUID

interface ReguleringRepo {
    fun hent(id: ReguleringId): Regulering?
    fun hentStatusForÅpneManuelleReguleringer(): List<ReguleringSomKreverManuellBehandling>
    fun hentForSakId(sakId: UUID, sessionContext: SessionContext = defaultSessionContext()): Reguleringer
    fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer>
    fun lagre(regulering: Regulering, sessionContext: TransactionContext = defaultTransactionContext())
    fun lagre(supplement: Reguleringssupplement)
    fun defaultSessionContext(): SessionContext
    fun defaultTransactionContext(): TransactionContext
}
