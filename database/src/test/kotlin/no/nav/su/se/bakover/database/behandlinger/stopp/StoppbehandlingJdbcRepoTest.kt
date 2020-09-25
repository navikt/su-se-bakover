package no.nav.su.se.bakover.database.behandlinger.stopp

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.MicroInstant
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.toMicroInstant
import no.nav.su.se.bakover.database.DatabaseRepo
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.behandlinger.stopp.Stoppbehandling
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import org.junit.jupiter.api.Test
import java.util.UUID

internal class StoppbehandlingJdbcRepoTest {

    private val generalRepo = DatabaseRepo(EmbeddedDatabase.instance())
    private val stoppbehandlingRepo = StoppbehandlingJdbcRepo(EmbeddedDatabase.instance(), generalRepo)
    private val FNR = FnrGenerator.random()

    @Test
    fun `opprett og hent Stoppbehandling`() {
        withMigratedDb {
            val behandlingId = UUID.randomUUID()
            val sak = generalRepo.opprettSak(FNR)
            val utbetaling = Utbetaling(
                opprettet = 1.juli(2020).atStartOfDay().toMicroInstant(),
                utbetalingslinjer = emptyList(),
                fnr = FNR
            )
            generalRepo.opprettUtbetaling(
                oppdragId = sak.oppdrag.id,
                utbetaling = utbetaling
            )

            val nyBehandling = Stoppbehandling.Simulert(
                id = behandlingId,
                opprettet = MicroInstant.EPOCH,
                sakId = sak.id,
                utbetaling = utbetaling,
                stoppÅrsak = "stoppÅrsak",
                saksbehandler = Saksbehandler(id = "saksbehandler")
            )
            stoppbehandlingRepo.opprettStoppbehandling(
                nyBehandling
            )
            stoppbehandlingRepo.hentPågåendeStoppbehandling(sak.id) shouldBe nyBehandling
        }
    }
}
