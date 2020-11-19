package no.nav.su.se.bakover.database.behandling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID

interface BehandlingRepo {
    fun hentBehandling(behandlingId: UUID): Behandling?
    fun oppdaterBehandlingsinformasjon(behandlingId: UUID, behandlingsinformasjon: Behandlingsinformasjon)
    fun oppdaterBehandlingStatus(behandlingId: UUID, status: Behandling.BehandlingsStatus)
    fun leggTilUtbetaling(behandlingId: UUID, utbetalingId: UUID30)
    fun leggTilSimulering(behandlingId: UUID, simulering: Simulering)
    fun leggTilBeregning(behandlingId: UUID, beregning: Beregning)
    fun slettBeregning(behandlingId: UUID)
    fun settSaksbehandler(behandlingId: UUID, saksbehandler: NavIdentBruker.Saksbehandler)
    fun oppdaterAttestant(behandlingId: UUID, attestant: NavIdentBruker.Attestant)
    fun opprettSøknadsbehandling(nySøknadsbehandling: NySøknadsbehandling)
    fun oppdaterOppgaveId(behandlingId: UUID, oppgaveId: OppgaveId)
    fun oppdaterIverksattJournalpostId(behandlingId: UUID, journalpostId: JournalpostId)
    fun oppdaterIverksattBrevbestillingId(behandlingId: UUID, bestillingId: BrevbestillingId)
}
