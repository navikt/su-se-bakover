package no.nav.su.se.bakover.database.søknad

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.AvsluttSøknadsBehandlingBody
import no.nav.su.se.bakover.domain.AvsluttSøkndsBehandlingBegrunnelse
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
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
    fun `avsluttet søknad skal bli hentet med begrunnelse for avsluttelse`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = repo.opprettSøknad(
                sakId = sak.id,
                søknad = Søknad(
                    id = UUID.randomUUID(),
                    søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                )
            )

            val avsluttSøknadsBehandlingBody = AvsluttSøknadsBehandlingBody(
                sakId = sak.id,
                søknadId = søknad.id,
                avsluttSøkndsBehandlingBegrunnelse = AvsluttSøkndsBehandlingBegrunnelse.Trukket
            )

            repo.avsluttSøknadsBehandling(avsluttSøknadsBehandlingBody)
            val hentetSøknad = repo.hentSøknad(søknad.id)

            hentetSøknad!!.id shouldBe søknad.id
            hentetSøknad.avsluttetBegrunnelse shouldBe AvsluttSøkndsBehandlingBegrunnelse.Trukket
        }
    }

    @Test
    fun `søknader med en førstegangsbehandling skal ikke bli avsluttet`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = repo.opprettSøknad(
                sakId = sak.id,
                søknad = Søknad(
                    id = UUID.randomUUID(),
                    søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                )
            )
            val behandling = testDataHelper.insertBehandling(sak.id, søknad)

            val avsluttSøknadsBehandlingBody = AvsluttSøknadsBehandlingBody(
                sakId = sak.id,
                søknadId = søknad.id,
                avsluttSøkndsBehandlingBegrunnelse = AvsluttSøkndsBehandlingBegrunnelse.Trukket
            )

            repo.avsluttSøknadsBehandling(avsluttSøknadsBehandlingBody)
            val hentetSøknad = repo.hentSøknad(søknad.id)

            hentetSøknad!!.id shouldBe søknad.id
            hentetSøknad.avsluttetBegrunnelse shouldBe null
            behandling shouldNotBe null
            behandling.id shouldNotBe null
        }
    }
}
