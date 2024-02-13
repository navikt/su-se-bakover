package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface SøknadsbehandlingRepo {
    fun lagre(søknadsbehandling: Søknadsbehandling, sessionContext: TransactionContext = defaultTransactionContext())
    fun hent(id: SøknadsbehandlingId): Søknadsbehandling?
    fun hentForSak(sakId: UUID, sessionContext: SessionContext = defaultSessionContext()): List<Søknadsbehandling>

    /** En søknad kan kun være knyttet til 0 eller 1 søknadsbehandling. */
    fun hentForSøknad(søknadId: UUID): Søknadsbehandling?
    fun defaultTransactionContext(): TransactionContext
    fun defaultSessionContext(): SessionContext
}
