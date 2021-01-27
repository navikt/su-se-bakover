package no.nav.su.se.bakover.database

import com.nhaarman.mockitokotlin2.mock
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.behandling.BehandlingPostgresRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggPostgresRepo
import no.nav.su.se.bakover.database.sak.SakPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.time.Clock
import java.util.UUID
import javax.sql.DataSource

internal class TestDataHelper(
    dataSource: DataSource = EmbeddedDatabase.instance(),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val behandlingMetrics = mock<BehandlingMetrics>()
    private val behandlingFactory = BehandlingFactory(behandlingMetrics, clock)
    private val behandlingPostgresRepo = BehandlingPostgresRepo(dataSource, behandlingFactory)
    private val utbetalingRepo = UtbetalingPostgresRepo(dataSource)
    private val hendelsesloggRepo = HendelsesloggPostgresRepo(dataSource)
    private val søknadRepo = SøknadPostgresRepo(dataSource)
    private val saksbehandlingRepo = SaksbehandlingsPostgresRepo(dataSource)

    private val behandlingRepo = behandlingPostgresRepo
    private val sakRepo = SakPostgresRepo(dataSource, saksbehandlingRepo)

    fun nySakMedJournalførtSøknadOgOppgave(
        fnr: Fnr = FnrGenerator.random(),
        oppgaveId: OppgaveId = OppgaveId("defaultOppgaveId"),
        journalpostId: JournalpostId = JournalpostId("defaultJournalpostId")
    ): Sak {
        val nySak = insertSak(fnr)
        val journalførtSøknad = nySak.søknad.journalfør(journalpostId).also {
            søknadRepo.oppdaterjournalpostId(nySak.søknad.id, journalpostId)
        }
        journalførtSøknad.medOppgave(oppgaveId).also {
            søknadRepo.oppdaterOppgaveId(nySak.søknad.id, oppgaveId)
        }

        return sakRepo.hentSak(fnr) ?: throw RuntimeException("Feil ved henting av sak.")
    }

    internal fun insertSak(fnr: Fnr): NySak = SakFactory(clock = clock).nySak(fnr, SøknadInnholdTestdataBuilder.build()).also {
        sakRepo.opprettSak(it)
    }

    fun insertSøknad(sakId: UUID): Søknad.Ny = Søknad.Ny(
        sakId = sakId,
        id = UUID.randomUUID(),
        søknadInnhold = SøknadInnholdTestdataBuilder.build(),
        opprettet = Tidspunkt.EPOCH,
    ).also { søknadRepo.opprettSøknad(it) }

    fun insertBehandling(sakId: UUID, søknad: Søknad, oppgaveId: OppgaveId = OppgaveId("1234")): NySøknadsbehandling =
        NySøknadsbehandling(
            sakId = sakId,
            søknadId = søknad.id,
            oppgaveId = oppgaveId
        ).also {
            behandlingRepo.opprettSøknadsbehandling(it)
        }

    fun opprettUtbetaling(utbetaling: Utbetaling.OversendtUtbetaling.UtenKvittering) =
        utbetalingRepo.opprettUtbetaling(utbetaling)

    fun oppdaterHendelseslogg(hendelseslogg: Hendelseslogg) = hendelsesloggRepo.oppdaterHendelseslogg(hendelseslogg)

    fun insertBehandlingsinformasjonMedEps(behandlingId: UUID, eps: Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle?): Behandlingsinformasjon =
        Behandlingsinformasjon(
            uførhet = null,
            flyktning = null,
            lovligOpphold = null,
            fastOppholdINorge = null,
            oppholdIUtlandet = null,
            formue = null,
            personligOppmøte = null,
            bosituasjon = null,
            ektefelle = eps
        ).also {
            behandlingRepo.oppdaterBehandlingsinformasjon(behandlingId, it)
        }
}
