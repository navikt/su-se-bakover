package no.nav.su.se.bakover.database.utbetaling

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.utbetalingslinje
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test

internal class UtbetalingPostgresRepoTest {

    @Test
    fun `opprett og hent utbetaling uten kvittering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.utbetalingRepo

            val utenKvittering = testDataHelper.nyIverksattInnvilget().second
            repo.hentUtbetaling(utenKvittering.id) shouldBe utenKvittering
        }
    }

    @Test
    fun `hent utbetaling fra avstemmingsnøkkel`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.utbetalingRepo
            val utbetalingMedKvittering = testDataHelper.nyOversendtUtbetalingMedKvittering().second
            val hentet =
                repo.hentUtbetaling(utbetalingMedKvittering.avstemmingsnøkkel) as Utbetaling.OversendtUtbetaling.MedKvittering
            utbetalingMedKvittering shouldBe hentet
        }
    }

    @Test
    fun `hent utbetalinger uten kvittering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.utbetalingRepo
            val utbetalingUtenKvittering = testDataHelper.nyIverksattInnvilget().second
            testDataHelper.nyOversendtUtbetalingMedKvittering()
            repo.hentUkvitterteUtbetalinger() shouldBe listOf(utbetalingUtenKvittering)
        }
    }

    @Test
    fun `oppdater med kvittering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.utbetalingRepo
            val utbetalingMedKvittering = testDataHelper.nyOversendtUtbetalingMedKvittering().second
            repo.hentUtbetaling(utbetalingMedKvittering.id)
                .shouldBeInstanceOf<Utbetaling.OversendtUtbetaling.MedKvittering>()
        }
    }

    @Test
    fun `opprett og hent utbetalingslinjer`() {
        val utbetalingslinjeId1 = UUID30.randomUUID()
        val utbetalingslinjer = nonEmptyListOf(
            utbetalingslinje().copy(
                id = utbetalingslinjeId1,
            ),
            utbetalingslinje().copy(
                forrigeUtbetalingslinjeId = utbetalingslinjeId1,
            ),
        )
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val utbetaling = testDataHelper.nyIverksattInnvilget(utbetalingslinjer = utbetalingslinjer)
            dataSource.withSession {
                val hentet = UtbetalingInternalRepo.hentUtbetalingslinjer(utbetaling.second.id, it)
                hentet shouldBe utbetalingslinjer
            }
        }
    }

    @Test
    fun `endring av eksisterende utbetalingslinje oppretter ny linje med status`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.utbetalingRepo
            val originalUtbetalingslinje = Utbetalingslinje.Ny(
                opprettet = fixedTidspunkt,
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.desember(2020),
                forrigeUtbetalingslinjeId = null,
                beløp = 25000,
                uføregrad = Uføregrad.parse(50),
            )

            val (_, utbetaling) = testDataHelper.nyIverksattInnvilget(
                utbetalingslinjer = nonEmptyListOf(originalUtbetalingslinje),
            )

            val endretUtbetalingslinje = Utbetalingslinje.Endring.Opphør(
                utbetalingslinje = originalUtbetalingslinje,
                virkningstidspunkt = 1.februar(2021),
                clock = fixedClock
            )

            val opphør = utbetaling.copy(
                id = UUID30.randomUUID(),
                type = Utbetaling.UtbetalingsType.OPPHØR,
                utbetalingslinjer = nonEmptyListOf(endretUtbetalingslinje),
            )

            repo.opprettUtbetaling(opphør, repo.defaultTransactionContext())

            repo.hentUtbetaling(opphør.id)!!.utbetalingslinjer shouldBe nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    id = originalUtbetalingslinje.id,
                    opprettet = endretUtbetalingslinje.opprettet,
                    fraOgMed = originalUtbetalingslinje.fraOgMed,
                    tilOgMed = originalUtbetalingslinje.tilOgMed,
                    forrigeUtbetalingslinjeId = originalUtbetalingslinje.forrigeUtbetalingslinjeId,
                    beløp = originalUtbetalingslinje.beløp,
                    virkningstidspunkt = endretUtbetalingslinje.virkningstidspunkt,
                    uføregrad = originalUtbetalingslinje.uføregrad
                ),
            )
            repo.hentUtbetaling(utbetaling.id) shouldNotBe repo.hentUtbetaling(opphør.id)
        }
    }
}
