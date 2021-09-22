package no.nav.su.se.bakover.database.søknadsbehandling

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.søknadsbehandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.util.UUID

interface SøknadsbehandlingRepo {
    fun lagreNySøknadsbehandling(søknadsbehandling: NySøknadsbehandling)
    fun lagre(søknadsbehandling: Søknadsbehandling)
    fun hent(id: UUID): Søknadsbehandling?
    fun hentEventuellTidligereAttestering(id: UUID): Attestering?
    fun hentForSak(sakId: UUID, sessionContext: SessionContext = defaultSessionContext()): List<Søknadsbehandling>

    fun defaultSessionContext(): SessionContext
}
