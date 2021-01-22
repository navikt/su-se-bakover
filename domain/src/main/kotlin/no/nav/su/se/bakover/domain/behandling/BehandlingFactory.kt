package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.time.Clock
import java.util.UUID

class BehandlingFactory(
    private val behandlingMetrics: BehandlingMetrics,
    private val clock: Clock,
    private val uuidFactory: UUIDFactory = UUIDFactory(),
) {
    fun createBehandling(
        id: UUID = uuidFactory.newUUID(),
        opprettet: Tidspunkt = Tidspunkt.now(clock),
        behandlingsinformasjon: Behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
        søknad: Søknad.Journalført.MedOppgave,
        beregning: Beregning? = null,
        simulering: Simulering? = null,
        status: Behandling.BehandlingsStatus = Behandling.BehandlingsStatus.OPPRETTET,
        saksbehandler: NavIdentBruker.Saksbehandler? = null,
        attestering: Attestering? = null,
        sakId: UUID,
        saksnummer: Saksnummer,
        hendelseslogg: Hendelseslogg = Hendelseslogg(id.toString()), // TODO create when behandling created by service probably also move out from behandling alltogether.
        fnr: Fnr,
        oppgaveId: OppgaveId,
        iverksattJournalpostId: JournalpostId? = null,
        iverksattBrevbestillingId: BrevbestillingId? = null,
    ) = Behandling(
        behandlingMetrics = behandlingMetrics,
        id = id,
        opprettet = opprettet,
        behandlingsinformasjon = behandlingsinformasjon,
        søknad = søknad,
        beregning = beregning,
        simulering = simulering,
        status = status,
        saksbehandler = saksbehandler,
        attestering = attestering,
        sakId = sakId,
        saksnummer = saksnummer,
        hendelseslogg = hendelseslogg, // TODO create when behandling created by service probably also move out from behandling alltogether.
        fnr = fnr,
        oppgaveId = oppgaveId,
        iverksattJournalpostId = iverksattJournalpostId,
        iverksattBrevbestillingId = iverksattBrevbestillingId,
    )
}
