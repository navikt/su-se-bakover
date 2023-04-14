package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.søknadsbehandling.opprett.NySøknadsbehandling
import java.util.UUID

interface SøknadsbehandlingRepo {
    fun lagreNySøknadsbehandling(søknadsbehandling: NySøknadsbehandling)
    fun lagre(søknadsbehandling: Søknadsbehandling, sessionContext: TransactionContext = defaultTransactionContext())
    fun hent(id: UUID): Søknadsbehandling?
    fun hentForSak(sakId: UUID, sessionContext: SessionContext = defaultSessionContext()): List<Søknadsbehandling>

    /** En søknad kan kun være knyttet til 0 eller 1 søknadsbehandling. */
    fun hentForSøknad(søknadId: UUID): Søknadsbehandling?
    fun defaultTransactionContext(): TransactionContext
    fun defaultSessionContext(): SessionContext

    fun lagreMedSkattegrunnlag(skattegrunnlagForSøknadsbehandling: SøknadsbehandlingMedSkattegrunnlag)
    fun hentSkattegrunnlag(behandlingId: UUID): SøknadsbehandlingMedSkattegrunnlag?
}
