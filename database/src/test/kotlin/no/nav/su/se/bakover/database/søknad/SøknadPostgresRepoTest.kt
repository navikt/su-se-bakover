package no.nav.su.se.bakover.database.søknad

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.AvsluttetBegrunnelse
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
    fun `slettet behandling for søknad skal ikke bli hentet`() {
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
                repo.slettBehandlingForSøknad(søknadId = søknad.id, avsluttetBegrunnelse = AvsluttetBegrunnelse.Trukket)
                repo.hentSøknad(søknad.id) shouldBe null
            }
        }
    }
}
