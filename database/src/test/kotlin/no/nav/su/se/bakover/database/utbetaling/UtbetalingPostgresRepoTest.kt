package no.nav.su.se.bakover.database.utbetaling

import io.kotest.matchers.beInstanceOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test

internal class UtbetalingPostgresRepoTest {

    private val FNR = FnrGenerator.random()
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = UtbetalingPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `hent utbetaling`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val utbetaling = repo.opprettUtbetaling(sak.oppdrag.id, defaultOversendtUtbetaling())
            val hentet = repo.hentUtbetaling(utbetaling.id)

            utbetaling shouldBe hentet
        }
    }

    @Test
    fun `hent utbetaling fra avstemmingsnøkkel`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val oversendtUtbetaling = defaultOversendtUtbetaling()
            val utbetaling = repo.opprettUtbetaling(sak.oppdrag.id, oversendtUtbetaling)
            val kvittert = repo.oppdaterMedKvittering(
                utbetalingId = utbetaling.id,
                kvittering = Kvittering(
                    Kvittering.Utbetalingsstatus.OK,
                    "some xml",
                    mottattTidspunkt = Tidspunkt.EPOCH
                )
            )
            val hentet = repo.hentUtbetaling(oversendtUtbetaling.id) as Utbetaling.KvittertUtbetaling
            kvittert shouldBe hentet
        }
    }

    @Test
    fun `oppdater med kvittering`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val utbetaling = repo.opprettUtbetaling(sak.oppdrag.id, defaultOversendtUtbetaling())

            val kvittering = Kvittering(
                Kvittering.Utbetalingsstatus.OK,
                "some xml",
                mottattTidspunkt = Tidspunkt.EPOCH
            )
            val oppdatert = repo.oppdaterMedKvittering(utbetaling.id, kvittering)

            oppdatert shouldBe beInstanceOf(Utbetaling.KvittertUtbetaling::class)
        }
    }

    @Test
    fun `opprett og hent utbetalingslinjer`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val utbetaling = repo.opprettUtbetaling(sak.oppdrag.id, defaultOversendtUtbetaling())
            val utbetalingslinje1 = repo.opprettUtbetalingslinje(utbetaling.id, defaultUtbetalingslinje())
            val utbetalingslinje2 =
                repo.opprettUtbetalingslinje(utbetaling.id, defaultUtbetalingslinje(utbetalingslinje1.id))
            EmbeddedDatabase.instance().withSession {
                val hentet = UtbetalingInternalRepo.hentUtbetalingslinjer(utbetaling.id, it)
                listOf(utbetalingslinje1, utbetalingslinje2) shouldBe hentet
            }
        }
    }

    private fun defaultOversendtUtbetaling() = Utbetaling.OversendtUtbetaling(
        id = UUID30.randomUUID(),
        utbetalingslinjer = listOf(),
        fnr = FNR,
        simulering = Simulering(
            gjelderId = FNR,
            gjelderNavn = "",
            datoBeregnet = idag(),
            nettoBeløp = 0,
            periodeList = listOf()
        ),
        oppdragsmelding = Oppdragsmelding(
            originalMelding = "",
            avstemmingsnøkkel = Avstemmingsnøkkel()
        ),
        type = Utbetaling.UtbetalingsType.NY
    )

    private fun defaultUtbetalingslinje(forrigeUtbetalingslinjeId: UUID30? = null) = Utbetalingslinje(
        fraOgMed = 1.januar(2020),
        tilOgMed = 31.desember(2020),
        forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
        beløp = 25000
    )
}
