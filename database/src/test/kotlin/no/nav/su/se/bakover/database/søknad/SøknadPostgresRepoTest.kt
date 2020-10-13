package no.nav.su.se.bakover.database.søknad

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.Trukket
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadPostgresRepoTest {

    private val FNR = FnrGenerator.random()
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = SøknadPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `opprett og hent søknad`() {
        withMigratedDb {
            EmbeddedDatabase.instance().withSession {
                val sak = testDataHelper.insertSak(FNR)
                val søknad = repo.opprettSøknad(
                    sakId = sak.id,
                    søknad = Søknad(
                        sakId = sak.id,
                        id = UUID.randomUUID(),
                        søknadInnhold = SøknadInnholdTestdataBuilder.build()
                    )
                )
                val hentet = repo.hentSøknad(søknad.id)

                søknad shouldBe hentet
            }
        }
    }

    @Test
    fun `søknader som ikke er trukket skal ikke være trukket`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = repo.opprettSøknad(
                sakId = sak.id,
                søknad = Søknad(
                    sakId = sak.id,
                    id = UUID.randomUUID(),
                    søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                )
            )

            val hentetSøknad = repo.hentSøknad(søknad.id)

            hentetSøknad!!.id shouldBe søknad.id
            hentetSøknad.trukket shouldBe null
        }
    }

    @Test
    fun `trukket søknad skal bli hentet med saksbehandler som har trekt søknaden`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = repo.opprettSøknad(
                sakId = sak.id,
                søknad = Søknad(
                    sakId = sak.id,
                    id = UUID.randomUUID(),
                    søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                )
            )

            val saksbehandler = Saksbehandler("Z993156")
            repo.trekkSøknad(
                søknadId = søknad.id,
                trukket = Trukket(
                    tidspunkt = Tidspunkt.now(),
                    saksbehandler = saksbehandler
                )
            )
            val hentetSøknad = repo.hentSøknad(søknad.id)

            hentetSøknad!!.id shouldBe søknad.id
            hentetSøknad.trukket shouldBe Trukket(hentetSøknad.trukket!!.tidspunkt, saksbehandler)
        }
    }
}
