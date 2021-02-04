package no.nav.su.se.bakover.database

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggPostgresRepo
import no.nav.su.se.bakover.database.sak.SakPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadPostgresRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingPostgresRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.OpprettetRevurdering
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

internal class TestDataHelper(
    dataSource: DataSource = EmbeddedDatabase.instance(),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val utbetalingRepo = UtbetalingPostgresRepo(dataSource)
    private val hendelsesloggRepo = HendelsesloggPostgresRepo(dataSource)
    private val søknadRepo = SøknadPostgresRepo(dataSource)
    private val søknadsbehandlingRepo = SøknadsbehandlingPostgresRepo(dataSource)
    private val revurderingRepo = RevurderingPostgresRepo(dataSource, søknadsbehandlingRepo)

    private val sakRepo = SakPostgresRepo(dataSource, søknadsbehandlingRepo)

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

    fun opprettUtbetaling(utbetaling: Utbetaling.OversendtUtbetaling.UtenKvittering) =
        utbetalingRepo.opprettUtbetaling(utbetaling)

    fun oppdaterHendelseslogg(hendelseslogg: Hendelseslogg) = hendelsesloggRepo.oppdaterHendelseslogg(hendelseslogg)

    fun insertRevurdering(behandlingId: UUID) =
        OpprettetRevurdering(
            id = UUID.randomUUID(),
            periode = mock(),
            tilRevurdering = mock
            { on { id } doReturn behandlingId },
            opprettet = Tidspunkt(instant = Instant.now()),
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "1337")
        ).also {
            revurderingRepo.lagre(it)
        }

    fun uavklartVilkårsvurdering(
        sak: Sak,
        søknad: Søknad.Journalført.MedOppgave,
        behandlingsinformasjon: Behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()
    ): Søknadsbehandling.Vilkårsvurdert.Uavklart {

        return Søknadsbehandling.Vilkårsvurdert.Uavklart(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            søknad = søknad,
            oppgaveId = søknad.oppgaveId,
            behandlingsinformasjon = behandlingsinformasjon,
            fnr = sak.fnr
        ).also {
            søknadsbehandlingRepo.lagre(it)
        }
    }
}
