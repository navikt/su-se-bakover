package no.nav.su.se.bakover.database.utbetaling

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.fixedClock
import no.nav.su.se.bakover.database.utbetalingslinje
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import org.junit.jupiter.api.Test

internal class UtbetalingPostgresRepoTest {

    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = testDataHelper.utbetalingRepo

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
        val utbetalingslinjer = nonEmptyListOf(
            utbetalingslinje().copy(
                id = utbetalingslinjeId1,
            ),
            utbetalingslinje().copy(
                forrigeUtbetalingslinjeId = utbetalingslinjeId1,
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

    @Test
    fun `endring av eksisterende utbetalingslinje oppretter ny linje med status`() {
        withMigratedDb {
            val originalUtbetalingslinje = Utbetalingslinje.Ny(
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.desember(2020),
                forrigeUtbetalingslinjeId = null,
                beløp = 25000,
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

            repo.opprettUtbetaling(opphør)

            repo.hentUtbetaling(opphør.id)!!.utbetalingslinjer shouldBe nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    id = originalUtbetalingslinje.id,
                    opprettet = endretUtbetalingslinje.opprettet,
                    fraOgMed = originalUtbetalingslinje.fraOgMed,
                    tilOgMed = originalUtbetalingslinje.tilOgMed,
                    forrigeUtbetalingslinjeId = originalUtbetalingslinje.forrigeUtbetalingslinjeId,
                    beløp = originalUtbetalingslinje.beløp,
                    virkningstidspunkt = endretUtbetalingslinje.virkningstidspunkt,
                ),
            )

            repo.hentUtbetaling(utbetaling.id) shouldNotBe repo.hentUtbetaling(opphør.id)
        }
    }
}
