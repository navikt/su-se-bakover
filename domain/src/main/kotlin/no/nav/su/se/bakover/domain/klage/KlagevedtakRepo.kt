package no.nav.su.se.bakover.domain.klage

import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID

interface KlagevedtakRepo {
    fun lagre(klagevedtak: UprosessertFattetKlagevedtak)
    fun hentUbehandlaKlagevedtak(): List<UprosessertFattetKlagevedtak>
    fun lagreOppgaveIdOgMarkerSomProssesert(id: UUID, oppgaveId: OppgaveId)
    fun markerSomProssesert(id: UUID)
    fun markerSomFeil(id: UUID)
}
