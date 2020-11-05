package no.nav.su.se.bakover.database.person

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test

internal class PersonPostgresRepoTest {
    private val FNR = FnrGenerator.random()
    private val repo = PersonPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `hent fnr for sak`() {
        withDbWithData {
            repo.hentFnrForSak(sak.id) shouldBe FNR
        }
    }

    @Test
    fun `hent fnr for søknad`() {
        withDbWithData {
            repo.hentFnrForSøknad(søknad.id) shouldBe FNR
        }
    }

    @Test
    fun `hent fnr for behandling`() {
        withDbWithData {
            repo.hentFnrForBehandling(behandling.id) shouldBe FNR
        }
    }

    @Test
    fun `hent fnr for utbetaling`() {
        withDbWithData {
            repo.hentFnrForUtbetaling(utbetaling.id) shouldBe FNR
        }
    }

    private fun withDbWithData(test: Ctx.() -> Unit) {
        val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = testDataHelper.insertSøknad(sak.id)
            val behandling = testDataHelper.insertBehandling(sak.id, søknad)
            val utbetaling = Utbetaling.OversendtUtbetaling.UtenKvittering(
                id = UUID30.randomUUID(),
                utbetalingslinjer = listOf(),
                fnr = FNR,
                avstemmingsnøkkel = Avstemmingsnøkkel(),
                simulering = Simulering(
                    gjelderId = FNR,
                    gjelderNavn = "",
                    datoBeregnet = idag(),
                    nettoBeløp = 0,
                    periodeList = listOf()
                ),
                utbetalingsrequest = Utbetalingsrequest(
                    value = ""
                ),
                type = Utbetaling.UtbetalingsType.NY,
                oppdragId = sak.oppdrag.id,
                behandler = NavIdentBruker.Attestant("Z123")
            )
            testDataHelper.opprettUtbetaling(utbetaling)

            Ctx(sak, søknad, behandling, utbetaling).test()
        }
    }

    private data class Ctx(
        val sak: NySak,
        val søknad: Søknad,
        val behandling: NySøknadsbehandling,
        val utbetaling: Utbetaling
    )
}
