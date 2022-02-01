package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagManglendeDokumentasjon
import java.util.UUID

interface SøknadsbehandlingRepo {
    fun lagreNySøknadsbehandling(søknadsbehandling: NySøknadsbehandling)
    fun lagre(søknadsbehandling: Søknadsbehandling, sessionContext: TransactionContext = defaultTransactionContext())
    fun hent(id: UUID): Søknadsbehandling?
    fun hentEventuellTidligereAttestering(id: UUID): Attestering?
    fun hentForSak(sakId: UUID, sessionContext: SessionContext = defaultSessionContext()): List<Søknadsbehandling>
    fun lagreAvslagManglendeDokumentasjon(avslag: AvslagManglendeDokumentasjon, sessionContext: TransactionContext = defaultTransactionContext())

    /** En søknad kan kun være knyttet til 0 eller 1 søknadsbehandling. */
    fun hentForSøknad(søknadId: UUID): Søknadsbehandling?
    fun defaultTransactionContext(): TransactionContext
    fun defaultSessionContext(): SessionContext
}
