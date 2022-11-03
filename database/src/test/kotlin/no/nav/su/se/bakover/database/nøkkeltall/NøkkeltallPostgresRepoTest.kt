package no.nav.su.se.bakover.database.nøkkeltall

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.nøkkeltall.Nøkkeltall
import no.nav.su.se.bakover.domain.søknadinnhold.ForNav
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.søknad.søknadinnhold
import org.junit.jupiter.api.Test

internal class NøkkeltallPostgresRepoTest {
    @Test
    fun `null søknader`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val nøkkeltallRepo = testDataHelper.nøkkeltallRepo
            nøkkeltallRepo.hentNøkkeltall() shouldBe Nøkkeltall(
                søknader = Nøkkeltall.Søknader(
                    totaltAntall = 0,
                    iverksatteAvslag = 0,
                    iverksatteInnvilget = 0,
                    ikkePåbegynt = 0,
                    påbegynt = 0,
                    lukket = 0,
                    digitalsøknader = 0,
                    papirsøknader = 0,
                ),
                antallUnikePersoner = 0,
                løpendeSaker = 0,
            )
        }
    }

    @Test
    fun `to søknader knyttet til en sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val nySak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
            testDataHelper.persisterSøknadUtenJournalføringOgOppgavePåEksisterendeSak(nySak.id)

            val nøkkeltallRepo = testDataHelper.nøkkeltallRepo
            nøkkeltallRepo.hentNøkkeltall() shouldBe Nøkkeltall(
                søknader = Nøkkeltall.Søknader(
                    totaltAntall = 2,
                    iverksatteAvslag = 0,
                    iverksatteInnvilget = 0,
                    ikkePåbegynt = 2,
                    påbegynt = 0,
                    lukket = 0,
                    digitalsøknader = 2,
                    papirsøknader = 0,
                ),
                antallUnikePersoner = 1,
                løpendeSaker = 0,
            )
        }
    }

    @Test
    fun `en avslått og en innvilget søknad i to forskjellige saker`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            testDataHelper.persisterSøknadsbehandlingIverksattAvslagUtenBeregning()
            testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()
            val nøkkeltallRepo = testDataHelper.nøkkeltallRepo
            nøkkeltallRepo.hentNøkkeltall() shouldBe Nøkkeltall(
                søknader = Nøkkeltall.Søknader(
                    totaltAntall = 2,
                    iverksatteAvslag = 1,
                    iverksatteInnvilget = 1,
                    ikkePåbegynt = 0,
                    påbegynt = 0,
                    lukket = 0,
                    digitalsøknader = 2,
                    papirsøknader = 0,
                ),
                antallUnikePersoner = 2,
                løpendeSaker = 1,
            )
        }
    }

    @Test
    fun `en bruker med mange lukket søknader`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val nøkkeltallRepo = testDataHelper.nøkkeltallRepo
            val nySak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
            testDataHelper.persisterLukketJournalførtSøknadMedOppgave(nySak.id)
            testDataHelper.persisterLukketJournalførtSøknadMedOppgave(nySak.id)
            testDataHelper.persisterLukketJournalførtSøknadMedOppgave(nySak.id)

            nøkkeltallRepo.hentNøkkeltall() shouldBe Nøkkeltall(
                søknader = Nøkkeltall.Søknader(
                    totaltAntall = 4,
                    iverksatteAvslag = 0,
                    iverksatteInnvilget = 0,
                    ikkePåbegynt = 1,
                    påbegynt = 0,
                    lukket = 3,
                    digitalsøknader = 4,
                    papirsøknader = 0,
                ),
                antallUnikePersoner = 1,
                løpendeSaker = 0,
            )
        }
    }

    @Test
    fun `en bruker som sendt in en papirsøknad`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val nøkkeltallRepo = testDataHelper.nøkkeltallRepo
            testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave(
                søknadInnhold = søknadinnhold(
                    forNav = ForNav.Papirsøknad(
                        mottaksdatoForSøknad = fixedLocalDate,
                        grunnForPapirinnsending = ForNav.Papirsøknad.GrunnForPapirinnsending.MidlertidigUnntakFraOppmøteplikt,
                        annenGrunn = null,
                    ),
                ),
            )

            nøkkeltallRepo.hentNøkkeltall() shouldBe Nøkkeltall(
                søknader = Nøkkeltall.Søknader(
                    totaltAntall = 1,
                    iverksatteAvslag = 0,
                    iverksatteInnvilget = 0,
                    ikkePåbegynt = 1,
                    påbegynt = 0,
                    lukket = 0,
                    digitalsøknader = 0,
                    papirsøknader = 1,
                ),
                antallUnikePersoner = 1,
                løpendeSaker = 0,
            )
        }
    }

    @Test
    fun `en behandling som ble påbegynt og lukket, telles ikke som påbegynt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val nøkkeltallRepo = testDataHelper.nøkkeltallRepo
            testDataHelper.persisterSøknadsbehandlingAvsluttet()

            nøkkeltallRepo.hentNøkkeltall() shouldBe Nøkkeltall(
                søknader = Nøkkeltall.Søknader(
                    totaltAntall = 1,
                    iverksatteAvslag = 0,
                    iverksatteInnvilget = 0,
                    ikkePåbegynt = 0,
                    påbegynt = 0,
                    lukket = 1,
                    digitalsøknader = 1,
                    papirsøknader = 0,
                ),
                antallUnikePersoner = 1,
                løpendeSaker = 0,
            )
        }
    }
}
