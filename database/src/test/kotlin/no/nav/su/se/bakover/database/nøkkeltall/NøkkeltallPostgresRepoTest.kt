package no.nav.su.se.bakover.database.nøkkeltall

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.ForNav
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.nøkkeltall.Nøkkeltall
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class NøkkeltallPostgresRepoTest {
    @Test
    fun `null søknader`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val nøkkeltallRepo = testDataHelper.nøkkeltallRepo
            nøkkeltallRepo.hentNøkkeltall() shouldBe Nøkkeltall(
                totalt = 0,
                iverksattAvslag = 0,
                iverksattInnvilget = 0,
                ikkePåbegynt = 0,
                påbegynt = 0,
                digitalsøknader = 0,
                papirsøknader = 0,
                personer = 0
            )
        }
    }

    @Test
    fun `to søknader knyttet til en sak`() {
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

    @Test
    fun `en avslått og en innvilget søknad i to forskjellige saker`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            testDataHelper.nyIverksattAvslagUtenBeregning()
            testDataHelper.nyIverksattInnvilget()
            val nøkkeltallRepo = testDataHelper.nøkkeltallRepo
            nøkkeltallRepo.hentNøkkeltall() shouldBe Nøkkeltall(
                totalt = 2,
                iverksattAvslag = 1,
                iverksattInnvilget = 1,
                ikkePåbegynt = 0,
                påbegynt = 0,
                digitalsøknader = 2,
                papirsøknader = 0,
                personer = 2
            )
        }
    }

    @Test
    fun `en bruker med mange lukket søknader`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val nøkkeltallRepo = testDataHelper.nøkkeltallRepo
            val nySak = testDataHelper.nySakMedNySøknad()
            testDataHelper.nyLukketSøknadForEksisterendeSak(nySak.id)
            testDataHelper.nyLukketSøknadForEksisterendeSak(nySak.id)
            testDataHelper.nyLukketSøknadForEksisterendeSak(nySak.id)

            nøkkeltallRepo.hentNøkkeltall() shouldBe Nøkkeltall(
                totalt = 4,
                iverksattAvslag = 0,
                iverksattInnvilget = 0,
                ikkePåbegynt = 1,
                påbegynt = 0,
                digitalsøknader = 4,
                papirsøknader = 0,
                personer = 1
            )
        }
    }

    @Test
    fun `en bruker som sendt in en papirsøknad`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val nøkkeltallRepo = testDataHelper.nøkkeltallRepo
            testDataHelper.nySakMedNySøknad(
                søknadInnhold = SøknadInnholdTestdataBuilder.build(
                    forNav = ForNav.Papirsøknad(
                        mottaksdatoForSøknad = LocalDate.now(),
                        grunnForPapirinnsending = ForNav.Papirsøknad.GrunnForPapirinnsending.MidlertidigUnntakFraOppmøteplikt,
                        annenGrunn = null
                    )
                )
            )

            nøkkeltallRepo.hentNøkkeltall() shouldBe Nøkkeltall(
                totalt = 1,
                iverksattAvslag = 0,
                iverksattInnvilget = 0,
                ikkePåbegynt = 1,
                påbegynt = 0,
                digitalsøknader = 0,
                papirsøknader = 1,
                personer = 1
            )
        }
    }
}
