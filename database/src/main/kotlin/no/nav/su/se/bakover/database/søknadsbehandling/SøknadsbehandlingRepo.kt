package no.nav.su.se.bakover.database.søknadsbehandling

import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.søknadsbehandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.util.UUID

interface SøknadsbehandlingRepo {
    fun lagreNySøknadsbehandling(søknadsbehandling: NySøknadsbehandling)
    fun lagre(søknadsbehandling: Søknadsbehandling)
    fun hent(id: UUID): Søknadsbehandling?
    fun hent(id: UUID, session: Session): Søknadsbehandling?
    fun hentForSak(sakId: UUID, session: Session): List<Søknadsbehandling>
    fun hentEventuellTidligereAttestering(id: UUID): Attestering?
}
