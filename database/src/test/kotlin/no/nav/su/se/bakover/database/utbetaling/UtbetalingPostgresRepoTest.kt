package no.nav.su.se.bakover.database.utbetaling

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.utbetalingslinje
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import org.junit.jupiter.api.Test

internal class UtbetalingPostgresRepoTest {

    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = UtbetalingPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `opprett og hent utbetaling uten kvittering`() {
        withMigratedDb {
            val utenKvittering = testDataHelper.nyIverksattInnvilget().second
            repo.hentUtbetaling(utenKvittering.id) shouldBe utenKvittering
        }
    }

    @Test
    fun `hent utbetaling fra avstemmingsnøkkel`() {
        withMigratedDb {
            val utbetalingMedKvittering = testDataHelper.nyOversendtUtbetalingMedKvittering().second
            val hentet =
                repo.hentUtbetaling(utbetalingMedKvittering.avstemmingsnøkkel) as Utbetaling.OversendtUtbetaling.MedKvittering
            utbetalingMedKvittering shouldBe hentet
        }
    }

    @Test
    fun `hent utbetalinger uten kvittering`() {
        withMigratedDb {
            val utbetalingUtenKvittering = testDataHelper.nyIverksattInnvilget().second
            testDataHelper.nyOversendtUtbetalingMedKvittering()
            repo.hentUkvitterteUtbetalinger() shouldBe listOf(utbetalingUtenKvittering)
        }
    }

    @Test
    fun `oppdater med kvittering`() {
        withMigratedDb {
            val utbetalingMedKvittering = testDataHelper.nyOversendtUtbetalingMedKvittering().second
            repo.hentUtbetaling(utbetalingMedKvittering.id).shouldBeInstanceOf<Utbetaling.OversendtUtbetaling.MedKvittering>()
        }
    }

    @Test
    fun `opprett og hent utbetalingslinjer`() {
        val utbetalingslinjeId1 = UUID30.randomUUID()
        val utbetalingslinjer = listOf(
            utbetalingslinje().copy(
                id = utbetalingslinjeId1
            ),
            utbetalingslinje().copy(
                forrigeUtbetalingslinjeId = utbetalingslinjeId1
            ),
        )
        withMigratedDb {
            val utbetaling = testDataHelper.nyIverksattInnvilget(utbetalingslinjer = utbetalingslinjer)
            EmbeddedDatabase.instance().withSession {
                val hentet = UtbetalingInternalRepo.hentUtbetalingslinjer(utbetaling.second.id, it)
                hentet shouldBe utbetalingslinjer
            }
        }
    }
}
