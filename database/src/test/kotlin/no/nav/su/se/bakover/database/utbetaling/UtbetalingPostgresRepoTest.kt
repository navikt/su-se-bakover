package no.nav.su.se.bakover.database.utbetaling

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
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
            val utbetaling = defaultOversendtUtbetaling(sak.oppdrag.id, FNR)
            repo.opprettUtbetaling(utbetaling)
            val hentet = repo.hentUtbetaling(utbetaling.id)

            utbetaling shouldBe hentet
        }
    }

    @Test
    fun `hent utbetaling fra avstemmingsnøkkel`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val oversendtUtbetaling = defaultOversendtUtbetaling(sak.oppdrag.id, FNR)
            repo.opprettUtbetaling(oversendtUtbetaling)
            val kvittert = repo.oppdaterMedKvittering(
                utbetalingId = oversendtUtbetaling.id,
                kvittering = Kvittering(
                    Kvittering.Utbetalingsstatus.OK,
                    "some xml",
                    mottattTidspunkt = Tidspunkt.EPOCH
                )
            )
            val hentet = repo.hentUtbetaling(oversendtUtbetaling.avstemmingsnøkkel) as Utbetaling.OversendtUtbetaling.MedKvittering
            kvittert shouldBe hentet
        }
    }

    @Test
    fun `oppdater med kvittering`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val utbetaling = defaultOversendtUtbetaling(sak.oppdrag.id, FNR)
            repo.opprettUtbetaling(utbetaling)

            val kvittering = Kvittering(
                Kvittering.Utbetalingsstatus.OK,
                "some xml",
                mottattTidspunkt = Tidspunkt.EPOCH
            )
            val oppdatert = repo.oppdaterMedKvittering(utbetaling.id, kvittering)

            oppdatert.shouldBeInstanceOf<Utbetaling.OversendtUtbetaling.MedKvittering>()
        }
    }

    @Test
    fun `opprett og hent utbetalingslinjer`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val utbetaling = defaultOversendtUtbetaling(sak.oppdrag.id, FNR)
            repo.opprettUtbetaling(utbetaling)
            val utbetalingslinje1 = repo.opprettUtbetalingslinje(utbetaling.id, defaultUtbetalingslinje())
            val utbetalingslinje2 =
                repo.opprettUtbetalingslinje(utbetaling.id, defaultUtbetalingslinje(utbetalingslinje1.id))
            EmbeddedDatabase.instance().withSession {
                val hentet = UtbetalingInternalRepo.hentUtbetalingslinjer(utbetaling.id, it)
                listOf(utbetalingslinje1, utbetalingslinje2) shouldBe hentet
            }
        }
    }

    companion object {
        internal fun defaultOversendtUtbetaling(oppdragId: UUID30, fnr: Fnr) =
            Utbetaling.OversendtUtbetaling.UtenKvittering(
                id = UUID30.randomUUID(),
                utbetalingslinjer = listOf(),
                fnr = fnr,
                avstemmingsnøkkel = Avstemmingsnøkkel(),
                simulering = Simulering(
                    gjelderId = fnr,
                    gjelderNavn = "",
                    datoBeregnet = idag(),
                    nettoBeløp = 0,
                    periodeList = listOf()
                ),
                utbetalingsrequest = Utbetalingsrequest(
                    value = ""
                ),
                type = Utbetaling.UtbetalingsType.NY,
                oppdragId = oppdragId,
                behandler = NavIdentBruker.Attestant("Z123")
            )

        private fun defaultUtbetalingslinje(forrigeUtbetalingslinjeId: UUID30? = null) = Utbetalingslinje(
            fraOgMed = 1.januar(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
            beløp = 25000
        )
    }
}
