package no.nav.su.se.bakover.database.utbetaling

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class UtbetalingPostgresRepoTest {

    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = UtbetalingPostgresRepo(EmbeddedDatabase.instance())
    private val kvitteringOK = Kvittering(
        Kvittering.Utbetalingsstatus.OK,
        "some xml",
        mottattTidspunkt = Tidspunkt.EPOCH
    )

    @Test
    fun `hent utbetaling`() {
        withMigratedDb {
            val sak: Sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()

            val expected = lagUtbetalingUtenKvittering(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr
            )
            repo.opprettUtbetaling(expected)
            repo.hentUtbetaling(expected.id) shouldBe expected
        }
    }

    @Test
    fun `hent utbetaling fra avstemmingsnøkkel`() {
        withMigratedDb {
            val sak: Sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()

            val utbetalingUtenKvittering = lagUtbetalingUtenKvittering(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr
            ).also {
                repo.opprettUtbetaling(it)
            }
            val utbetalingMedKvittering = utbetalingUtenKvittering.toKvittertUtbetaling(
                Kvittering(
                    Kvittering.Utbetalingsstatus.OK,
                    "some xml",
                    mottattTidspunkt = Tidspunkt.EPOCH
                )
            ).also {
                repo.oppdaterMedKvittering(it)
            }
            val hentet =
                repo.hentUtbetaling(utbetalingMedKvittering.avstemmingsnøkkel) as Utbetaling.OversendtUtbetaling.MedKvittering
            utbetalingMedKvittering shouldBe hentet
        }
    }

    @Test
    fun `hent utbetalinger uten kvittering`() {
        withMigratedDb {
            val sak: Sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()

            val utbetalingUtenKvittering = lagUtbetalingUtenKvittering(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr
            ).also {
                repo.opprettUtbetaling(it)
            }
            lagUtbetalingUtenKvittering(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr
            ).also {
                repo.opprettUtbetaling(it)
                repo.oppdaterMedKvittering(it.toKvittertUtbetaling(kvitteringOK))
            }
            repo.hentUkvitterteUtbetalinger() shouldBe listOf(utbetalingUtenKvittering)
        }
    }

    @Test
    fun `oppdater med kvittering`() {

        withMigratedDb {
            val sak: Sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()

            val utbetalingUtenKvittering = lagUtbetalingUtenKvittering(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr
            ).also {
                repo.opprettUtbetaling(it)
            }
            val utbetalingMedKvittering = utbetalingUtenKvittering.toKvittertUtbetaling(
                kvitteringOK
            ).also {
                repo.oppdaterMedKvittering(it)
            }
            repo.hentUtbetaling(utbetalingMedKvittering.id).shouldBeInstanceOf<Utbetaling.OversendtUtbetaling.MedKvittering>()
        }
    }

    @Test
    fun `opprett og hent utbetalingslinjer`() {
        withMigratedDb {
            val sak: Sak = testDataHelper.nySakMedJournalførtSøknadOgOppgave()

            val utbetaling = lagUtbetalingUtenKvittering(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr
            ).copy(utbetalingslinjer = emptyList())
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
        internal fun lagUtbetalingUtenKvittering(
            sakId: UUID = UUID.randomUUID(),
            saksnummer: Saksnummer,
            fnr: Fnr,
            datoBeregnet: LocalDate = idag()
        ) =
            Utbetaling.OversendtUtbetaling.UtenKvittering(
                id = UUID30.randomUUID(),
                opprettet = Tidspunkt.now(),
                sakId = sakId,
                saksnummer = saksnummer,
                utbetalingslinjer = listOf(
                    Utbetalingslinje(
                        id = UUID30.randomUUID(),
                        opprettet = Tidspunkt.EPOCH,
                        fraOgMed = LocalDate.EPOCH,
                        tilOgMed = LocalDate.EPOCH.plusDays(30),
                        forrigeUtbetalingslinjeId = null,
                        beløp = 3
                    )
                ),
                fnr = fnr,
                avstemmingsnøkkel = Avstemmingsnøkkel(),
                simulering = Simulering(
                    gjelderId = fnr,
                    gjelderNavn = "gjelderNavn",
                    datoBeregnet = datoBeregnet,
                    nettoBeløp = 0,
                    periodeList = listOf()
                ),
                utbetalingsrequest = Utbetalingsrequest(
                    value = "<xml></xml>"
                ),
                type = Utbetaling.UtbetalingsType.NY,
                behandler = NavIdentBruker.Attestant("Z123"),
            )

        private fun defaultUtbetalingslinje(forrigeUtbetalingslinjeId: UUID30? = null) = Utbetalingslinje(
            fraOgMed = 1.januar(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
            beløp = 25000
        )
    }
}
