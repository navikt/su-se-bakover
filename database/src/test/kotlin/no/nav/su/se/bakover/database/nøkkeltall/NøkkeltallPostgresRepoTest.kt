package no.nav.su.se.bakover.database.nøkkeltall

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.nøkkeltall.Nøkkeltall
import org.junit.jupiter.api.Test

internal class NøkkeltallPostgresRepoTest {
    @Test
    fun `henter og summerer nøkkeltall riktig`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val nySak = testDataHelper.nySakMedNySøknad()
            testDataHelper.nySøknadForEksisterendeSak(nySak.id)

            val nøkkeltallRepo = testDataHelper.nøkkeltallRepo
            nøkkeltallRepo.hentNøkkeltall() shouldBe Nøkkeltall(
                totalt = 2,
                iverksattAvslag = 0,
                iverksattInnvilget = 0,
                ikkePåbegynt = 2,
                påbegynt = 0,
                digitalsøknader = 2,
                papirsøknader = 0,
                personer = 1
            )
        }
    }
}
