package no.nav.su.se.bakover.database.søknad

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class SøknadPostgresRepoTest {

    private val FNR = FnrGenerator.random()
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = SøknadPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `opprett og hent søknad`() {
        withMigratedDb {
            EmbeddedDatabase.instance().withSession {
                val sak: Sak = testDataHelper.insertSak(FNR).toSak()
                val søknad: Søknad = Søknad(
                    sakId = sak.id,
                    id = UUID.randomUUID(),
                    søknadInnhold = SøknadInnholdTestdataBuilder.build()
                ).also { repo.opprettSøknad(it) }
                val hentet = repo.hentSøknad(søknad.id)

                søknad shouldBe hentet
            }
        }
    }

    @Test
    fun `søknader som ikke er trukket skal ikke være trukket`() {
        withMigratedDb {
            val sak: Sak = testDataHelper.insertSak(FNR).toSak()
            val søknad: Søknad = Søknad(
                sakId = sak.id,
                id = UUID.randomUUID(),
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            ).also { repo.opprettSøknad(it) }
            val hentetSøknad: Søknad = repo.hentSøknad(søknad.id)!!
            hentetSøknad.id shouldBe søknad.id
            hentetSøknad.lukket shouldBe null
        }
    }

    @Test
    fun `trukket søknad skal bli hentet med saksbehandler som har trekt søknaden`() {
        withMigratedDb {
            val sak: Sak = testDataHelper.insertSak(FNR).toSak()
            val søknad: Søknad = Søknad(
                sakId = sak.id,
                id = UUID.randomUUID(),
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            ).also { repo.opprettSøknad(it) }
            val saksbehandler = Saksbehandler("Z993156")
            repo.lukkSøknad(
                søknadId = søknad.id,
                lukket = Søknad.Lukket.Trukket(
                    tidspunkt = Tidspunkt.now(),
                    saksbehandler = saksbehandler,
                    datoSøkerTrakkSøknad = LocalDate.now()
                )
            )
            val hentetSøknad = repo.hentSøknad(søknad.id)
            hentetSøknad!!.id shouldBe søknad.id
            hentetSøknad.lukket shouldBe Søknad.Lukket.Trukket(
                tidspunkt = hentetSøknad.lukket!!.tidspunkt,
                saksbehandler = saksbehandler,
                datoSøkerTrakkSøknad = LocalDate.now()
            )
        }
    }
}
