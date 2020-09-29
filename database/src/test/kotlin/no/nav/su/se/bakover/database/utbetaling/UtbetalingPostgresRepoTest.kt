package no.nav.su.se.bakover.database.utbetaling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import org.junit.jupiter.api.Test

internal class UtbetalingPostgresRepoTest {

    private val FNR = FnrGenerator.random()
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = UtbetalingPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `hent utbetaling`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val utbetaling = testDataHelper.insertUtbetaling(sak.oppdrag.id, defaultUtbetaling())
            val hentet = repo.hentUtbetaling(utbetaling.id)

            utbetaling shouldBe hentet
        }
    }

    @Test
    fun `oppdater med kvittering`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val utbetaling = testDataHelper.insertUtbetaling(sak.oppdrag.id, defaultUtbetaling())
            val kvittering = Kvittering(
                Kvittering.Utbetalingsstatus.OK,
                "some xml",
                mottattTidspunkt = Tidspunkt.EPOCH
            )
            val oppdatert = repo.oppdaterMedKvittering(utbetaling.id, kvittering)

            oppdatert shouldBe utbetaling.copy(
                kvittering = kvittering
            )
        }
    }

    private fun defaultUtbetaling() = Utbetaling(
        id = UUID30.randomUUID(),
        utbetalingslinjer = listOf(),
        fnr = FNR
    )
}
