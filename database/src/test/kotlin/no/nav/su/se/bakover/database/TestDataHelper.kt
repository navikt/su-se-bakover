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
        val sak: Sak = nySakMedJournalførtSøknad(fnr, journalpostId)
        sak.journalførtSøknad().medOppgave(oppgaveId).also {
            søknadRepo.oppdaterOppgaveId(it)
        }
        return sakRepo.hentSak(fnr)
            ?: throw java.lang.IllegalStateException("Fant ikke sak rett etter vi opprettet den.")
    }

    fun nySakMedJournalførtSøknad(
        fnr: Fnr = FnrGenerator.random(),
        journalpostId: JournalpostId = JournalpostId("defaultJournalpostId")
    ): Sak {
        val nySak: NySak = nySakMedNySøknad(fnr)
        nySak.søknad.journalfør(journalpostId).also { journalførtSøknad ->
            søknadRepo.oppdaterjournalpostId(journalførtSøknad)
        }
        return sakRepo.hentSak(fnr)
            ?: throw java.lang.IllegalStateException("Fant ikke sak rett etter vi opprettet den.")
    }

    internal fun nySakMedNySøknad(fnr: Fnr): NySak =
        SakFactory(clock = clock).nySak(fnr, SøknadInnholdTestdataBuilder.build()).also {
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

    companion object {
        /** Kaster hvis size != 1 */
        fun Sak.journalførtSøknadMedOppgave(): Søknad.Journalført.MedOppgave {
            kastDersomSøknadErUlikEn()
            return søknader().first() as Søknad.Journalført.MedOppgave
        }

        /** Kaster hvis size != 1 */
        fun Sak.journalførtSøknad(): Søknad.Journalført.UtenOppgave {
            kastDersomSøknadErUlikEn()
            return søknader().first() as Søknad.Journalført.UtenOppgave
        }

        /** Kaster hvis size != 1 */
        fun Sak.søknadNy(): Søknad.Ny {
            kastDersomSøknadErUlikEn()
            return søknader().first() as Søknad.Ny
        }

        private fun Sak.kastDersomSøknadErUlikEn() {
            if (søknader.size != 1) throw IllegalStateException("Var ferre/fler enn 1 søknad. Testen bør spesifisere dersom fler. Antall: ${søknader.size}")
        }
    }
}
